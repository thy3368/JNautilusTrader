package com.tanggo.fund.jnautilustrader.core.entity.event.trade;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 订单更新事件
 * 表示订单状态的变化（包括新建、取消、拒绝、成交等）
 * 对应币安的executionReport事件
 *
 * 注意：该类不依赖Jackson注解，通过手动解析币安字段构建对象
 */
@Data
@NoArgsConstructor
public class OrderUpdate {

    /**
     * 事件类型 - 固定为 "executionReport"
     * 币安字段: e
     */
    private String eventType;

    /**
     * 事件时间（毫秒时间戳）
     * 币安字段: E
     */
    private long eventTime;

    /**
     * 交易对符号
     * 币安字段: s
     */
    private String symbol;

    /**
     * 客户端订单ID
     * 币安字段: c
     */
    private String clientOrderId;

    /**
     * 订单方向 (BUY/SELL)
     * 币安字段: S
     */
    private String side;

    /**
     * 订单类型 (LIMIT/MARKET/STOP_LOSS等)
     * 币安字段: o
     */
    private String orderType;

    /**
     * 有效期类型 (GTC/IOC/FOK等)
     * 币安字段: f
     */
    private String timeInForce;

    /**
     * 订单原始数量
     * 币安字段: q
     */
    private double originalQuantity;

    /**
     * 订单原始价格
     * 币安字段: p
     */
    private double originalPrice;

    /**
     * 止损/止盈价格
     * 币安字段: P
     */
    private double stopPrice;

    /**
     * 执行类型 (NEW/CANCELED/REPLACED/REJECTED/TRADE/EXPIRED)
     * 币安字段: x
     */
    private String executionType;

    /**
     * 订单当前状态 (NEW/PARTIALLY_FILLED/FILLED/CANCELED/REJECTED等)
     * 币安字段: X
     */
    private String orderStatus;

    /**
     * 订单拒绝原因
     * 币安字段: r
     */
    private String rejectReason;

    /**
     * 交易所订单ID
     * 币安字段: i
     */
    private long orderId;

    /**
     * 最后成交数量
     * 币安字段: l
     */
    private double lastExecutedQuantity;

    /**
     * 累计成交数量
     * 币安字段: z
     */
    private double cumulativeFilledQuantity;

    /**
     * 最后成交价格
     * 币安字段: L
     */
    private double lastExecutedPrice;

    /**
     * 手续费金额
     * 币安字段: n
     */
    private double commissionAmount;

    /**
     * 手续费资产类型
     * 币安字段: N
     */
    private String commissionAsset;

    /**
     * 交易时间（毫秒时间戳）
     * 币安字段: T
     */
    private long transactionTime;

    /**
     * 成交ID
     * 币安字段: t
     */
    private long tradeId;

    /**
     * 是否为做市商成交
     * 币安字段: m
     */
    private boolean isMaker;

    /**
     * 是否为工作中的订单（挂单）
     * 币安字段: w
     */
    private boolean isOrderWorking;

    /**
     * 成交金额
     * 币安字段: Y
     */
    private double cumulativeQuoteQuantity;

    /**
     * 最后成交金额
     * 币安字段: Z
     */
    private double lastQuoteQuantity;

    /**
     * 订单列表ID（OCO订单）
     * 币安字段: g
     */
    private long orderListId = -1;

    /**
     * 原始客户端订单ID（修改订单时）
     * 币安字段: C
     */
    private String originalClientOrderId;

    // ========== 业务方法 ==========

    /**
     * 获取订单状态枚举
     */
    public OrderStatus getOrderStatusEnum() {
        return OrderStatus.fromBinance(orderStatus);
    }

    /**
     * 获取执行类型枚举
     */
    public ExecutionType getExecutionTypeEnum() {
        return ExecutionType.fromBinance(executionType);
    }

    /**
     * 获取事件时间的Instant对象
     */
    public Instant getEventTimeInstant() {
        return Instant.ofEpochMilli(eventTime);
    }

    /**
     * 获取交易时间的Instant对象
     */
    public Instant getTransactionTimeInstant() {
        return Instant.ofEpochMilli(transactionTime);
    }

    /**
     * 判断是否为买单
     */
    public boolean isBuy() {
        return "BUY".equals(side);
    }

    /**
     * 判断是否为卖单
     */
    public boolean isSell() {
        return "SELL".equals(side);
    }

    /**
     * 判断是否有新的成交
     */
    public boolean hasNewExecution() {
        return ExecutionType.TRADE.name().equals(executionType) && lastExecutedQuantity > 0;
    }

    /**
     * 判断订单是否为最终状态
     */
    public boolean isFinalState() {
        return getOrderStatusEnum().isFinalState();
    }

    /**
     * 获取剩余未成交数量
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
     * 获取平均成交价格
     */
    public double getAveragePrice() {
        if (cumulativeFilledQuantity == 0) {
            return 0.0;
        }
        return cumulativeQuoteQuantity / cumulativeFilledQuantity;
    }

    @Override
    public String toString() {
        return String.format(
            "OrderUpdate{symbol='%s', orderId=%d, clientOrderId='%s', status=%s, " +
            "executionType=%s, side=%s, type=%s, origQty=%.8f, price=%.8f, " +
            "filledQty=%.8f(%.2f%%), avgPrice=%.8f, remainQty=%.8f}",
            symbol, orderId, clientOrderId, orderStatus, executionType,
            side, orderType, originalQuantity, originalPrice,
            cumulativeFilledQuantity, getFilledPercentage(), getAveragePrice(),
            getRemainingQuantity()
        );
    }
}
