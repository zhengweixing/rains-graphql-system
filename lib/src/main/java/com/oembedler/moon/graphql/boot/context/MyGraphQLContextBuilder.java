package com.oembedler.moon.graphql.boot.context;

import com.corundumstudio.socketio.SocketIOClient;
import graphql.servlet.context.DefaultGraphQLContextBuilder;
import graphql.servlet.context.GraphQLContext;


public class MyGraphQLContextBuilder  extends DefaultGraphQLContextBuilder {

    public GraphQLContext build(SocketIOClient session) {
        return DefaultGraphQLSocketIoContext.createSocketIoContext().with(session).build();
    }
}