package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 订单簿增量更新列表实体类
 * 包含多个订单簿增量更新数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookDeltas {
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
     * 买盘增量更新（价格和数量）
     */
    @JsonProperty("b")
    private List<PriceLevel> bids;

    /**
     * 卖盘增量更新（价格和数量）
     */
    @JsonProperty("a")
    private List<PriceLevel> asks;

    // 转换为Instant类型
    public Instant getEventTime() {
        return Instant.ofEpochMilli(eventTime);
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    @Override
    public String toString() {
        return "OrderBookDeltas{" +
                "symbol='" + symbol + '\'' +
                ", eventTime=" + getEventTime() +
                ", lastUpdateId=" + lastUpdateId +
                ", bids=" + bids +
                ", asks=" + asks +
                '}';
    }
}
