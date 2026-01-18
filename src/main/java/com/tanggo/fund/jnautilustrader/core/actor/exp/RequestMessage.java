package com.tanggo.fund.jnautilustrader.core.actor.exp;

/**
 * 支持请求-响应模式的消息接口
 */
public interface RequestMessage {

    /**
     * 获取请求ID
     */
    String getRequestId();

    /**
     * 设置请求ID
     */
    void setRequestId(String requestId);
}
