package com.tanggo.fund.jnautilustrader.core.entity;

/**
 * 事件仓储接口
 */
public interface EventRepo<T> {
    /**
     * 接收事件
     *
     * @return 任意类型的事件
     */
    Event<T> receive();
}
