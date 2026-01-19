package com.tanggo.fund.jnautilustrader.core.entity.data;

/**
 * 执行类型枚举
 * 表示订单执行报告的具体类型
 */
public enum ExecutionType {
    /**
     * 新建订单
     */
    NEW,

    /**
     * 已取消
     */
    CANCELED,

    /**
     * 已替换（修改订单）
     */
    REPLACED,

    /**
     * 已拒绝
     */
    REJECTED,

    /**
     * 成交 - 订单有新的成交
     */
    TRADE,

    /**
     * 已过期
     */
    EXPIRED,

    /**
     * 修改中
     */
    AMENDMENT;

    /**
     * 从币安执行类型字符串转换
     */
    public static ExecutionType fromBinance(String binanceType) {
        if (binanceType == null) {
            throw new IllegalArgumentException("Execution type cannot be null");
        }
        return valueOf(binanceType.toUpperCase());
    }
}
