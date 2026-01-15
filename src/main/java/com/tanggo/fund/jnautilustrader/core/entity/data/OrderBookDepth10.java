package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
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
     * 最后更新ID
     */
    @JsonProperty("lastUpdateId")
    private long lastUpdateId;

    /**
     * 买盘深度
     */
    @JsonProperty("bids")
    private List<List<String>> bids;

    /**
     * 卖盘深度
     */
    @JsonProperty("asks")
    private List<List<String>> asks;

    @Override
    public String toString() {
        return "OrderBookDepth10{" +
                "symbol='" + symbol + '\'' +
                ", lastUpdateId=" + lastUpdateId +
                ", bids=" + bids +
                ", asks=" + asks +
                '}';
    }
}
