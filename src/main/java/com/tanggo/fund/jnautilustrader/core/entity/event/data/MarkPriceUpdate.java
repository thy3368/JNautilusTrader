package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import lombok.Data;

import java.time.Instant;

/**
 * 标记价格更新数据
 */
@Data
public class MarkPriceUpdate {
    /**
     * 品种代码
     */
    private String symbol;

    /**
     * 标记价格
     */
    private double markPrice;

    /**
     * 指数价格
     */
    private double indexPrice;

    /**
     * 资金费率
     */
    private double fundingRate;

    /**
     * 下次资金时间
     */
    private long nextFundingTime;

    /**
     * 事件时间戳
     */
    private long eventTime;

    // 转换为Instant类型
    public Instant getNextFundingTimeInstant() {
        return Instant.ofEpochMilli(nextFundingTime);
    }

    public Instant getEventTimeInstant() {
        return Instant.ofEpochMilli(eventTime);
    }
}
