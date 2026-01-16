package com.tanggo.fund.jnautilustrader.core.process.cross;

import lombok.Data;

/**
 * 跨交易所套利策略状态类
 * 包含策略运行过程中的所有状态信息
 *
 * @author JNautilusTrader
 * @version 1.0
 */
@Data
public class CrossArbitrageState {

    /**
     * 策略是否运行
     */
    private boolean isRunning;

    /**
     * 策略启动时间（毫秒）
     */
    private long startTime;

    /**
     * 当前运行时间（秒）
     */
    private double currentTime;

    /**
     * 币安最新买入价
     */
    private double binanceBidPrice;

    /**
     * 币安最新卖出价
     */
    private double binanceAskPrice;

    /**
     * 币安最新中间价
     */
    private double binanceMidPrice;

    /**
     * Bitget最新买入价
     */
    private double bitgetBidPrice;

    /**
     * Bitget最新卖出价
     */
    private double bitgetAskPrice;

    /**
     * Bitget最新中间价
     */
    private double bitgetMidPrice;

    /**
     * 最后一次套利时间（毫秒）
     */
    private long lastArbitrageTime;

    /**
     * 总套利次数
     */
    private int arbitrageCount;

    /**
     * 成功套利次数
     */
    private int successfulArbitrageCount;

    /**
     * 失败套利次数
     */
    private int failedArbitrageCount;

    /**
     * 总利润（USDT）
     */
    private double totalProfit;

    /**
     * 当前持仓（BTC）
     */
    private double currentPosition;

    /**
     * 最大持仓（BTC）
     */
    private double maxPosition;

    /**
     * 最小持仓（BTC）
     */
    private double minPosition;

    /**
     * 平均持仓（BTC）
     */
    private double avgPosition;

    /**
     * 总交易量（BTC）
     */
    private double totalVolume;

    /**
     * 最后一次价差（百分比）
     */
    private double lastSpreadPercentage;

    /**
     * 最大价差（百分比）
     */
    private double maxSpreadPercentage;

    /**
     * 平均价差（百分比）
     */
    private double avgSpreadPercentage;

    /**
     * 策略状态信息字符串
     */
    private String statusInfo;

    /**
     * 创建初始状态
     */
    public static CrossArbitrageState initialState() {
        CrossArbitrageState state = new CrossArbitrageState();
        state.isRunning = false;
        state.startTime = 0;
        state.currentTime = 0;
        state.binanceBidPrice = 0;
        state.binanceAskPrice = 0;
        state.binanceMidPrice = 0;
        state.bitgetBidPrice = 0;
        state.bitgetAskPrice = 0;
        state.bitgetMidPrice = 0;
        state.lastArbitrageTime = 0;
        state.arbitrageCount = 0;
        state.successfulArbitrageCount = 0;
        state.failedArbitrageCount = 0;
        state.totalProfit = 0;
        state.currentPosition = 0;
        state.maxPosition = 0;
        state.minPosition = 0;
        state.avgPosition = 0;
        state.totalVolume = 0;
        state.lastSpreadPercentage = 0;
        state.maxSpreadPercentage = 0;
        state.avgSpreadPercentage = 0;
        state.statusInfo = "初始化完成";
        return state;
    }

    /**
     * 启动策略状态
     */
    public void start() {
        this.isRunning = true;
        this.startTime = System.currentTimeMillis();
        this.statusInfo = "策略运行中";
    }

    /**
     * 停止策略状态
     */
    public void stop() {
        this.isRunning = false;
        this.statusInfo = "策略已停止";
    }

    /**
     * 更新策略状态
     */
    public void updateState() {
        this.currentTime = (System.currentTimeMillis() - startTime) / 1000.0;
    }

    /**
     * 记录套利执行
     */
    public void recordArbitrage(boolean success, double profit, double positionChange) {
        arbitrageCount++;
        if (success) {
            successfulArbitrageCount++;
            totalProfit += profit;
            currentPosition += positionChange;
            totalVolume += Math.abs(positionChange);

            if (currentPosition > maxPosition) {
                maxPosition = currentPosition;
            }
            if (currentPosition < minPosition) {
                minPosition = currentPosition;
            }
            avgPosition = totalVolume / arbitrageCount;
        } else {
            failedArbitrageCount++;
        }
        lastArbitrageTime = System.currentTimeMillis();
    }

    /**
     * 计算当前价差百分比
     */
    public double calculateSpreadPercentage() {
        if (binanceMidPrice > 0 && bitgetMidPrice > 0) {
            double spread = Math.abs(binanceMidPrice - bitgetMidPrice);
            double spreadPercentage = (spread / Math.min(binanceMidPrice, bitgetMidPrice)) * 100;
            lastSpreadPercentage = spreadPercentage;
            if (spreadPercentage > maxSpreadPercentage) {
                maxSpreadPercentage = spreadPercentage;
            }
            avgSpreadPercentage = (avgSpreadPercentage * (arbitrageCount - 1) + spreadPercentage) / arbitrageCount;
            return spreadPercentage;
        }
        return 0;
    }

    /**
     * 判断是否有有效市场数据
     */
    public boolean hasValidMarketData() {
        return binanceBidPrice > 0 && binanceAskPrice > 0 && bitgetBidPrice > 0 && bitgetAskPrice > 0;
    }

    /**
     * 判断是否可以进行套利
     */
    public boolean canArbitrage(long minIntervalMs) {
        long currentTimeMs = System.currentTimeMillis();
        return hasValidMarketData() && (currentTimeMs - lastArbitrageTime) >= minIntervalMs;
    }

    @Override
    public String toString() {
        return "CrossArbitrageState{" +
                "isRunning=" + isRunning +
                ", currentTime=" + String.format("%.2f", currentTime) + "s" +
                ", binanceMidPrice=" + String.format("%.2f", binanceMidPrice) +
                ", bitgetMidPrice=" + String.format("%.2f", bitgetMidPrice) +
                ", spread=" + String.format("%.4f", lastSpreadPercentage) + "%" +
                ", arbitrageCount=" + arbitrageCount +
                ", successfulArbitrageCount=" + successfulArbitrageCount +
                ", failedArbitrageCount=" + failedArbitrageCount +
                ", totalProfit=" + String.format("%.4f", totalProfit) + " USDT" +
                ", currentPosition=" + String.format("%.6f", currentPosition) + " BTC" +
                ", totalVolume=" + String.format("%.4f", totalVolume) + " BTC" +
                ", status='" + statusInfo + '\'' +
                '}';
    }
}