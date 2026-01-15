package com.tanggo.fund.jnautilustrader.core.entity.data;

import java.time.Instant;

/**
 * 交易Tick数据
 */
public class TradeTick {
    /**
     * 交易ID
     */
    public String tradeId;

    /**
     * 品种代码
     */
    public String symbol;

    /**
     * 价格
     */
    public double price;

    /**
     * 数量
     */
    public double quantity;

    /**
     * 交易时间
     */
    public Instant timestamp;

    /**
     * 方向 (buy/sell)
     */
    public String side;

    /**
     * 成交量
     */
    public double volume;
}
