package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * 订单簿增量更新实体类
 * 表示订单簿的增量更新数据
 */
@Data
public class OrderBookDelta {
    /**
     * 品种代码
     */
    @JsonProperty("s")
    private String symbol;

    /**
     * 更新时间戳
     */
    @JsonProperty("E")
    private long eventTime;

    /**
     * 最后更新ID
     */
    @JsonProperty("u")
    private long lastUpdateId;

    /**
     * 最佳买价
     */
    @JsonProperty("b")
    private double bidPrice;

    /**
     * 最佳买量
     */
    @JsonProperty("B")
    private double bidQuantity;

    /**
     * 最佳卖价
     */
    @JsonProperty("a")
    private double askPrice;

    /**
     * 最佳卖量
     */
    @JsonProperty("A")
    private double askQuantity;

    // 转换为Instant类型
    public Instant getEventTime() {
        return Instant.ofEpochMilli(eventTime);
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    @Override
    public String toString() {
        return "OrderBookDelta{" +
                "symbol='" + symbol + '\'' +
                ", eventTime=" + getEventTime() +
                ", lastUpdateId=" + lastUpdateId +
                ", bidPrice=" + bidPrice +
                ", bidQuantity=" + bidQuantity +
                ", askPrice=" + askPrice +
                ", askQuantity=" + askQuantity +
                '}';
    }
}