package com.rains.graphql.common.graphql.internal;


public abstract class MySubscriptionProtocolFactory {

    private final String protocol;

    public MySubscriptionProtocolFactory(String protocol) {
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    public abstract MySubscriptionProtocolHandler createHandler();
}