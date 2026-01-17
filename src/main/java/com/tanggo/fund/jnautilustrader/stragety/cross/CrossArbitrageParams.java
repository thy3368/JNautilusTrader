package com.tanggo.fund.jnautilustrader.stragety.cross;

import lombok.Data;

/**
 * 跨交易所套利策略参数类
 * 包含策略运行所需的所有配置参数
 *
 * @author JNautilusTrader
 * @version 1.0
 */
@Data
public class CrossArbitrageParams {

    /**
     * 交易对（如 BTCUSDT）
     */
    private String symbol;

    /**
     * 币安交易所名称
     */
    private String binanceExchangeName;

    /**
     * Bitget交易所名称
     */
    private String bitgetExchangeName;

    /**
     * 套利触发阈值（价差百分比）
     * 当两个交易所的价差超过此值时触发套利
     */
    private double arbitrageThreshold;

    /**
     * 最小套利利润（考虑手续费和滑点后的净利润）
     */
    private double minProfit;

    /**
     * 每次套利的订单数量
     */
    private double orderQuantity;

    /**
     * 交易所手续费率（双边）
     * 币安现货手续费率（默认0.1%）
     */
    private double binanceFeeRate;

    /**
     * Bitget现货手续费率（默认0.1%）
     */
    private double bitgetFeeRate;

    /**
     * 滑点容忍度（百分比）
     */
    private double slippageTolerance;

    /**
     * 策略运行时间（秒）
     */
    private double runTime;

    /**
     * 检查套利机会的时间间隔（毫秒）
     */
    private long checkInterval;

    /**
     * 最大持仓限制
     */
    private double maxPositionLimit;

    /**
     * 是否启用调试模式
     */
    private boolean debugMode;

    /**
     * 创建默认参数
     */
    public static CrossArbitrageParams defaultParams() {
        CrossArbitrageParams params = new CrossArbitrageParams();
        params.symbol = "BTCUSDT";
        params.binanceExchangeName = "BINANCE";
        params.bitgetExchangeName = "BITGET";
        params.arbitrageThreshold = 0.1; // 0.1%的价差触发套利
        params.minProfit = 0.0005; // 最低0.05%的净利润
        params.orderQuantity = 0.001; // 每次套利0.001 BTC
        params.binanceFeeRate = 0.001; // 0.1%手续费
        params.bitgetFeeRate = 0.001; // 0.1%手续费
        params.slippageTolerance = 0.0005; // 0.05%滑点容忍
        params.runTime = 3600; // 运行1小时
        params.checkInterval = 100; // 每100毫秒检查一次
        params.maxPositionLimit = 0.01; // 最大持仓0.01 BTC
        params.debugMode = true;
        return params;
    }

    /**
     * 计算总成本（包含手续费）
     */
    public double calculateTotalCost(double price, double quantity, String exchange) {
        double feeRate = exchange.equals(binanceExchangeName) ? binanceFeeRate : bitgetFeeRate;
        return price * quantity * (1 + feeRate);
    }

    /**
     * 计算总收益（包含手续费）
     */
    public double calculateTotalRevenue(double price, double quantity, String exchange) {
        double feeRate = exchange.equals(binanceExchangeName) ? binanceFeeRate : bitgetFeeRate;
        return price * quantity * (1 - feeRate);
    }

    /**
     * 检查是否满足套利条件
     */
    public boolean shouldArbitrage(double binancePrice, double bitgetPrice) {
        double spread = Math.abs(binancePrice - bitgetPrice);
        double spreadPercentage = (spread / Math.min(binancePrice, bitgetPrice)) * 100;
        return spreadPercentage >= arbitrageThreshold;
    }

    @Override
    public String toString() {
        return "CrossArbitrageParams{" +
                "symbol='" + symbol + '\'' +
                ", binanceExchangeName='" + binanceExchangeName + '\'' +
                ", bitgetExchangeName='" + bitgetExchangeName + '\'' +
                ", arbitrageThreshold=" + arbitrageThreshold +
                ", minProfit=" + minProfit +
                ", orderQuantity=" + orderQuantity +
                ", binanceFeeRate=" + binanceFeeRate +
                ", bitgetFeeRate=" + bitgetFeeRate +
                ", slippageTolerance=" + slippageTolerance +
                ", runTime=" + runTime +
                ", checkInterval=" + checkInterval +
                ", maxPositionLimit=" + maxPositionLimit +
                ", debugMode=" + debugMode +
                '}';
    }
}
