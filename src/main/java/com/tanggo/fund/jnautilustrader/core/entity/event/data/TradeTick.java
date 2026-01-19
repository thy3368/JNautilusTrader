package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

/**
 * 交易Tick数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
     * 事件时间 - 币安服务器发送消息的时间戳 (毫秒)
     * 用于计算网络延迟：事件时间 - 成交时间 = 传输延迟
     */
    @JsonProperty("E")
    public long eventTimeMs;

    /**
     * 成交时间 - 交易在撮合引擎中实际发生的时间戳 (毫秒)
     * 用于确定交易的准确时序
     */
    @JsonProperty("T")
    public long tradeTimeMs; // 币安返回的是Unix时间戳（毫秒）

    /**
     * 方向 (buy/sell)
     */
    @JsonProperty("m")
    public boolean isBuyerMaker; // true表示卖方主动（sell），false表示买方主动（buy）

    // 转换为Instant类型
    public Instant getEventTime() {
        return Instant.ofEpochMilli(eventTimeMs);
    }

    public Instant getTradeTime() {
        return Instant.ofEpochMilli(tradeTimeMs);
    }

    // 保留原有的timestampMs getter/setter方法以保持兼容性
    public long getTimestampMs() {
        return tradeTimeMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.tradeTimeMs = timestampMs;
    }

    public Instant getTimestamp() {
        return getTradeTime();
    }

    /**
     * 获取网络传输延迟 (毫秒)
     */
    public long getNetworkDelayMs() {
        return eventTimeMs - tradeTimeMs;
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
