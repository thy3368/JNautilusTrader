package com.tanggo.fund.jnautilustrader.core.entity.data;

import lombok.Data;

import java.time.Instant;

/**
 * 报价Tick数据
 */
@Data
public class QuoteTick {
    /**
     * 品种代码
     */
    private String symbol;

    /**
     * 最佳买价
     */
    private double bidPrice;

    /**
     * 最佳买量
     */
    private double bidQuantity;

    /**
     * 最佳卖价
     */
    private double askPrice;

    /**
     * 最佳卖量
     */
    private double askQuantity;

    /**
     * 时间戳
     */
    private long timestampMs;

    /**
     * 开盘价
     */
    private double openPrice;

    /**
     * 最高价
     */
    private double highPrice;

    /**
     * 最低价
     */
    private double lowPrice;

    /**
     * 收盘价
     */
    private double closePrice;

    /**
     * 成交量
     */
    private double volume;

    /**
     * 成交额
     */
    private double quoteVolume;

    // 转换为Instant类型
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestampMs);
    }
}