package com.tanggo.fund.jnautilustrader.core.actor.exp;

/**
 * RequestMessage 接口的默认实现类
 */
public class DefaultRequestMessage implements RequestMessage {

    private String requestId;
    private Object payload;

    public DefaultRequestMessage() {
    }

    public DefaultRequestMessage(Object payload) {
        this.payload = payload;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "DefaultRequestMessage{" +
                "requestId='" + requestId + '\'' +
                ", payload=" + payload +
                '}';
    }
}