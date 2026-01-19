package com.tanggo.fund.jnautilustrader.core.entity.event.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 订单簿深度10档实体类
 * 包含订单簿的10档深度数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookDepth10 {
    /**
     * 品种代码
     */
    @JsonProperty("s")
    private String symbol;

    /**
     * 事件时间
     */
    @JsonProperty("E")
    private long eventTime;

    /**
     * 最后更新ID
     */
    @JsonProperty("lastUpdateId")
    private long lastUpdateId;

    /**
     * 买盘深度（价格从高到低排序）
     */
    @JsonProperty("bids")
    private List<PriceLevel> bids;

    /**
     * 卖盘深度（价格从低到高排序）
     */
    @JsonProperty("asks")
    private List<PriceLevel> asks;

    @Override
    public String toString() {
        return "OrderBookDepth10{" +
                "symbol='" + symbol + '\'' +
                ", eventTime=" + eventTime +
                ", lastUpdateId=" + lastUpdateId +
                ", bids=" + bids +
                ", asks=" + asks +
                '}';
    }
}
