package com.tanggo.fund.jnautilustrader.core.entity;

/**
 * 事件处理器接口
 * @param <T> 事件数据类型（如 TradeTick, OrderBookDelta 等）
 */
public interface EventHandler<T> {
    void handle(Event<T> event);
}
