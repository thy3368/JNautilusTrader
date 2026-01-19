package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

/**
 * 成交回报
 * 表示订单的实际成交信息（从OrderUpdate中提取）
 * 用于记录每一笔实际发生的交易
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeExecution {

    /**
     * 成交ID（交易所返回）
     */
    private long tradeId;

    /**
     * 订单ID（交易所返回）
     */
    private long orderId;

    /**
     * 客户端订单ID
     */
    private String clientOrderId;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 订单方向 (BUY/SELL)
     */
    private String side;

    /**
     * 成交价格
     */
    private double price;

    /**
     * 成交数量
     */
    private double quantity;

    /**
     * 成交金额（价格 * 数量）
     */
    private double quoteQuantity;

    /**
     * 手续费金额
     */
    private double commission;

    /**
     * 手续费资产类型
     */
    private String commissionAsset;

    /**
     * 是否为做市商成交
     * true: maker成交（挂单成交，通常手续费更低）
     * false: taker成交（吃单成交）
     */
    private boolean isMaker;

    /**
     * 成交时间（毫秒时间戳）
     */
    private long executionTime;

    /**
     * 事件接收时间（毫秒时间戳）
     */
    private long eventTime;

    /**
     * 订单当前累计成交数量
     */
    private double cumulativeFilledQuantity;

    /**
     * 订单原始数量
     */
    private double originalQuantity;

    /**
     * 订单当前状态
     */
    private String orderStatus;

    // ========== 构造函数 ==========

    public TradeExecution() {
    }

    /**
     * 从OrderUpdate创建TradeExecution
     */
    public static TradeExecution fromOrderUpdate(OrderUpdate orderUpdate) {
        if (!orderUpdate.hasNewExecution()) {
            return null; // 如果没有新的成交，返回null
        }

        TradeExecution execution = new TradeExecution();
        execution.setTradeId(orderUpdate.getTradeId());
        execution.setOrderId(orderUpdate.getOrderId());
        execution.setClientOrderId(orderUpdate.getClientOrderId());
        execution.setSymbol(orderUpdate.getSymbol());
        execution.setSide(orderUpdate.getSide());
        execution.setPrice(orderUpdate.getLastExecutedPrice());
        execution.setQuantity(orderUpdate.getLastExecutedQuantity());
        execution.setQuoteQuantity(orderUpdate.getLastQuoteQuantity());
        execution.setCommission(orderUpdate.getCommissionAmount());
        execution.setCommissionAsset(orderUpdate.getCommissionAsset());
        execution.setMaker(orderUpdate.isMaker());
        execution.setExecutionTime(orderUpdate.getTransactionTime());
        execution.setEventTime(orderUpdate.getEventTime());
        execution.setCumulativeFilledQuantity(orderUpdate.getCumulativeFilledQuantity());
        execution.setOriginalQuantity(orderUpdate.getOriginalQuantity());
        execution.setOrderStatus(orderUpdate.getOrderStatus());

        return execution;
    }

    // ========== 业务方法 ==========

    /**
     * 判断是否为买入成交
     */
    public boolean isBuy() {
        return "BUY".equals(side);
    }

    /**
     * 判断是否为卖出成交
     */
    public boolean isSell() {
        return "SELL".equals(side);
    }

    /**
     * 获取成交时间的Instant对象
     */
    public Instant getExecutionTimeInstant() {
        return Instant.ofEpochMilli(executionTime);
    }

    /**
     * 获取事件时间的Instant对象
     */
    public Instant getEventTimeInstant() {
        return Instant.ofEpochMilli(eventTime);
    }

    /**
     * 计算网络延迟（毫秒）
     */
    public long getNetworkDelayMs() {
        return eventTime - executionTime;
    }

    /**
     * 获取成交角色描述
     */
    public String getRoleDescription() {
        return isMaker ? "Maker(挂单)" : "Taker(吃单)";
    }

    /**
     * 判断订单是否已完全成交
     */
    public boolean isFullyFilled() {
        return "FILLED".equals(orderStatus);
    }

    /**
     * 获取订单剩余未成交数量
     */
    public double getRemainingQuantity() {
        return originalQuantity - cumulativeFilledQuantity;
    }

    /**
     * 获取成交百分比
     */
    public double getFilledPercentage() {
        if (originalQuantity == 0) {
            return 0.0;
        }
        return (cumulativeFilledQuantity / originalQuantity) * 100.0;
    }

    /**
     * 计算实际支付金额（含手续费）
     * 买入时：成交金额 + 手续费（如果手续费是quote资产）
     * 卖出时：成交金额 - 手续费（如果手续费是quote资产）
     */
    public double getNetAmount() {
        // 简化处理：如果手续费资产是quote资产的一部分，需要调整
        return quoteQuantity;
    }

    @Override
    public String toString() {
        return String.format(
            "TradeExecution{tradeId=%d, orderId=%d, symbol='%s', side=%s, " +
            "price=%.8f, qty=%.8f, quoteQty=%.8f, commission=%.8f %s, " +
            "role=%s, status=%s, filled=%.2f%%, remaining=%.8f, time=%s}",
            tradeId, orderId, symbol, side,
            price, quantity, quoteQuantity, commission, commissionAsset,
            getRoleDescription(), orderStatus, getFilledPercentage(),
            getRemainingQuantity(), getExecutionTimeInstant()
        );
    }
}
