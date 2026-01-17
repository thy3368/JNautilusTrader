package com.tanggo.fund.jnautilustrader.core.entity.data;

import lombok.Data;

import java.time.Instant;

/**
 * 资金费率更新数据
 */
@Data
public class FundingRateUpdate {
    /**
     * 品种代码
     */
    private String symbol;

    /**
     * 资金费率
     */
    private double fundingRate;

    /**
     * 资金时间
     */
    private long fundingTime;

    /**
     * 事件时间戳
     */
    private long eventTime;

    // 转换为Instant类型
    public Instant getFundingTimeInstant() {
        return Instant.ofEpochMilli(fundingTime);
    }

    public Instant getEventTimeInstant() {
        return Instant.ofEpochMilli(eventTime);
    }
}