package com.tanggo.fund.jnautilustrader.core.entity;

import com.tanggo.fund.jnautilustrader.core.entity.data.*;

/**
 * 市场数据枚举
 * 表示币安WebSocket可能返回的不同类型的市场数据对象
 */
public enum MarketData {

    TRADE_TICK(null),
    QUOTE_TICK(null),
    BAR(null),
    ORDER_BOOK_DEPTH(null),
    ORDER_BOOK_DELTA(null),
    MARK_PRICE_UPDATE(null),
    INDEX_PRICE_UPDATE(null),
    FUNDING_RATE_UPDATE(null),
    INSTRUMENT_STATUS(null),
    INSTRUMENT_CLOSE(null);

    private Object message;

    MarketData(Object message) {
        this.message = message;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    /**
     * 根据消息对象类型获取对应的MarketData枚举
     */
    public static MarketData fromMessage(Object message) {
        if (message instanceof TradeTick) {
            return TRADE_TICK;
        } else if (message instanceof QuoteTick) {
            return QUOTE_TICK;
        } else if (message instanceof Bar) {
            return BAR;
        } else if (message instanceof OrderBookDepth10) {
            return ORDER_BOOK_DEPTH;
        } else if (message instanceof OrderBookDeltas || message instanceof OrderBookDelta) {
            return ORDER_BOOK_DELTA;
        } else if (message instanceof MarkPriceUpdate) {
            return MARK_PRICE_UPDATE;
        } else if (message instanceof IndexPriceUpdate) {
            return INDEX_PRICE_UPDATE;
        } else if (message instanceof FundingRateUpdate) {
            return FUNDING_RATE_UPDATE;
        } else if (message instanceof InstrumentStatus) {
            return INSTRUMENT_STATUS;
        } else if (message instanceof InstrumentClose) {
            return INSTRUMENT_CLOSE;
        }
        throw new IllegalArgumentException("Unsupported message type: " + message.getClass().getName());
    }

    /**
     * 创建包含实际数据的MarketData实例
     */
    public static MarketData createWithData(Object data) {
        MarketData marketData = fromMessage(data);
        marketData.setMessage(data);
        return marketData;
    }

    @Override
    public String toString() {
        if (message != null) {
            return message.toString();
        }
        return name();
    }
}
