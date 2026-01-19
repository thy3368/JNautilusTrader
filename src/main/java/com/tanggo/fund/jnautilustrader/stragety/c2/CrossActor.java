package com.tanggo.fund.jnautilustrader.stragety.c2;

import com.tanggo.fund.jnautilustrader.core.actor.MessageActor.ActorStatus;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.ErrorHandler;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.MessageHandler;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.State;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.StartHandler;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.StopHandler;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.TradeCmd;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDeltas;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDepth10;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import com.tanggo.fund.jnautilustrader.core.entity.trade.PlaceOrder;
import com.tanggo.fund.jnautilustrader.stragety.cross.CrossArbitrageParams;
import com.tanggo.fund.jnautilustrader.stragety.cross.CrossArbitrageState;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨币安和Bitget现货BTC交易套利策略 - StrategyActor实现
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
@Slf4j
public class CrossActor {

    // 策略参数
    private final CrossArbitrageParams params;

    // StrategyActor实例
    private final StrategyActor<Event<MarketData>, CrossArbitrageState> actor;

    // 事件仓库
    private EventRepo<MarketData> marketDataRepo;
    private EventRepo<TradeCmd> tradeCmdRepo;

    public CrossActor() {
        this(CrossArbitrageParams.defaultParams());
    }

    public CrossActor(CrossArbitrageParams params) {
        this.params = params;
        this.actor = createStrategyActor();
    }

    /**
     * 创建StrategyActor实例
     */
    private StrategyActor<Event<MarketData>, CrossArbitrageState> createStrategyActor() {
        // 初始状态
        CrossArbitrageState initialState = new CrossArbitrageState(params);

        // 消息处理策略
        MessageHandler<Event<MarketData>, CrossArbitrageState> messageHandler = (message, state) -> {
            log.debug("处理消息: {}, 当前状态: {}", message != null ? message.type : "null", state.getState());

            if (message == null) {
                log.warn("收到空消息，跳过处理");
                return;
            }

            // 根据事件类型处理
            if (message instanceof Event && message.payload != null) {
                handleMarketDataEvent(message, state);
            }
        };

        // 错误处理策略
        ErrorHandler errorHandler = e -> {
            log.error("策略Actor处理消息时出错: {}", e.getMessage(), e);
        };

        // 启动回调
        StartHandler<CrossArbitrageState> startHandler = (state) -> {
            log.debug("StrategyActor 启动完成");
            state.setState(state.getState());
            state.getState().start();
        };

        // 停止回调
        StopHandler<CrossArbitrageState> stopHandler = (state) -> {
            log.debug("StrategyActor 停止完成");
            state.getState().stop();
            printStrategySummary(state.getState());
        };

        return new StrategyActor<>(messageHandler, initialState, errorHandler, startHandler, stopHandler);
    }

    /**
     * 处理市场数据事件
     */
    private void handleMarketDataEvent(Event<MarketData> event, State<CrossArbitrageState> state) {
        CrossArbitrageState currentState = state.getState();
        MarketData marketData = event.payload;

        try {
            // 根据事件类型更新状态
            switch (event.type) {
                case "BINANCE_TRADE_TICK":
                    handleBinanceTradeTick(marketData, currentState);
                    break;
                case "BINANCE_ORDER_BOOK_DEPTH":
                    handleBinanceOrderBookDepth(marketData, currentState);
                    break;
                case "BINANCE_ORDER_BOOK_DELTA":
                    handleBinanceOrderBookDelta(marketData, currentState);
                    break;
                case "BINANCE_QUOTE_TICK":
                    handleBinanceQuoteTick(marketData, currentState);
                    break;
                case "BITGET_TRADE_TICK":
                    handleBitgetTradeTick(marketData, currentState);
                    break;
                case "BITGET_ORDER_BOOK_DEPTH":
                    handleBitgetOrderBookDepth(marketData, currentState);
                    break;
                case "BITGET_ORDER_BOOK_DELTA":
                    handleBitgetOrderBookDelta(marketData, currentState);
                    break;
                default:
                    log.debug("未知事件类型: {}", event.type);
                    break;
            }

            // 更新状态
            state.setState(currentState);

            // 检查是否需要执行策略
            checkAndExecuteStrategy(state);

        } catch (Exception e) {
            log.error("处理市场数据事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理币安交易Tick事件
     */
    private void handleBinanceTradeTick(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof TradeTick tradeTick) {
            state.setBinanceMidPrice(tradeTick.price);
            if (params.isDebugMode()) {
                log.debug("币安最新成交价: {}", String.format("%.2f", tradeTick.price));
            }
        }
    }

    /**
     * 处理币安订单簿深度事件
     */
    private void handleBinanceOrderBookDepth(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
            extractBestPrices(orderBook, state::setBinanceBidPrice, state::setBinanceAskPrice);
            updateMidPrice(state, state.getBinanceBidPrice(), state.getBinanceAskPrice(), state::setBinanceMidPrice);
        }
    }

    /**
     * 处理币安订单簿增量更新事件
     */
    private void handleBinanceOrderBookDelta(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof OrderBookDeltas deltas) {
            extractBestPricesFromDeltas(deltas, state::setBinanceBidPrice, state::setBinanceAskPrice);
            updateMidPrice(state, state.getBinanceBidPrice(), state.getBinanceAskPrice(), state::setBinanceMidPrice);
        } else if (marketData.getMessage() instanceof OrderBookDelta delta) {
            if (delta.getBidPrice() > 0) {
                state.setBinanceBidPrice(delta.getBidPrice());
            }
            if (delta.getAskPrice() > 0) {
                state.setBinanceAskPrice(delta.getAskPrice());
            }
            updateMidPrice(state, state.getBinanceBidPrice(), state.getBinanceAskPrice(), state::setBinanceMidPrice);
        }
    }

    /**
     * 处理币安QuoteTick事件
     */
    private void handleBinanceQuoteTick(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof com.tanggo.fund.jnautilustrader.core.entity.data.QuoteTick quoteTick) {
            if (quoteTick.getBidPrice() > 0) {
                state.setBinanceBidPrice(quoteTick.getBidPrice());
            }
            if (quoteTick.getAskPrice() > 0) {
                state.setBinanceAskPrice(quoteTick.getAskPrice());
            }
            updateMidPrice(state, state.getBinanceBidPrice(), state.getBinanceAskPrice(), state::setBinanceMidPrice);
        }
    }

    /**
     * 处理Bitget交易Tick事件
     */
    private void handleBitgetTradeTick(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof TradeTick tradeTick) {
            state.setBitgetMidPrice(tradeTick.price);
            if (params.isDebugMode()) {
                log.debug("Bitget最新成交价: {}", String.format("%.2f", tradeTick.price));
            }
        }
    }

    /**
     * 处理Bitget订单簿深度事件
     */
    private void handleBitgetOrderBookDepth(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
            extractBestPrices(orderBook, state::setBitgetBidPrice, state::setBitgetAskPrice);
            updateMidPrice(state, state.getBitgetBidPrice(), state.getBitgetAskPrice(), state::setBitgetMidPrice);
        }
    }

    /**
     * 处理Bitget订单簿增量更新事件
     */
    private void handleBitgetOrderBookDelta(MarketData marketData, CrossArbitrageState state) {
        if (marketData.getMessage() instanceof OrderBookDeltas deltas) {
            extractBestPricesFromDeltas(deltas, state::setBitgetBidPrice, state::setBitgetAskPrice);
            updateMidPrice(state, state.getBitgetBidPrice(), state.getBitgetAskPrice(), state::setBitgetMidPrice);
        } else if (marketData.getMessage() instanceof OrderBookDelta delta) {
            if (delta.getBidPrice() > 0) {
                state.setBitgetBidPrice(delta.getBidPrice());
            }
            if (delta.getAskPrice() > 0) {
                state.setBitgetAskPrice(delta.getAskPrice());
            }
            updateMidPrice(state, state.getBitgetBidPrice(), state.getBitgetAskPrice(), state::setBitgetMidPrice);
        }
    }

    /**
     * 从订单簿中提取最佳买卖价
     */
    private void extractBestPrices(OrderBookDepth10 orderBook, PriceSetter bidSetter, PriceSetter askSetter) {
        if (orderBook.getBids() != null && !orderBook.getBids().isEmpty()) {
            try {
                // 使用PriceLevel对象获取价格
                if (orderBook.getBids().get(0) != null) {
                    String bidPriceStr = orderBook.getBids().get(0).getPrice();
                    if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                        double bidPrice = Double.parseDouble(bidPriceStr);
                        bidSetter.setPrice(bidPrice);
                    }
                }
            } catch (Exception e) {
                log.warn("解析订单簿买价失败: {}", e.getMessage());
            }
        }

        if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
            try {
                // 使用PriceLevel对象获取价格
                if (orderBook.getAsks().get(0) != null) {
                    String askPriceStr = orderBook.getAsks().get(0).getPrice();
                    if (askPriceStr != null && !askPriceStr.isEmpty()) {
                        double askPrice = Double.parseDouble(askPriceStr);
                        askSetter.setPrice(askPrice);
                    }
                }
            } catch (Exception e) {
                log.warn("解析订单簿卖价失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 从增量更新中提取最佳买卖价
     */
    private void extractBestPricesFromDeltas(OrderBookDeltas deltas, PriceSetter bidSetter, PriceSetter askSetter) {
        if (deltas.getBids() != null && !deltas.getBids().isEmpty()) {
            try {
                // 使用PriceLevel对象获取价格
                if (deltas.getBids().get(0) != null) {
                    String bidPriceStr = deltas.getBids().get(0).getPrice();
                    if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                        double bidPrice = Double.parseDouble(bidPriceStr);
                        bidSetter.setPrice(bidPrice);
                    }
                }
            } catch (Exception e) {
                log.warn("解析增量更新买价失败: {}", e.getMessage());
            }
        }

        if (deltas.getAsks() != null && !deltas.getAsks().isEmpty()) {
            try {
                // 使用PriceLevel对象获取价格
                if (deltas.getAsks().get(0) != null) {
                    String askPriceStr = deltas.getAsks().get(0).getPrice();
                    if (askPriceStr != null && !askPriceStr.isEmpty()) {
                        double askPrice = Double.parseDouble(askPriceStr);
                        askSetter.setPrice(askPrice);
                    }
                }
            } catch (Exception e) {
                log.warn("解析增量更新卖价失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 更新中间价
     */
    private void updateMidPrice(CrossArbitrageState state, double bidPrice, double askPrice, PriceSetter midPriceSetter) {
        if (bidPrice > 0 && askPrice > 0) {
            double midPrice = (bidPrice + askPrice) / 2;
            midPriceSetter.setPrice(midPrice);
        }
    }

    /**
     * 检查并执行策略
     */
    private void checkAndExecuteStrategy(State<CrossArbitrageState> state) {
        CrossArbitrageState currentState = state.getState();

        // 检查策略是否正在运行
        if (!currentState.isRunning()) {
            return;
        }

        // 检查是否有有效市场数据和是否可以进行套利
        if (!currentState.canArbitrage(1000)) { // 1秒内只能套利一次
            if (params.isDebugMode() && currentState.hasValidMarketData()) {
                double spreadPercentage = currentState.calculateSpreadPercentage();
                log.debug("价差: {}%, 未达到套利阈值: {}%", String.format("%.4f", spreadPercentage), params.getArbitrageThreshold());
            }
            return;
        }

        // 检查执行频率
        long currentTimeNanos = System.nanoTime();
        long intervalNanos = params.getCheckInterval() * 1_000_000L; // 转换为纳秒
        if (currentTimeNanos - currentState.getLastStrategyExecutionTime() < intervalNanos) {
            return;
        }

        // 更新状态和执行策略
        currentState.updateState();
        executeStrategy(currentState, state);
        currentState.setLastStrategyExecutionTime(currentTimeNanos);
    }

    /**
     * 执行套利策略
     */
    private void executeStrategy(CrossArbitrageState currentState, State<CrossArbitrageState> state) {
        log.debug("执行策略检查 - 开始");
        log.debug("市场数据状态: Binance买价={}, Binance卖价={}, Binance中间价={}, Bitget买价={}, Bitget卖价={}, Bitget中间价={}", currentState.getBinanceBidPrice(), currentState.getBinanceAskPrice(), currentState.getBinanceMidPrice(), currentState.getBitgetBidPrice(), currentState.getBitgetAskPrice(), currentState.getBitgetMidPrice());

        // 计算价格差异
        double spreadPercentage = currentState.calculateSpreadPercentage();

        // 检查是否满足套利条件
        if (!params.shouldArbitrage(currentState.getBinanceMidPrice(), currentState.getBitgetMidPrice())) {
            if (params.isDebugMode()) {
                log.debug("价差: {}%, 未达到套利阈值: {}%", String.format("%.4f", spreadPercentage), params.getArbitrageThreshold());
            }
            return;
        }

        // 确定套利方向
        if (currentState.getBinanceMidPrice() < currentState.getBitgetMidPrice()) {
            // 币安价格低，Bitget价格高 → 在币安买入，Bitget卖出
            executeArbitrage("BINANCE", "BITGET", currentState.getBinanceAskPrice(), currentState.getBitgetBidPrice(), currentState, state);
        } else {
            // Bitget价格低，币安价格高 → 在Bitget买入，币安卖出
            executeArbitrage("BITGET", "BINANCE", currentState.getBitgetAskPrice(), currentState.getBinanceBidPrice(), currentState, state);
        }
    }

    /**
     * 执行套利操作
     */
    private void executeArbitrage(String buyExchange, String sellExchange, double buyPrice, double sellPrice, CrossArbitrageState currentState, State<CrossArbitrageState> state) {
        log.info("发现套利机会: {}买入价={}, {}卖出价={}", buyExchange, String.format("%.2f", buyPrice), sellExchange, String.format("%.2f", sellPrice));

        // 计算套利成本和收益
        double buyCost = params.calculateTotalCost(buyPrice, params.getOrderQuantity(), buyExchange);
        double sellRevenue = params.calculateTotalRevenue(sellPrice, params.getOrderQuantity(), sellExchange);
        double profit = sellRevenue - buyCost;

        // 检查最小利润条件
        if (profit < params.getMinProfit()) {
            log.info("套利利润未达到最小利润要求: {} USDT < {} USDT", String.format("%.6f", profit), params.getMinProfit());
            currentState.recordArbitrage(false, 0, 0);
            state.setState(currentState);
            return;
        }

        // 风险管理检查
        if (!checkRiskManagement(currentState)) {
            log.warn("风险管理检查失败，取消套利操作");
            currentState.recordArbitrage(false, 0, 0);
            state.setState(currentState);
            return;
        }

        // 发送套利订单
        boolean buySuccess = sendBuyOrder(buyExchange, buyPrice);
        boolean sellSuccess = sendSellOrder(sellExchange, sellPrice);

        if (buySuccess && sellSuccess) {
            log.info("套利成功! 利润: {} USDT", String.format("%.6f", profit));
            currentState.recordArbitrage(true, profit, 0); // 跨交易所套利通常是对冲交易，净持仓为0
        } else {
            log.error("套利失败! 买入或卖出订单发送失败");
            currentState.recordArbitrage(false, 0, 0);

            // 如果只有一个订单成功，需要尝试撤销另一个
            if (buySuccess) {
                cancelOrders(buyExchange);
            }
            if (sellSuccess) {
                cancelOrders(sellExchange);
            }
        }

        state.setState(currentState);
    }

    /**
     * 风险管理检查
     */
    private boolean checkRiskManagement(CrossArbitrageState state) {
        // 检查持仓限制
        if (Math.abs(state.getCurrentPosition()) >= params.getMaxPositionLimit()) {
            log.warn("持仓超过限制: {} BTC >= {} BTC", String.format("%.6f", state.getCurrentPosition()), params.getMaxPositionLimit());
            return false;
        }

        // 检查价格合理性
        if (state.getBinanceMidPrice() == 0 || state.getBitgetMidPrice() == 0) {
            log.warn("无效的市场价格");
            return false;
        }

        return true;
    }

    /**
     * 发送买入订单
     */
    private boolean sendBuyOrder(String exchange, double price) {
        if (tradeCmdRepo == null) {
            log.error("交易指令仓库未初始化");
            return false;
        }

        PlaceOrder order = PlaceOrder.createLimitBuyOrder(params.getSymbol(), params.getOrderQuantity(), price);
        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER_" + exchange;
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            log.info("{}发送买入订单成功: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        } else {
            log.error("{}发送买入订单失败: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        }
        return sent;
    }

    /**
     * 发送卖出订单
     */
    private boolean sendSellOrder(String exchange, double price) {
        if (tradeCmdRepo == null) {
            log.error("交易指令仓库未初始化");
            return false;
        }

        PlaceOrder order = PlaceOrder.createLimitSellOrder(params.getSymbol(), params.getOrderQuantity(), price);
        TradeCmd tradeCmd = TradeCmd.createWithData(order);

        Event<TradeCmd> event = new Event<>();
        event.type = "PLACE_ORDER_" + exchange;
        event.payload = tradeCmd;

        boolean sent = tradeCmdRepo.send(event);
        if (sent) {
            log.info("{}发送卖出订单成功: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        } else {
            log.error("{}发送卖出订单失败: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        }
        return sent;
    }

    /**
     * 取消订单
     */
    private void cancelOrders(String exchange) {
        log.warn("取消{}的未成交订单", exchange);
        // TODO: 实现取消订单逻辑
    }

    /**
     * 启动策略
     */
    public void start() {
        log.info("启动跨币安和Bitget现货BTC套利策略（StrategyActor模式）");
        log.info("策略参数: {}", params);

        // 启动Actor
        actor.start();
    }

    /**
     * 停止策略
     */
    public void stop() {
        log.info("正在停止策略...");
        actor.close();
    }

    /**
     * 打印策略执行结果摘要
     */
    private void printStrategySummary(CrossArbitrageState state) {
        log.info("=== 跨交易所套利策略执行结果 ===");
        log.info("运行时间: {} 秒", String.format("%.2f", state.getCurrentTime()));
        log.info("总套利次数: {}", state.getArbitrageCount());
        log.info("成功次数: {}", state.getSuccessfulArbitrageCount());
        log.info("失败次数: {}", state.getFailedArbitrageCount());

        if (state.getArbitrageCount() > 0) {
            log.info("成功率: {}%", String.format("%.2f", (double) state.getSuccessfulArbitrageCount() / state.getArbitrageCount() * 100));
        }

        log.info("总利润: {} USDT", String.format("%.4f", state.getTotalProfit()));

        if (state.getSuccessfulArbitrageCount() > 0) {
            log.info("平均每次利润: {} USDT", String.format("%.6f", state.getTotalProfit() / state.getSuccessfulArbitrageCount()));
        }

        log.info("总交易量: {} BTC", String.format("%.4f", state.getTotalVolume()));
        log.info("最终持仓: {} BTC", String.format("%.6f", state.getCurrentPosition()));
        log.info("最大持仓: {} BTC", String.format("%.6f", state.getMaxPosition()));
        log.info("最小持仓: {} BTC", String.format("%.6f", state.getMinPosition()));
        log.info("平均持仓: {} BTC", String.format("%.6f", state.getAvgPosition()));
        log.info("最大价差: {}%", String.format("%.4f", state.getMaxSpreadPercentage()));
        log.info("平均价差: {}%", String.format("%.4f", state.getAvgSpreadPercentage()));
        log.info("策略已停止");
    }

    /**
     * 设置事件仓库
     */
    public void setMarketDataRepo(EventRepo<MarketData> marketDataRepo) {
        this.marketDataRepo = marketDataRepo;
    }

    public void setTradeCmdRepo(EventRepo<TradeCmd> tradeCmdRepo) {
        this.tradeCmdRepo = tradeCmdRepo;
    }

    /**
     * 获取当前策略状态
     */
    public CrossArbitrageState getState() {
        return actor.getState();
    }

    /**
     * 获取Actor状态
     */
    public ActorStatus getActorStatus() {
        return actor.getStatus();
    }

    /**
     * 价格设置器函数式接口
     */
    private interface PriceSetter {
        void setPrice(double price);
    }
}
