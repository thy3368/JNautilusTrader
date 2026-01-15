package com.tanggo.fund.jnautilustrader.core.process;

/**
 * Avellaneda-Stoikov 策略参数类
 * 包含策略的所有配置参数
 */
public class AvellanedaStoikovParams {
    // 波动率 (每日)
    public double volatility;
    // 平均订单到达率 (每分钟)
    public double lambda;
    // 做市商风险厌恶系数
    public double gamma;
    // 初始库存
    public double initialInventory;
    // 订单数量 (每次下单的数量)
    public double orderQuantity;
    // 网格间距
    public double gridSpacing;
    // 策略运行时间 (秒)
    public double runTime;
    // 交易对
    public String symbol;

    public AvellanedaStoikovParams() {
        // 默认参数
        this.volatility = 0.02;  // 2% 每日波动率
        this.lambda = 1.0;       // 每分钟1个订单
        this.gamma = 0.1;        // 风险厌恶系数
        this.initialInventory = 0.0;  // 初始库存为0
        this.orderQuantity = 0.001;  // 每次下单0.001个BTC
        this.gridSpacing = 0.0005;  // 0.05% 的网格间距
        this.runTime = 3600;     // 运行1小时
        this.symbol = "BTCUSDT"; // 默认交易对
    }

    public AvellanedaStoikovParams(double volatility, double lambda, double gamma,
                                  double initialInventory, double orderQuantity,
                                  double gridSpacing, double runTime, String symbol) {
        this.volatility = volatility;
        this.lambda = lambda;
        this.gamma = gamma;
        this.initialInventory = initialInventory;
        this.orderQuantity = orderQuantity;
        this.gridSpacing = gridSpacing;
        this.runTime = runTime;
        this.symbol = symbol;
    }

    public static AvellanedaStoikovParams defaultParams() {
        return new AvellanedaStoikovParams();
    }

    public AvellanedaStoikovParams withVolatility(double volatility) {
        this.volatility = volatility;
        return this;
    }

    public AvellanedaStoikovParams withLambda(double lambda) {
        this.lambda = lambda;
        return this;
    }

    public AvellanedaStoikovParams withGamma(double gamma) {
        this.gamma = gamma;
        return this;
    }

    public AvellanedaStoikovParams withInitialInventory(double initialInventory) {
        this.initialInventory = initialInventory;
        return this;
    }

    public AvellanedaStoikovParams withOrderQuantity(double orderQuantity) {
        this.orderQuantity = orderQuantity;
        return this;
    }

    public AvellanedaStoikovParams withGridSpacing(double gridSpacing) {
        this.gridSpacing = gridSpacing;
        return this;
    }

    public AvellanedaStoikovParams withRunTime(double runTime) {
        this.runTime = runTime;
        return this;
    }

    public AvellanedaStoikovParams withSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }
}