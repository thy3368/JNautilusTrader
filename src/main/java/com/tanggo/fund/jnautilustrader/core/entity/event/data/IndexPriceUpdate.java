package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import lombok.Data;

import java.time.Instant;

/**
 * 指数价格更新数据
 */
@Data
public class IndexPriceUpdate {
    /**
     * 品种代码
     */
    private String symbol;

    /**
     * 指数价格
     */
    private double indexPrice;

    /**
     * 事件时间戳
     */
    private long eventTime;

    // 转换为Instant类型
    public Instant getEventTimeInstant() {
        return Instant.ofEpochMilli(eventTime);
    }
}
