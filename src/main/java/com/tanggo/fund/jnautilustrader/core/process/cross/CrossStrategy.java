package com.tanggo.fund.jnautilustrader.core.process.cross;

import com.tanggo.fund.jnautilustrader.core.entity.*;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDepth10;
import com.tanggo.fund.jnautilustrader.core.entity.data.PlaceOrder;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 跨币安和Bitget现货BTC交易套利策略
 * <p>
 * 该策略通过同时监控两个交易所的实时市场数据，
 * 当发现价格差异超过设定阈值时，执行低买高卖的套利操作。
 * <p>
 * 核心功能：
 * - 实时监控币安和Bitget的BTC/USDT现货价格
 * - 计算价格差异和潜在利润
 * - 当价差超过阈值时执行套利订单
 * - 风险管理和持仓控制
 * - 策略状态监控和统计
 *
 * @author JNautilusTrader
 * @version 1.0
 */
@Component
public class CrossStrategy implements Actor {

    // 策略参数
    private final CrossArbitrageParams params;
    // 策略状态
    private final CrossArbitrageState state;

    // 事件仓库
    private final EventRepo<MarketData> marketDataRepo;
    private final EventRepo<TradeCmd> tradeCmdRepo;
    private final EventHandlerRepo<MarketData> eventHandlerRepo;

    // 线程引用，用于资源清理
    private Thread eventThread;
    private Thread strategyThread;

    // 默认构造函数，用于Spring自动装配
    public CrossStrategy() {
        this.marketDataRepo = null;
        this.tradeCmdRepo = null;
        this.eventHandlerRepo = null;
        this.params = CrossArbitrageParams.defaultParams();
        this.state = CrossArbitrageState.initialState();
    }

    public CrossStrategy(EventRepo<MarketData> marketDataRepo, EventRepo<TradeCmd> tradeCmdRepo,
                         EventHandlerRepo<MarketData> eventHandlerRepo) {
        this(marketDataRepo, tradeCmdRepo, eventHandlerRepo, CrossArbitrageParams.defaultParams());
    }

    public CrossStrategy(EventRepo<MarketData> marketDataRepo, EventRepo<TradeCmd> tradeCmdRepo,
                         EventHandlerRepo<MarketData> eventHandlerRepo, CrossArbitrageParams params) {
        this.marketDataRepo = marketDataRepo;
        this.tradeCmdRepo = tradeCmdRepo;
        this.eventHandlerRepo = eventHandlerRepo;
        this.params = params;
        this.state = CrossArbitrageState.initialState();

        // 注册事件处理器
        registerEventHandlers();
    }

    /**
     * 注册市场数据事件处理器
     */
    private void registerEventHandlers() {
        if (eventHandlerRepo != null) {
            eventHandlerRepo.addHandler("BINANCE_TRADE_TICK", new BinanceTradeTickEventHandler());
            eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DEPTH", new BinanceOrderBookDepthEventHandler());
            eventHandlerRepo.addHandler("BITGET_TRADE_TICK", new BitgetTradeTickEventHandler());
            eventHandlerRepo.addHandler("BITGET_ORDER_BOOK_DEPTH", new BitgetOrderBookDepthEventHandler());
        }
    }

    /**
     * 启动策略
     */
    @Override
    public void start() {
        state.start();

        System.out.println("跨币安和Bitget现货BTC套利策略启动成功");
        System.out.println("策略参数: " + params);

        // 启动事件处理线程
        eventThread = new Thread(() -> {
            while (state.isRunning()) {
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
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("事件处理线程被中断");
                    state.setRunning(false);
                } catch (Exception e) {
                    System.out.println("事件处理失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        eventThread.start();

        // 启动策略执行线程
        strategyThread = new Thread(() -> {
            while (state.isRunning() && state.getCurrentTime() < params.getRunTime()) {
                try {
                    // 更新当前时间
                    state.updateState();

                    // 执行策略逻辑
                    executeStrategy();

                    // 等待下一次执行
                    TimeUnit.MILLISECONDS.sleep(params.getCheckInterval());
                } catch (InterruptedException e) {
                    System.out.println("策略执行线程被中断");
                    state.setRunning(false);
                } catch (Exception e) {
                    System.out.println("策略执行失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            state.stop();
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
        state.stop();

        // 中断并等待线程结束
        if (eventThread != null && eventThread.isAlive()) {
            eventThread.interrupt();
            try {
                eventThread.join(5000);
            } catch (InterruptedException e) {
                System.out.println("等待事件处理线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        if (strategyThread != null && strategyThread.isAlive()) {
            strategyThread.interrupt();
            try {
                strategyThread.join(5000);
            } catch (InterruptedException e) {
                System.out.println("等待策略执行线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        // 打印最终统计信息
        printStrategySummary();
    }

    /**
     * 打印策略执行结果摘要
     */
    private void printStrategySummary() {
        System.out.println("=== 跨交易所套利策略执行结果 ===");
        System.out.println("运行时间: " + String.format("%.2f", state.getCurrentTime()) + " 秒");
        System.out.println("总套利次数: " + state.getArbitrageCount());
        System.out.println("成功次数: " + state.getSuccessfulArbitrageCount());
        System.out.println("失败次数: " + state.getFailedArbitrageCount());
        System.out.println("成功率: " + String.format("%.2f",
                (double) state.getSuccessfulArbitrageCount() / state.getArbitrageCount() * 100) + "%");
        System.out.println("总利润: " + String.format("%.4f", state.getTotalProfit()) + " USDT");
        System.out.println("平均每次利润: " + String.format("%.6f",
                state.getTotalProfit() / state.getSuccessfulArbitrageCount()) + " USDT");
        System.out.println("总交易量: " + String.format("%.4f", state.getTotalVolume()) + " BTC");
        System.out.println("最终持仓: " + String.format("%.6f", state.getCurrentPosition()) + " BTC");
        System.out.println("最大持仓: " + String.format("%.6f", state.getMaxPosition()) + " BTC");
        System.out.println("最小持仓: " + String.format("%.6f", state.getMinPosition()) + " BTC");
        System.out.println("平均持仓: " + String.format("%.6f", state.getAvgPosition()) + " BTC");
        System.out.println("最大价差: " + String.format("%.4f", state.getMaxSpreadPercentage()) + "%");
        System.out.println("平均价差: " + String.format("%.4f", state.getAvgSpreadPercentage()) + "%");
        System.out.println("策略已停止");
    }

    /**
     * 策略执行逻辑
     */
    private void executeStrategy() {
        // 检查是否有有效的市场数据和是否可以进行套利
        if (!state.canArbitrage(1000)) { // 1秒内只能套利一次
            if (params.isDebugMode() && state.hasValidMarketData()) {
                double spreadPercentage = state.calculateSpreadPercentage();
                System.out.println("价差: " + String.format("%.4f", spreadPercentage) + "%, 未达到套利阈值: " + params.getArbitrageThreshold() + "%");
            }
            return;
        }

        // 计算价格差异
        double spreadPercentage = state.calculateSpreadPercentage();

        // 检查是否满足套利条件
        if (!params.shouldArbitrage(state.getBinanceMidPrice(), state.getBitgetMidPrice())) {
            if (params.isDebugMode()) {
                System.out.println("价差: " + String.format("%.4f", spreadPercentage) + "%, 未达到套利阈值: " + params.getArbitrageThreshold() + "%");
            }
            return;
        }

        // 确定套利方向
        if (state.getBinanceMidPrice() < state.getBitgetMidPrice()) {
            // 币安价格低，Bitget价格高 → 在币安买入，Bitget卖出
            executeArbitrage("BINANCE", "BITGET", state.getBinanceAskPrice(), state.getBitgetBidPrice());
        } else {
            // Bitget价格低，币安价格高 → 在Bitget买入，币安卖出
            executeArbitrage("BITGET", "BINANCE", state.getBitgetAskPrice(), state.getBinanceBidPrice());
        }
    }

    /**
     * 执行套利操作
     */
    private void executeArbitrage(String buyExchange, String sellExchange, double buyPrice, double sellPrice) {
        System.out.println("发现套利机会: " + buyExchange + "买入价=" + String.format("%.2f", buyPrice) +
                ", " + sellExchange + "卖出价=" + String.format("%.2f", sellPrice));

        // 计算套利成本和收益
        double buyCost = params.calculateTotalCost(buyPrice, params.getOrderQuantity(), buyExchange);
        double sellRevenue = params.calculateTotalRevenue(sellPrice, params.getOrderQuantity(), sellExchange);
        double profit = sellRevenue - buyCost;

        // 检查最小利润条件
        if (profit < params.getMinProfit()) {
            System.out.println("套利利润未达到最小利润要求: " + String.format("%.6f", profit) + " USDT < " + params.getMinProfit() + " USDT");
            state.recordArbitrage(false, 0, 0);
            return;
        }

        // 风险管理检查
        if (!checkRiskManagement()) {
            System.out.println("风险管理检查失败，取消套利操作");
            state.recordArbitrage(false, 0, 0);
            return;
        }

        // 发送套利订单
        boolean buySuccess = sendBuyOrder(buyExchange, buyPrice);
        boolean sellSuccess = sendSellOrder(sellExchange, sellPrice);

        if (buySuccess && sellSuccess) {
            System.out.println("套利成功! 利润: " + String.format("%.6f", profit) + " USDT");
            state.recordArbitrage(true, profit, 0); // 跨交易所套利通常是对冲交易，净持仓为0
        } else {
            System.out.println("套利失败! 买入或卖出订单发送失败");
            state.recordArbitrage(false, 0, 0);

            // 如果只有一个订单成功，需要尝试撤销另一个
            if (buySuccess) {
                cancelOrders(buyExchange);
            }
            if (sellSuccess) {
                cancelOrders(sellExchange);
            }
        }
    }

    /**
     * 风险管理检查
     */
    private boolean checkRiskManagement() {
        // 检查持仓限制
        if (Math.abs(state.getCurrentPosition()) >= params.getMaxPositionLimit()) {
            System.out.println("持仓超过限制: " + String.format("%.6f", state.getCurrentPosition()) + " BTC >= " + params.getMaxPositionLimit() + " BTC");
            return false;
        }

        // 检查价格合理性
        if (state.getBinanceMidPrice() == 0 || state.getBitgetMidPrice() == 0) {
            System.out.println("无效的市场价格");
            return false;
        }

        return true;
    }

    /**
     * 发送买入订单
     */
    private boolean sendBuyOrder(String exchange, double price) {
        PlaceOrder order = PlaceOrder.createLimitBuyOrder(params.getSymbol(), params.getOrderQuantity(), price);
        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER_" + exchange;
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            System.out.println(exchange + "发送买入订单成功: 价格=" + String.format("%.2f", price) +
                    ", 数量=" + params.getOrderQuantity() + " BTC");
        } else {
            System.out.println(exchange + "发送买入订单失败: 价格=" + String.format("%.2f", price) +
                    ", 数量=" + params.getOrderQuantity() + " BTC");
        }
        return sent;
    }

    /**
     * 发送卖出订单
     */
    private boolean sendSellOrder(String exchange, double price) {
        PlaceOrder order = PlaceOrder.createLimitSellOrder(params.getSymbol(), params.getOrderQuantity(), price);
        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER_" + exchange;
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            System.out.println(exchange + "发送卖出订单成功: 价格=" + String.format("%.2f", price) +
                    ", 数量=" + params.getOrderQuantity() + " BTC");
        } else {
            System.out.println(exchange + "发送卖出订单失败: 价格=" + String.format("%.2f", price) +
                    ", 数量=" + params.getOrderQuantity() + " BTC");
        }
        return sent;
    }

    /**
     * 取消订单
     */
    private void cancelOrders(String exchange) {
        System.out.println("取消" + exchange + "的未成交订单");
        // todo: 实现取消订单逻辑
    }

    /**
     * 处理币安交易Tick事件
     */
    private class BinanceTradeTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof TradeTick tradeTick) {
                state.setBinanceMidPrice(tradeTick.price);
                if (params.isDebugMode()) {
                    System.out.println("币安最新成交价: " + String.format("%.2f", tradeTick.price));
                }
            }
        }
    }

    /**
     * 处理币安订单簿深度事件
     */
    private class BinanceOrderBookDepthEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
                // 提取最佳买卖价
                if (orderBook.getBids() != null && !orderBook.getBids().isEmpty()) {
                    double bidPrice = Double.parseDouble(orderBook.getBids().get(0).get(0));
                    state.setBinanceBidPrice(bidPrice);
                }
                if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
                    double askPrice = Double.parseDouble(orderBook.getAsks().get(0).get(0));
                    state.setBinanceAskPrice(askPrice);
                }
                // 更新中间价
                if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                    state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
                }
            }
        }
    }

    /**
     * 处理Bitget交易Tick事件
     */
    private class BitgetTradeTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof TradeTick tradeTick) {
                state.setBitgetMidPrice(tradeTick.price);
                if (params.isDebugMode()) {
                    System.out.println("Bitget最新成交价: " + String.format("%.2f", tradeTick.price));
                }
            }
        }
    }

    /**
     * 处理Bitget订单簿深度事件
     */
    private class BitgetOrderBookDepthEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
                // 提取最佳买卖价
                if (orderBook.getBids() != null && !orderBook.getBids().isEmpty()) {
                    double bidPrice = Double.parseDouble(orderBook.getBids().get(0).get(0));
                    state.setBitgetBidPrice(bidPrice);
                }
                if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
                    double askPrice = Double.parseDouble(orderBook.getAsks().get(0).get(0));
                    state.setBitgetAskPrice(askPrice);
                }
                // 更新中间价
                if (state.getBitgetBidPrice() > 0 && state.getBitgetAskPrice() > 0) {
                    state.setBitgetMidPrice((state.getBitgetBidPrice() + state.getBitgetAskPrice()) / 2);
                }
            }
        }
    }
}