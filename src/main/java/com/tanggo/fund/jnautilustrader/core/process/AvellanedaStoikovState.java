package com.tanggo.fund.jnautilustrader.core.process;

/**
 * Avellaneda-Stoikov 策略状态类
 * 用于跟踪策略的执行状态
 */
public class AvellanedaStoikovState {
    // 当前时间
    public double currentTime;
    // 当前库存
    public double inventory;
    // 执行的交易数量
    public int tradeCount;
    // 总利润
    public double totalProfit;
    // 上一次执行时间
    public double lastExecutionTime;
    // 最后一次交易价格
    public double lastTradePrice;
    // 订单簿中间价
    public double midPrice;
    // 最优买入价格
    public double bestBid;
    // 最优卖出价格
    public double bestAsk;
    // 是否在运行
    public boolean isRunning;
    // 时间戳
    public long startTime;

    public AvellanedaStoikovState() {
        this.currentTime = 0;
        this.inventory = 0;
        this.tradeCount = 0;
        this.totalProfit = 0;
        this.lastExecutionTime = 0;
        this.lastTradePrice = 0;
        this.midPrice = 0;
        this.bestBid = 0;
        this.bestAsk = 0;
        this.isRunning = false;
    }

    public AvellanedaStoikovState(double currentTime, double inventory, int tradeCount,
                                  double totalProfit, double lastExecutionTime,
                                  double lastTradePrice, double midPrice,
                                  double bestBid, double bestAsk, boolean isRunning) {
        this.currentTime = currentTime;
        this.inventory = inventory;
        this.tradeCount = tradeCount;
        this.totalProfit = totalProfit;
        this.lastExecutionTime = lastExecutionTime;
        this.lastTradePrice = lastTradePrice;
        this.midPrice = midPrice;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.isRunning = isRunning;
    }

    public static AvellanedaStoikovState initialState() {
        return new AvellanedaStoikovState();
    }

    public void reset() {
        this.currentTime = 0;
        this.inventory = 0;
        this.tradeCount = 0;
        this.totalProfit = 0;
        this.lastExecutionTime = 0;
        this.lastTradePrice = 0;
        this.midPrice = 0;
        this.bestBid = 0;
        this.bestAsk = 0;
        this.isRunning = false;
    }

    public String toString() {
        return String.format("Time: %.2f, Inventory: %.4f, Trades: %d, Profit: %.2f, " +
                        "MidPrice: %.2f, BestBid: %.2f, BestAsk: %.2f",
                currentTime, inventory, tradeCount, totalProfit,
                midPrice, bestBid, bestAsk);
    }
}
