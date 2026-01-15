package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandler;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.TradeCmd;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import com.tanggo.fund.jnautilustrader.core.entity.data.PlaceOrder;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.adapter.BlockingQueueEventRepo;

import java.util.concurrent.TimeUnit;
import java.time.Instant;

/**
 * Avellaneda-Stoikov 做市策略实现类
 * 基于币安 WebSocket 实现
 */
public class AvellanedaStoikovStrategy {

    // 策略参数
    private final AvellanedaStoikovParams params;
    // 策略状态
    private final AvellanedaStoikovState state;

    // 事件仓库
    private final EventRepo<MarketData> marketDataRepo;
    private final EventRepo<TradeCmd> tradeCmdRepo;
    private final EventHandlerRepo<MarketData> eventHandlerRepo;

    // 时间戳
    private long startTime;

    public AvellanedaStoikovStrategy(EventRepo<MarketData> marketDataRepo,
                                     EventRepo<TradeCmd> tradeCmdRepo,
                                     EventHandlerRepo<MarketData> eventHandlerRepo) {
        this(marketDataRepo, tradeCmdRepo, eventHandlerRepo, AvellanedaStoikovParams.defaultParams());
    }

    public AvellanedaStoikovStrategy(EventRepo<MarketData> marketDataRepo,
                                     EventRepo<TradeCmd> tradeCmdRepo,
                                     EventHandlerRepo<MarketData> eventHandlerRepo,
                                     AvellanedaStoikovParams params) {
        this.marketDataRepo = marketDataRepo;
        this.tradeCmdRepo = tradeCmdRepo;
        this.eventHandlerRepo = eventHandlerRepo;
        this.params = params;
        this.state = AvellanedaStoikovState.initialState();

        // 初始化事件处理器
        initializeEventHandlers();
    }

    /**
     * 初始化事件处理器
     */
    private void initializeEventHandlers() {
        // 交易Tick事件处理器
        eventHandlerRepo.queryBy("BINANCE_TRADE_TICK");

        // 订单簿增量更新事件处理器
        eventHandlerRepo.queryBy("BINANCE_ORDER_BOOK_DELTA");
    }

    /**
     * 启动策略
     */
    public void start() {
        state.isRunning = true;
        startTime = System.currentTimeMillis();
        state.currentTime = 0;
        state.inventory = params.initialInventory;

        System.out.println("Avellaneda-Stoikov 策略启动成功");
        System.out.println("策略参数: " + params);

        // 启动事件处理线程
        Thread eventThread = new Thread(() -> {
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
        Thread strategyThread = new Thread(() -> {
            while (state.isRunning && state.currentTime < params.runTime) {
                try {
                    // 更新当前时间
                    state.currentTime = (System.currentTimeMillis() - startTime) / 1000.0;

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
    public void stop() {
        state.isRunning = false;
        System.out.println("策略已停止");
    }

    /**
     * 策略执行逻辑
     */
    private void executeStrategy() {
        // 计算当前最优买卖价格
        double[] prices = calculateOptimalPrices();
        double optimalBid = prices[0];
        double optimalAsk = prices[1];

        // 更新策略状态
        state.bestBid = optimalBid;
        state.bestAsk = optimalAsk;

        // 发送买卖订单
        sendBuyOrder(optimalBid);
        sendSellOrder(optimalAsk);

        // 打印策略状态
        System.out.println("策略状态: " + state);
    }

    /**
     * 计算最优买卖价格
     */
    private double[] calculateOptimalPrices() {
        // 计算库存风险调整项
        double inventoryAdjustment = params.gamma * params.volatility * params.volatility *
                (params.runTime - state.currentTime) * state.inventory;

        // 计算最优买价和卖价
        double optimalBid = state.midPrice - inventoryAdjustment - params.gridSpacing;
        double optimalAsk = state.midPrice + inventoryAdjustment + params.gridSpacing;

        return new double[]{optimalBid, optimalAsk};
    }

    /**
     * 发送买入订单
     */
    private void sendBuyOrder(double price) {
        PlaceOrder order = PlaceOrder.createLimitBuyOrder(
                params.symbol,
                params.orderQuantity,
                price
        );

        TradeCmd tradeCmd = TradeCmd.createWithData(order);
        tradeCmdRepo.receive(); // 这应该是发送命令，需要根据实际API修改
    }

    /**
     * 发送卖出订单
     */
    private void sendSellOrder(double price) {
        PlaceOrder order = PlaceOrder.createLimitSellOrder(
                params.symbol,
                params.orderQuantity,
                price
        );

        TradeCmd tradeCmd = TradeCmd.createWithData(order);
        tradeCmdRepo.receive(); // 这应该是发送命令，需要根据实际API修改
    }

    /**
     * 处理交易Tick事件
     */
    private class TradeTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof TradeTick) {
                TradeTick tradeTick = (TradeTick) marketData.getMessage();
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
            // 简单的利润计算
            return params.orderQuantity * (price - state.lastTradePrice);
        }
    }

    /**
     * 处理订单簿增量更新事件
     */
    private class OrderBookDeltaEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDelta) {
                OrderBookDelta orderBookDelta = (OrderBookDelta) marketData.getMessage();
                // 更新中间价
                if (orderBookDelta.getBidPrice() > 0 && orderBookDelta.getAskPrice() > 0) {
                    state.midPrice = (orderBookDelta.getBidPrice() + orderBookDelta.getAskPrice()) / 2;
                }
            }
        }
    }
}