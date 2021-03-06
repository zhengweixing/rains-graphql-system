package com.rains.graphql.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rains.graphql.common.domain.QueryRequest;
import com.rains.graphql.system.util.generator.GenConstants;
import com.rains.graphql.system.util.generator.GenUtils;
import com.rains.graphql.system.util.generator.VelocityInitializer;
import com.rains.graphql.system.util.generator.VelocityUtils;
import com.rains.graphql.common.rsql.RsqlToMybatisPlusWrapper;
import com.rains.graphql.common.utils.StringUtils;
import com.rains.graphql.common.utils.SysUtil;
import com.rains.graphql.system.domain.GeneratorConfig;
import com.rains.graphql.system.service.GeneratorConfigService;
import com.rains.graphql.system.service.impl.BaseService;
import com.rains.graphql.tool.entity.GenTable;
import com.rains.graphql.tool.entity.GenTableColumn;
import com.rains.graphql.tool.mapper.GenTableColumnMapper;
import com.rains.graphql.tool.mapper.GenTableMapper;
import com.rains.graphql.tool.service.IGenTableService;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.util.*;

/**
 * 代码生成业务表 Service实现
 *
 * @author hugoDD
 * @date 2020-01-19 14:14:07
 */
@Service
@Transactional
public class GenTableServiceImpl extends BaseService<GenTableMapper, GenTable> implements IGenTableService {

    @Autowired
    private GeneratorConfigService generatorConfigService;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Override
    public List<GenTable> findGenTables(GenTable genTable) {
        LambdaQueryWrapper<GenTable> queryWrapper = new LambdaQueryWrapper<>();
        // TODO 设置查询条件
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<GenTable> selectDbTableList(QueryRequest request) {
        Wrapper<GenTable> queryWrapper = RsqlToMybatisPlusWrapper.getInstance().rsqlToWrapper(request.getFilter(), currentModelClass());

        ((QueryWrapper<GenTable>) queryWrapper).lambda().notLike(GenTable::getTableName, "qrtz_%").notLike(GenTable::getTableName, "gen_%").notIn(GenTable::getTableName, "select table_name from gen_table").apply("table_schema = (select database())  ");
        return baseMapper.selectDbTableList(queryWrapper);
    }

    public boolean importGenTable(List<GenTable> tableList) {
        String operName = SysUtil.getCurrentUserName();
        GeneratorConfig genConfig = generatorConfigService.findGeneratorConfig();

        for (GenTable table : tableList) {
            try {
                String tableName = table.getTableName();
                GenUtils.initTable(table, operName, genConfig);
                int row = baseMapper.insert(table);
                if (row > 0) {
                    // 保存列信息
                    List<GenTableColumn> genTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
                    for (GenTableColumn column : genTableColumns) {
                        GenUtils.initColumnField(column, table);
                        genTableColumnMapper.insert(column);
                    }
                }
            } catch (Exception e) {
                log.error("表名 " + table.getTableName() + " 导入失败：", e);
                return false;
            }
        }
        return true;
    }

    public List<Map<String, Object>> preViewUI(Long tableId, Map<String, Map<String, Object>> uiMap) {
        List<Map<String, Object>> drawinglist = new ArrayList<>();
        // 查询列信息
        LambdaQueryWrapper<GenTableColumn> columnQuery = Wrappers.lambdaQuery();
        List<GenTableColumn> columns = genTableColumnMapper.selectList(columnQuery.eq(GenTableColumn::getTableId, tableId));
        columns.stream().filter(GenTableColumn::isUIEdit).forEach(column -> {
            String tag = column.getHtmlType();
            Map<String, Object> ui = uiMap.get(tag);
            Map<String, Object> destUi = new HashMap<>();
            ui.forEach((k, v) -> destUi.put(k, v));

            destUi.put("vModel", column.getJavaField());
            destUi.put("label", column.getColumnComment());
            destUi.put("span", 12);
            destUi.put("formId", column.getColumnId());
            destUi.put("required", column.getIsRequired());
            destUi.put("placeholder", "请输入" + column.getColumnComment());
            destUi.put("renderKey", System.nanoTime());
            drawinglist.add(destUi);
        });

        return drawinglist;
    }


    /**
     * 预览代码
     *
     * @param tableId 表编号
     * @return 预览数据列表
     */
    public Map<String, String> previewCode(Long tableId) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        // 查询表信息
        GenTable table = baseMapper.selectById(tableId);
        // 查询列信息
        LambdaQueryWrapper<GenTableColumn> columnQuery = Wrappers.lambdaQuery();
        List<GenTableColumn> columns = genTableColumnMapper.selectList(columnQuery.eq(GenTableColumn::getTableId, tableId));
        table.setColumns(columns);
        //List<GenTableColumn> columns = table.getColumns();
        setPkColumn(table, columns);
        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        for (String template : templates) {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, GenConstants.UTF8);
            tpl.merge(context, sw);
            dataMap.put(template, sw.toString());
        }
        return dataMap;
    }

    /**
     * 设置主键列信息
     *
     * @param table   业务表信息
     * @param columns 业务字段列表
     */
    public void setPkColumn(GenTable table, List<GenTableColumn> columns) {
        for (GenTableColumn column : columns) {
            if (column.getIsPk()) {
                table.setPkColumn(column);
                break;
            }
        }
        if (StringUtils.isNull(table.getPkColumn())) {
            table.setPkColumn(columns.get(0));
        }

    }
}
