package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import lombok.Data;

import java.time.Instant;

/**
 * K线数据
 */
@Data
public class Bar {
    /**
     * 品种代码
     */
    private String symbol;

    /**
     * 开盘时间
     */
    private long openTime;

    /**
     * 收盘时间
     */
    private long closeTime;

    /**
     * K线间隔
     */
    private String interval;

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
     * 交易次数
     */
    private int tradeCount;

    /**
     * 成交额
     */
    private double quoteVolume;

    /**
     * 主动买入成交量
     */
    private double takerBuyVolume;

    /**
     * 主动买入成交额
     */
    private double takerBuyQuoteVolume;

    /**
     * 是否完成
     */
    private boolean isClosed;

    // 转换为Instant类型
    public Instant getOpenTimeInstant() {
        return Instant.ofEpochMilli(openTime);
    }

    public Instant getCloseTimeInstant() {
        return Instant.ofEpochMilli(closeTime);
    }
}
