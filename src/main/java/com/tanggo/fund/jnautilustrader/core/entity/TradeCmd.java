package com.tanggo.fund.jnautilustrader.core.entity;

import com.tanggo.fund.jnautilustrader.core.entity.trade.PlaceOrder;

/**
 * 交易命令枚举
 * 表示系统支持的各种交易操作命令
 */
public enum TradeCmd {

    PLACE_ORDER(null),
    CANCEL_ORDER(null),
    MODIFY_ORDER(null),
    QUERY_ORDER(null),
    QUERY_ACCOUNT(null),
    QUERY_POSITION(null),
    CANCEL_ALL_ORDERS(null),
    CLOSE_POSITION(null);

    private Object message;

    TradeCmd(Object message) {
        this.message = message;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    /**
     * 根据消息对象类型获取对应的TradeCmd枚举
     */
    public static TradeCmd fromMessage(Object message) {
        if (message instanceof PlaceOrder) {
            return PLACE_ORDER;
        }
        // 可以根据需要添加更多消息类型的判断
        throw new IllegalArgumentException("Unsupported message type: " + message.getClass().getName());
    }

    /**
     * 创建包含实际数据的TradeCmd实例
     */
    public static TradeCmd createWithData(Object data) {
        TradeCmd tradeCmd = fromMessage(data);
        tradeCmd.setMessage(data);
        return tradeCmd;
    }
}
