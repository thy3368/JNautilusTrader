package com.tanggo.fund.jnautilustrader.core.process.stoikov;

import com.tanggo.fund.jnautilustrader.core.entity.*;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.PlaceOrder;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Avellaneda-Stoikov 做市策略实现类
 * <p>
 * 该策略基于 Avellaneda-Stoikov 模型，是一种量化做市策略，通过优化买卖价差来最大化利润。
 * <p>
 * 核心公式：
 * - 预留价格 (Reservation Price): r = s - q * γ * σ^2 * (T - t)
 * - 最优价差: δ = γ * σ^2 * (T - t) + (2/γ) * ln(1 + γ/k)
 * - 最优买价: bid = r - δ/2
 * - 最优卖价: ask = r + δ/2
 * <p>
 * 其中：
 * - s: 当前市场中间价
 * - q: 当前库存
 * - γ: 风险厌恶系数
 * - σ: 波动率
 * - T - t: 剩余交易时间
 * - k: 订单到达率
 * <p>
 * 基于币安 WebSocket 实现实时市场数据接收和订单执行
 *
 * @author JNautilusTrader
 * @version 1.0
 */

@Component
public class AvellanedaStoikovStrategy implements Actor {

    // 策略参数 todo可以repo
    private final AvellanedaStoikovParams params;
    // 策略状态 todo可以repo
    private final AvellanedaStoikovState state;

    // 事件仓库
    private final EventRepo<MarketData> marketDataRepo;
    private final EventRepo<TradeCmd> tradeCmdRepo;
    private final EventHandlerRepo<MarketData> eventHandlerRepo;


    // 线程引用，用于资源清理
    private Thread eventThread;
    private Thread strategyThread;

    // 默认构造函数，用于 Spring 自动装配
    public AvellanedaStoikovStrategy() {
        this.params = AvellanedaStoikovParams.defaultParams();
        this.state = AvellanedaStoikovState.initialState();
        this.marketDataRepo = null;
        this.tradeCmdRepo = null;
        this.eventHandlerRepo = null;
    }

    public AvellanedaStoikovStrategy(EventRepo<MarketData> marketDataRepo, EventRepo<TradeCmd> tradeCmdRepo, EventHandlerRepo<MarketData> eventHandlerRepo) {
        this(marketDataRepo, tradeCmdRepo, eventHandlerRepo, AvellanedaStoikovParams.defaultParams());
    }

    public AvellanedaStoikovStrategy(EventRepo<MarketData> marketDataRepo, EventRepo<TradeCmd> tradeCmdRepo, EventHandlerRepo<MarketData> eventHandlerRepo, AvellanedaStoikovParams params) {
        this.marketDataRepo = marketDataRepo;
        this.tradeCmdRepo = tradeCmdRepo;
        this.eventHandlerRepo = eventHandlerRepo;
        this.params = params;
        this.state = AvellanedaStoikovState.initialState();

        // 注册事件处理器
        registerEventHandlers();
    }

    /**
     * 注册事件处理器
     */
    private void registerEventHandlers() {
        if (eventHandlerRepo != null) {
            eventHandlerRepo.addHandler("BINANCE_TRADE_TICK", new TradeTickEventHandler());
            eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DELTA", new OrderBookDeltaEventHandler());
        }
    }


    /**
     * 启动策略
     */
    @Override
    public void start() {



//         /Users/hongyaotang/src/JNautilusTrader/src/main/java/com/tanggo/fund/jnautilustrader/adapter/event_repo/ParquetFileQueueEventRepo.java todo 通过ParquetFile读写事件


        state.start();
        state.inventory = params.initialInventory;


        System.out.println("Avellaneda-Stoikov 策略启动成功");
        System.out.println("策略参数: " + params);

        // 启动事件处理线程
        eventThread = new Thread(() -> {
            while (state.isRunning) {
                try {
                    Event<MarketData> event = marketDataRepo.receive();
                    if (event != null) {
                        EventHandler<MarketData> handler = eventHandlerRepo.queryBy(event.type);
                        if (handler != null) {
                            handler.handle(event);
                        } else {
                            System.out.println("未找到事件处理器: " + event.type);
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    System.out.println("事件处理线程被中断");
                    state.isRunning = false;
                } catch (Exception e) {
                    System.out.println("事件处理失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        eventThread.start();

        // 启动策略执行线程
        strategyThread = new Thread(() -> {
            while (state.isRunning && state.currentTime < params.runTime) {
                try {
                    // 更新当前时间
                    state.currentTime = (System.currentTimeMillis() - state.startTime) / 1000.0;

                    // 执行策略逻辑
                    executeStrategy();

                    // 等待下一次执行
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    System.out.println("策略执行线程被中断");
                    state.isRunning = false;
                } catch (Exception e) {
                    System.out.println("策略执行失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            state.isRunning = false;
            System.out.println("策略执行完成");
        });

        strategyThread.start();
    }

    /**
     * 停止策略
     */
    @Override
    public void stop() {
        System.out.println("正在停止策略...");
        state.isRunning = false;

        // 中断并等待线程结束
        if (eventThread != null && eventThread.isAlive()) {
            eventThread.interrupt();
            try {
                eventThread.join(5000); // 最多等待5秒
            } catch (InterruptedException e) {
                System.out.println("等待事件处理线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        if (strategyThread != null && strategyThread.isAlive()) {
            strategyThread.interrupt();
            try {
                strategyThread.join(5000); // 最多等待5秒
            } catch (InterruptedException e) {
                System.out.println("等待策略执行线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        // 打印最终统计信息
        System.out.println("=== 策略执行结果 ===");
        System.out.println("运行时间: " + state.currentTime + " 秒");
        System.out.println("总交易次数: " + state.tradeCount);
        System.out.println("最终库存: " + state.inventory);
        System.out.println("总利润: " + state.totalProfit);
        System.out.println("最后中间价: " + state.midPrice);
        System.out.println("策略已停止");
    }

    /**
     * 策略执行逻辑
     */
    private void executeStrategy() {
        // 检查是否有有效的中间价
        if (state.midPrice <= 0) {
            System.out.println("等待有效市场数据...");
            return;
        }

        // 计算当前最优买卖价格
        double[] prices = calculateOptimalPrices();
        double optimalBid = prices[0];
        double optimalAsk = prices[1];

        // 更新策略状态
        state.bestBid = optimalBid;
        state.bestAsk = optimalAsk;

        // 发送买卖订单（添加简单的风险控制）
        if (shouldPlaceBuyOrder(optimalBid)) {
            sendBuyOrder(optimalBid);
        }

        if (shouldPlaceSellOrder(optimalAsk)) {
            sendSellOrder(optimalAsk);
        }

        // 打印策略状态
        System.out.println("策略状态: " + state);
    }

    /**
     * 检查是否应该放置买入订单的简单风险控制
     */
    private boolean shouldPlaceBuyOrder(double price) {
        // 简单的库存控制：如果库存过低可以买入
        return state.inventory < params.initialInventory + 0.1; // 允许一定的库存波动
    }

    /**
     * 检查是否应该放置卖出订单的简单风险控制
     */
    private boolean shouldPlaceSellOrder(double price) {
        // 简单的库存控制：如果库存过高可以卖出
        return state.inventory > params.initialInventory - 0.1; // 允许一定的库存波动
    }

    /**
     * 计算最优买卖价格
     * 基于 Avellaneda-Stoikov 做市模型
     */
    private double[] calculateOptimalPrices() {
        // 计算剩余时间
        double timeRemaining = params.runTime - state.currentTime;
        if (timeRemaining <= 0) {
            timeRemaining = 1.0; // 防止除以零
        }

        // 计算预留价格 (reservation price)
        // r = s - q * γ * σ^2 * (T - t)
        double reservationPrice = state.midPrice - state.inventory * params.gamma * params.volatility * params.volatility * timeRemaining;

        // 计算最优价差 (optimal spread)
        // δ = γ * σ^2 * (T - t) + (2/γ) * ln(1 + γ/k)
        // 简化版本：使用固定价差加上库存调整
        double spreadAdjustment = params.gamma * params.volatility * params.volatility * timeRemaining;

        // 计算最优买价和卖价
        // bid = r - δ/2 - gridSpacing
        // ask = r + δ/2 + gridSpacing
        double optimalBid = reservationPrice - spreadAdjustment / 2 - params.gridSpacing;
        double optimalAsk = reservationPrice + spreadAdjustment / 2 + params.gridSpacing;

        // 确保价格合理（不为负值且买价低于卖价）
        optimalBid = Math.max(optimalBid, state.midPrice * 0.95); // 最多偏离中间价5%
        optimalAsk = Math.max(optimalAsk, optimalBid + params.gridSpacing * 2);

        return new double[]{optimalBid, optimalAsk};
    }

    /**
     * 发送买入订单
     */
    private void sendBuyOrder(double price) {
        PlaceOrder order = PlaceOrder.createLimitBuyOrder(params.symbol, params.orderQuantity, price);

        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        // 创建并发送交易命令事件
        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER";
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            System.out.println("发送买入订单成功: 价格=" + price + ", 数量=" + params.orderQuantity);
        } else {
            System.out.println("发送买入订单失败: 价格=" + price + ", 数量=" + params.orderQuantity);
        }
    }

    /**
     * 发送卖出订单
     */
    private void sendSellOrder(double price) {
        PlaceOrder order = PlaceOrder.createLimitSellOrder(params.symbol, params.orderQuantity, price);

        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        // 创建并发送交易命令事件
        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER";
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            System.out.println("发送卖出订单成功: 价格=" + price + ", 数量=" + params.orderQuantity);
        } else {
            System.out.println("发送卖出订单失败: 价格=" + price + ", 数量=" + params.orderQuantity);
        }
    }

    /**
     * 处理交易Tick事件
     */
    private class TradeTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof TradeTick tradeTick) {
                // 更新中间价
                state.midPrice = tradeTick.price;
                // 更新最后交易价格
                state.lastTradePrice = tradeTick.price;
                // 更新库存
                if (tradeTick.isBuyerMaker) {
                    // 卖方主动（sell）
                    state.inventory -= params.orderQuantity;
                } else {
                    // 买方主动（buy）
                    state.inventory += params.orderQuantity;
                }
                // 增加交易计数
                state.tradeCount++;
                // 计算利润
                double profit = calculateProfit(tradeTick.price);
                state.totalProfit += profit;
            }
        }

        private double calculateProfit(double price) {
            if (state.tradeCount == 0) {
                return 0;
            }
            // 基于库存的利润计算
            // 当买入时，价格降低会增加潜在利润；卖出时，价格升高会增加利润
            double profit = 0;
            if (state.tradeCount % 2 == 0) {
                // 偶数次交易（可能是平仓）
                profit = params.orderQuantity * (price - state.lastTradePrice);
            } else {
                // 奇数次交易（可能是建仓）
                profit = -params.orderQuantity * (price - state.lastTradePrice);
            }
            return profit;
        }
    }

    /**
     * 处理订单簿增量更新事件
     */
    private class OrderBookDeltaEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDelta orderBookDelta) {
                // 更新中间价
                if (orderBookDelta.getBidPrice() > 0 && orderBookDelta.getAskPrice() > 0) {
                    state.midPrice = (orderBookDelta.getBidPrice() + orderBookDelta.getAskPrice()) / 2;
                }
            }
        }
    }
}
