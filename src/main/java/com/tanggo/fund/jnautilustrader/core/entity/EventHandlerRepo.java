package com.tanggo.fund.jnautilustrader.core.entity;

/**
 * 事件处理器仓储接口
 */
public interface EventHandlerRepo<T> {
    /**
     * 根据事件类型查询处理器
     * @param type 事件类型
     * @return 对应的事件处理器
     */
    EventHandler<T> queryBy(String type);
}
