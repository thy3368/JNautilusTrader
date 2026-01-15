package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * 交易Tick数据
 */
@Data
public class TradeTick {
    /**
     * 交易ID
     */
    @JsonProperty("t")
    public String tradeId;

    /**
     * 品种代码
     */
    @JsonProperty("s")
    public String symbol;

    /**
     * 价格
     */
    @JsonProperty("p")
    public double price;

    /**
     * 数量
     */
    @JsonProperty("q")
    public double quantity;

    /**
     * 交易时间
     */
    @JsonProperty("T")
    public long timestampMs; // 币安返回的是Unix时间戳（毫秒）
    /**
     * 方向 (buy/sell)
     */
    @JsonProperty("m")
    public boolean isBuyerMaker; // true表示卖方主动（sell），false表示买方主动（buy）

    // 转换为Instant类型
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestampMs);
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    // 转换为字符串表示的方向
    public String getSide() {
        return isBuyerMaker ? "sell" : "buy";
    }

    /**
     * 成交量
     */
    // 成交量 = 价格 * 数量
    public double getVolume() {
        return price * quantity;
    }
}
