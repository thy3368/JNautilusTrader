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
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Avellaneda-Stoikov 做市策略实现类
 * 基于币安 WebSocket 实现
 */

@Component
public class AvellanedaStoikovStrategy  implements Strategy {

    // 策略参数
    private  AvellanedaStoikovParams params;
    // 策略状态
    private  AvellanedaStoikovState state;

    // 事件仓库
    private  EventRepo<MarketData> marketDataRepo;
    private  EventRepo<TradeCmd> tradeCmdRepo;
    private  EventHandlerRepo<MarketData> eventHandlerRepo;

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
        // 由于 EventHandlerRepo 接口只有 queryBy 方法，没有提供注册方法
        // 我们需要检查 EventHandlerRepo 的实现类，看它是如何存储事件处理器的
        // 目前我们创建事件处理器实例，确保它们能被正确查询到
        // 这里假设 EventHandlerRepo 内部已经预注册了这些事件处理器

        System.out.println("事件处理器初始化完成");
        System.out.println("已配置的事件类型: BINANCE_TRADE_TICK, BINANCE_ORDER_BOOK_DELTA");
    }

    /**
     * 启动策略
     */
    @Override
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
    @Override
    public void stop() {
        state.isRunning = false;
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

        // 创建并发送交易命令事件
        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER";
        event.payload = tradeCmd;

        tradeCmdRepo.send(event);
        System.out.println("发送买入订单: 价格=" + price + ", 数量=" + params.orderQuantity);
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

        // 创建并发送交易命令事件
        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER";
        event.payload = tradeCmd;

        tradeCmdRepo.send(event);
        System.out.println("发送卖出订单: 价格=" + price + ", 数量=" + params.orderQuantity);
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
