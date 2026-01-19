package com.tanggo.fund.jnautilustrader.core.entity.data;

/**
 * 订单状态枚举
 * 映射币安订单状态
 */
public enum OrderStatus {
    /**
     * 新建订单 - 订单已被交易所接受
     */
    NEW,

    /**
     * 部分成交 - 订单已部分成交
     */
    PARTIALLY_FILLED,

    /**
     * 完全成交 - 订单已完全成交
     */
    FILLED,

    /**
     * 已取消 - 订单已被用户取消
     */
    CANCELED,

    /**
     * 待取消 - 取消请求已发送但尚未确认
     * (某些交易所使用)
     */
    PENDING_CANCEL,

    /**
     * 已拒绝 - 订单被交易所拒绝
     */
    REJECTED,

    /**
     * 已过期 - 订单已过期（如IOC、FOK订单未成交）
     */
    EXPIRED,

    /**
     * 已失效 - 订单已失效
     * (如市价单在撮合前因价格保护而失效)
     */
    EXPIRED_IN_MATCH;

    /**
     * 判断订单是否为最终状态（不会再变化）
     */
    public boolean isFinalState() {
        return this == FILLED || this == CANCELED || this == REJECTED ||
               this == EXPIRED || this == EXPIRED_IN_MATCH;
    }

    /**
     * 判断订单是否已成交（部分或完全）
     */
    public boolean hasExecution() {
        return this == PARTIALLY_FILLED || this == FILLED;
    }

    /**
     * 判断订单是否仍然活跃（可能继续成交）
     */
    public boolean isActive() {
        return this == NEW || this == PARTIALLY_FILLED;
    }

    /**
     * 从币安状态字符串转换
     */
    public static OrderStatus fromBinance(String binanceStatus) {
        if (binanceStatus == null) {
            throw new IllegalArgumentException("Order status cannot be null");
        }
        return valueOf(binanceStatus.toUpperCase());
    }
}
