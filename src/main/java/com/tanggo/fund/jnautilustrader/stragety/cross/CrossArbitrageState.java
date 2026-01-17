package com.tanggo.fund.jnautilustrader.stragety.cross;

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
     * 构造函数 - 接受策略参数
     */
    public CrossArbitrageState(CrossArbitrageParams params) {
        this.params = params;
        this.isRunning = false;
        this.startTime = 0;
        this.currentTime = 0;
        this.binanceBidPrice = 0;
        this.binanceAskPrice = 0;
        this.binanceMidPrice = 0;
        this.bitgetBidPrice = 0;
        this.bitgetAskPrice = 0;
        this.bitgetMidPrice = 0;
        this.lastArbitrageTime = 0;
        this.arbitrageCount = 0;
        this.successfulArbitrageCount = 0;
        this.failedArbitrageCount = 0;
        this.totalProfit = 0;
        this.currentPosition = 0;
        this.maxPosition = 0;
        this.minPosition = 0;
        this.avgPosition = 0;
        this.totalVolume = 0;
        this.lastSpreadPercentage = 0;
        this.maxSpreadPercentage = 0;
        this.avgSpreadPercentage = 0;
        this.statusInfo = "初始化完成";
    }

    /**
     * 无参构造函数 - Spring需要
     */
    public CrossArbitrageState() {
        this.isRunning = false;
        this.startTime = 0;
        this.currentTime = 0;
        this.binanceBidPrice = 0;
        this.binanceAskPrice = 0;
        this.binanceMidPrice = 0;
        this.bitgetBidPrice = 0;
        this.bitgetAskPrice = 0;
        this.bitgetMidPrice = 0;
        this.lastArbitrageTime = 0;
        this.arbitrageCount = 0;
        this.successfulArbitrageCount = 0;
        this.failedArbitrageCount = 0;
        this.totalProfit = 0;
        this.currentPosition = 0;
        this.maxPosition = 0;
        this.minPosition = 0;
        this.avgPosition = 0;
        this.totalVolume = 0;
        this.lastSpreadPercentage = 0;
        this.maxSpreadPercentage = 0;
        this.avgSpreadPercentage = 0;
        this.statusInfo = "初始化完成";
    }

    /**
     * 创建初始状态
     */
    public static CrossArbitrageState initialState() {
        return new CrossArbitrageState();
    }

    /**
     * 设置策略参数
     */
    public void setParams(CrossArbitrageParams params) {
        this.params = params;
    }

    // 策略参数
    private CrossArbitrageParams params;

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
