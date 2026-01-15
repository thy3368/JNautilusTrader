package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 下单指令实体
 * 表示用户提交的交易订单请求
 */
@Data
public class PlaceOrder {

    @JsonProperty("symbol")
    private String symbol; // 交易对，如 BTCUSDT

    @JsonProperty("side")
    private String side; // 订单方向，BUY 或 SELL

    @JsonProperty("type")
    private String type; // 订单类型，如 LIMIT, MARKET

    @JsonProperty("timeInForce")
    private String timeInForce; // 有效期，如 GTC, IOC

    @JsonProperty("quantity")
    private double quantity; // 订单数量

    @JsonProperty("price")
    private double price; // 订单价格（限价单必填）

    @JsonProperty("newClientOrderId")
    private String newClientOrderId; // 客户自定义订单ID

    public PlaceOrder() {
    }

    public PlaceOrder(String symbol, String side, String type, String timeInForce,
                      double quantity, double price, String newClientOrderId) {
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.timeInForce = timeInForce;
        this.quantity = quantity;
        this.price = price;
        this.newClientOrderId = newClientOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getNewClientOrderId() {
        return newClientOrderId;
    }

    public void setNewClientOrderId(String newClientOrderId) {
        this.newClientOrderId = newClientOrderId;
    }

    @Override
    public String toString() {
        return "PlaceOrder{" +
                "symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", type='" + type + '\'' +
                ", timeInForce='" + timeInForce + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", newClientOrderId='" + newClientOrderId + '\'' +
                '}';
    }

    /**
     * 创建限价买入订单
     */
    public static PlaceOrder createLimitBuyOrder(String symbol, double quantity, double price) {
        return new PlaceOrder(symbol, "BUY", "LIMIT", "GTC", quantity, price, null);
    }

    /**
     * 创建限价卖出订单
     */
    public static PlaceOrder createLimitSellOrder(String symbol, double quantity, double price) {
        return new PlaceOrder(symbol, "SELL", "LIMIT", "GTC", quantity, price, null);
    }

    /**
     * 创建市价买入订单
     */
    public static PlaceOrder createMarketBuyOrder(String symbol, double quantity) {
        return new PlaceOrder(symbol, "BUY", "MARKET", null, quantity, 0, null);
    }

    /**
     * 创建市价卖出订单
     */
    public static PlaceOrder createMarketSellOrder(String symbol, double quantity) {
        return new PlaceOrder(symbol, "SELL", "MARKET", null, quantity, 0, null);
    }
}
