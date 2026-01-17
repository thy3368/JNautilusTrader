package com.tanggo.fund.jnautilustrader.stragety.cross;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.HashMapEventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.*;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDeltas;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDepth10;
import com.tanggo.fund.jnautilustrader.core.entity.data.PlaceOrder;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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

@Data
public class CrossAppService implements Actor {

    private static final Logger logger = LoggerFactory.getLogger(CrossAppService.class);

    // 策略参数
    private CrossArbitrageParams params;
    // 策略状态
    private CrossArbitrageState state;

    // 事件仓库
    private EventRepo<MarketData> marketDataRepo;
    private EventRepo<TradeCmd> tradeCmdRepo;
    private EventHandlerRepo<MarketData> eventHandlerRepo;
    // 单线程执行器，用于事件处理和策略执行
    private ExecutorService singleThreadExecutor;

    // 用于跟踪提交的任务
    private Future<?> mainTaskFuture;

    // 最后一次策略执行时间戳（纳秒）
    private volatile long lastStrategyExecutionTime = 0;


    /**
     * 注册市场数据事件处理器
     * 在start_link()之前必须调用，确保事件处理器已注册
     */
    private void registerEventHandlers() {
        eventHandlerRepo = new HashMapEventHandlerRepo<MarketData>();
        eventHandlerRepo.addHandler("BINANCE_TRADE_TICK", new BinanceTradeTickEventHandler());
        eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DEPTH", new BinanceOrderBookDepthEventHandler());
        eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DELTA", new BinanceOrderBookDeltaEventHandler());
        eventHandlerRepo.addHandler("BINANCE_QUOTE_TICK", new BinanceQuoteTickEventHandler());  // bookTicker数据
        eventHandlerRepo.addHandler("BITGET_TRADE_TICK", new BitgetTradeTickEventHandler());
        eventHandlerRepo.addHandler("BITGET_ORDER_BOOK_DEPTH", new BitgetOrderBookDepthEventHandler());
        eventHandlerRepo.addHandler("BITGET_ORDER_BOOK_DELTA", new BitgetOrderBookDeltaEventHandler());
        logger.info("事件处理器注册完成");

    }

    /**
     * 启动策略 - 单线程事件驱动模式
     * 事件处理和策略执行在同一线程中，事件到达后立即触发策略检查
     */
    @Override
    public void start_link() {
        if (singleThreadExecutor == null) {
            logger.error("单线程执行器未初始化");
            return;
        }

        mainTaskFuture = singleThreadExecutor.submit(() -> {
            registerEventHandlers();
            state.start();
            logger.info("跨币安和Bitget现货BTC套利策略启动成功（单线程事件驱动模式）");
            logger.info("策略参数: {}", params);

            logger.debug("开始策略主循环 - state.isRunning()={}, currentTime={}, runTime={}",
                    state.isRunning(), state.getCurrentTime(), params.getRunTime());

            try {
                int loopCount = 0;
                int eventReceivedCount = 0;
                int eventHandledCount = 0;
                int strategyExecutedCount = 0;

                while (state.isRunning() && state.getCurrentTime() < params.getRunTime()) {
                    try {
                        loopCount++;

                        // 每1000次循环记录一次状态
                        if (loopCount % 1000 == 0) {
                            logger.debug("主循环状态 - 循环次数: {}, 接收事件: {}, 处理事件: {}, 执行策略: {}",
                                    loopCount, eventReceivedCount, eventHandledCount, strategyExecutedCount);
                        }

                        // 1. 接收市场数据事件（非阻塞模式，超时10ms）
                        Event<MarketData> event = marketDataRepo.receive();

                        if (event != null) {
                            eventReceivedCount++;
                            logger.debug("收到事件 #{} - 类型: {}, payload类型: {}",
                                    eventReceivedCount,
                                    event.type,
                                    event.payload != null ? event.payload.getClass().getSimpleName() : "null");

                            // 2. 处理事件并更新状态
                            EventHandler<MarketData> handler = eventHandlerRepo.queryBy(event.type);
                            if (handler != null) {
                                logger.debug("找到事件处理器: {} -> {}", event.type, handler.getClass().getSimpleName());
                                handler.handle(event);
                                eventHandledCount++;
                                logger.debug("事件处理完成 #{}", eventHandledCount);

                                // 3. 事件处理后立即尝试执行策略（事件驱动）
                                // 使用纳秒级精度时间戳控制执行频率
                                long currentTimeNanos = System.nanoTime();
                                long intervalNanos = params.getCheckInterval() * 1_000_000L; // 转换为纳秒
                                long timeSinceLastExecution = currentTimeNanos - lastStrategyExecutionTime;

                                logger.debug("策略执行间隔检查 - 距上次: {}ns, 要求间隔: {}ns, 是否执行: {}",
                                        timeSinceLastExecution, intervalNanos, timeSinceLastExecution >= intervalNanos);

                                if (currentTimeNanos - lastStrategyExecutionTime >= intervalNanos) {
                                    logger.debug("开始执行策略 - 更新状态并执行");
                                    state.updateState();
                                    executeStrategy();
                                    strategyExecutedCount++;
                                    lastStrategyExecutionTime = currentTimeNanos;
                                    logger.debug("策略执行完成 #{}", strategyExecutedCount);
                                } else {
                                    logger.debug("跳过策略执行 - 时间间隔未到");
                                }
                            } else {
                                logger.warn("未找到事件处理器: {} - 可能需要注册该事件类型", event.type);
                            }
                        } else {
                            // 4. 无事件时短暂休眠，避免CPU空转
                            if (loopCount % 10000 == 0) {
                                logger.debug("无事件接收 - 循环 #{}", loopCount);
                            }
                            TimeUnit.MILLISECONDS.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        logger.info("策略主线程被中断");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("事件处理或策略执行失败", e);
                    }
                }

                state.stop();
                logger.info("策略执行完成");
            } finally {
                logger.info("策略主线程已退出");
            }
        });
    }

    /**
     * 停止策略 - 优雅关闭单线程执行器
     */
    @Override
    public void stop() {
        logger.info("正在停止策略...");
        state.stop();

        // 取消主任务
        if (mainTaskFuture != null && !mainTaskFuture.isDone()) {
            logger.debug("取消策略主任务");
            mainTaskFuture.cancel(true);
            try {
                // 等待任务完成，最多等待5秒
                mainTaskFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("等待主任务完成时发生异常", e);
            }
        }

        // 打印最终统计信息
        printStrategySummary();
    }

    /**
     * 打印策略执行结果摘要
     */
    private void printStrategySummary() {
        logger.info("=== 跨交易所套利策略执行结果 ===");
        logger.info("运行时间: {} 秒", String.format("%.2f", state.getCurrentTime()));
        logger.info("总套利次数: {}", state.getArbitrageCount());
        logger.info("成功次数: {}", state.getSuccessfulArbitrageCount());
        logger.info("失败次数: {}", state.getFailedArbitrageCount());

        if (state.getArbitrageCount() > 0) {
            logger.info("成功率: {}%", String.format("%.2f", (double) state.getSuccessfulArbitrageCount() / state.getArbitrageCount() * 100));
        }

        logger.info("总利润: {} USDT", String.format("%.4f", state.getTotalProfit()));

        if (state.getSuccessfulArbitrageCount() > 0) {
            logger.info("平均每次利润: {} USDT", String.format("%.6f", state.getTotalProfit() / state.getSuccessfulArbitrageCount()));
        }

        logger.info("总交易量: {} BTC", String.format("%.4f", state.getTotalVolume()));
        logger.info("最终持仓: {} BTC", String.format("%.6f", state.getCurrentPosition()));
        logger.info("最大持仓: {} BTC", String.format("%.6f", state.getMaxPosition()));
        logger.info("最小持仓: {} BTC", String.format("%.6f", state.getMinPosition()));
        logger.info("平均持仓: {} BTC", String.format("%.6f", state.getAvgPosition()));
        logger.info("最大价差: {}%", String.format("%.4f", state.getMaxSpreadPercentage()));
        logger.info("平均价差: {}%", String.format("%.4f", state.getAvgSpreadPercentage()));
        logger.info("策略已停止");
    }

    /**
     * 策略执行逻辑
     */
    //todo 检查一下 策略为什么没有触发 也没看到日志
    private void executeStrategy() {
        logger.debug("执行策略检查 - 开始");
        logger.debug("市场数据状态: Binance买价={}, Binance卖价={}, Binance中间价={}, Bitget买价={}, Bitget卖价={}, Bitget中间价={}",
                state.getBinanceBidPrice(), state.getBinanceAskPrice(), state.getBinanceMidPrice(),
                state.getBitgetBidPrice(), state.getBitgetAskPrice(), state.getBitgetMidPrice());

        // 检查是否有有效的市场数据和是否可以进行套利
        if (!state.canArbitrage(1000)) { // 1秒内只能套利一次
            logger.debug("套利条件检查失败: canArbitrage=false, hasValidMarketData={}", state.hasValidMarketData());
            if (params.isDebugMode() && state.hasValidMarketData()) {
                double spreadPercentage = state.calculateSpreadPercentage();
                logger.debug("价差: {}%, 未达到套利阈值: {}%", String.format("%.4f", spreadPercentage), params.getArbitrageThreshold());
            }
            return;
        }

        // 计算价格差异
        double spreadPercentage = state.calculateSpreadPercentage();

        // 检查是否满足套利条件
        if (!params.shouldArbitrage(state.getBinanceMidPrice(), state.getBitgetMidPrice())) {
            if (params.isDebugMode()) {
                logger.debug("价差: {}%, 未达到套利阈值: {}%", String.format("%.4f", spreadPercentage), params.getArbitrageThreshold());
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
        logger.info("发现套利机会: {}买入价={}, {}卖出价={}", buyExchange, String.format("%.2f", buyPrice), sellExchange, String.format("%.2f", sellPrice));

        // 计算套利成本和收益
        double buyCost = params.calculateTotalCost(buyPrice, params.getOrderQuantity(), buyExchange);
        double sellRevenue = params.calculateTotalRevenue(sellPrice, params.getOrderQuantity(), sellExchange);
        double profit = sellRevenue - buyCost;

        // 检查最小利润条件
        if (profit < params.getMinProfit()) {
            logger.info("套利利润未达到最小利润要求: {} USDT < {} USDT", String.format("%.6f", profit), params.getMinProfit());
            state.recordArbitrage(false, 0, 0);
            return;
        }

        // 风险管理检查
        if (!checkRiskManagement()) {
            logger.warn("风险管理检查失败，取消套利操作");
            state.recordArbitrage(false, 0, 0);
            return;
        }

        // 发送套利订单
        boolean buySuccess = sendBuyOrder(buyExchange, buyPrice);
        boolean sellSuccess = sendSellOrder(sellExchange, sellPrice);

        if (buySuccess && sellSuccess) {
            logger.info("套利成功! 利润: {} USDT", String.format("%.6f", profit));
            state.recordArbitrage(true, profit, 0); // 跨交易所套利通常是对冲交易，净持仓为0
        } else {
            logger.error("套利失败! 买入或卖出订单发送失败");
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
            logger.warn("持仓超过限制: {} BTC >= {} BTC", String.format("%.6f", state.getCurrentPosition()), params.getMaxPositionLimit());
            return false;
        }

        // 检查价格合理性
        if (state.getBinanceMidPrice() == 0 || state.getBitgetMidPrice() == 0) {
            logger.warn("无效的市场价格");
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
            logger.info("{}发送买入订单成功: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        } else {
            logger.error("{}发送买入订单失败: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
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
            logger.info("{}发送卖出订单成功: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        } else {
            logger.error("{}发送卖出订单失败: 价格={}, 数量={} BTC", exchange, String.format("%.2f", price), params.getOrderQuantity());
        }
        return sent;
    }

    /**
     * 取消订单
     */
    private void cancelOrders(String exchange) {
        logger.warn("取消{}的未成交订单", exchange);
        // TODO: 实现取消订单逻辑
    }

    public void setParams(CrossArbitrageParams params) {
        this.params = params;
        // 初始化策略状态
        if (this.state == null) {
            this.state = new CrossArbitrageState(params);
        } else {
            this.state.setParams(params);
        }
    }

    public void setEventHandlerRepo(EventHandlerRepo<MarketData> eventHandlerRepo) {
        this.eventHandlerRepo = eventHandlerRepo;
        // 注册事件处理器
        registerEventHandlers();
    }

    /**
     * 处理币安交易Tick事件
     */
    //todo 任何一个EventHandler 成功失败 都应该有info日志
    private class BinanceTradeTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            logger.debug("BinanceTradeTickEventHandler.handle() 开始");
            MarketData marketData = event.payload;
            logger.debug("MarketData payload: {}", marketData != null ? marketData.getClass().getSimpleName() : "null");

            if (marketData.getMessage() instanceof TradeTick tradeTick) {
                logger.debug("解析到TradeTick: price={}", tradeTick.price);
                state.setBinanceMidPrice(tradeTick.price);
                logger.debug("币安中间价已更新: {}", tradeTick.price);

                if (params.isDebugMode()) {
                    logger.debug("币安最新成交价: {}", String.format("%.2f", tradeTick.price));
                }
            } else {
                logger.warn("MarketData.getMessage() 不是 TradeTick 类型: {}",
                        marketData.getMessage() != null ? marketData.getMessage().getClass().getName() : "null");
            }
            logger.debug("BinanceTradeTickEventHandler.handle() 完成");
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
                    try {
                        // 检查是否有足够的深度数据且不为空字符串
                        if (orderBook.getBids().get(0) != null && orderBook.getBids().get(0).size() > 0) {
                            String bidPriceStr = orderBook.getBids().get(0).get(0);
                            if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                                double bidPrice = Double.parseDouble(bidPriceStr);
                                state.setBinanceBidPrice(bidPrice);
                            } else {
                                logger.warn("Binance bid price is empty string");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid Binance bid price format: {}", orderBook.getBids().get(0).get(0), e);
                    } catch (Exception e) {
                        logger.warn("Error parsing Binance bid price", e);
                    }
                }

                if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
                    try {
                        // 检查是否有足够的深度数据且不为空字符串
                        if (orderBook.getAsks().get(0) != null && orderBook.getAsks().get(0).size() > 0) {
                            String askPriceStr = orderBook.getAsks().get(0).get(0);
                            if (askPriceStr != null && !askPriceStr.isEmpty()) {
                                double askPrice = Double.parseDouble(askPriceStr);
                                state.setBinanceAskPrice(askPrice);
                            } else {
                                logger.warn("Binance ask price is empty string");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid Binance ask price format: {}", orderBook.getAsks().get(0).get(0), e);
                    } catch (Exception e) {
                        logger.warn("Error parsing Binance ask price", e);
                    }
                }

                // 更新中间价
                if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                    state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
                }
            }
        }
    }

    // Setter methods for Spring dependency injection

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
                    logger.debug("Bitget最新成交价: {}", String.format("%.2f", tradeTick.price));
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
            logger.debug("收到Bitget订单簿深度事件");
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
                logger.debug("Bitget订单簿数据: bids.size={}, asks.size={}",
                        orderBook.getBids() != null ? orderBook.getBids().size() : 0,
                        orderBook.getAsks() != null ? orderBook.getAsks().size() : 0);

                // 提取最佳买卖价
                if (orderBook.getBids() != null && !orderBook.getBids().isEmpty()) {
                    try {
                        // 检查是否有足够的深度数据且不为空字符串
                        if (orderBook.getBids().get(0) != null && orderBook.getBids().get(0).size() > 0) {
                            String bidPriceStr = orderBook.getBids().get(0).get(0);
                            logger.debug("Bitget买价字符串: '{}'", bidPriceStr);
                            if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                                double bidPrice = Double.parseDouble(bidPriceStr);
                                state.setBitgetBidPrice(bidPrice);
                                logger.debug("Bitget买价解析成功: {}", bidPrice);
                            } else {
                                logger.warn("Bitget bid price is empty string");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid Bitget bid price format: {}", orderBook.getBids().get(0).get(0), e);
                    } catch (Exception e) {
                        logger.warn("Error parsing Bitget bid price", e);
                    }
                }

                if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
                    try {
                        // 检查是否有足够的深度数据且不为空字符串
                        if (orderBook.getAsks().get(0) != null && orderBook.getAsks().get(0).size() > 0) {
                            String askPriceStr = orderBook.getAsks().get(0).get(0);
                            logger.debug("Bitget卖价字符串: '{}'", askPriceStr);
                            if (askPriceStr != null && !askPriceStr.isEmpty()) {
                                double askPrice = Double.parseDouble(askPriceStr);
                                state.setBitgetAskPrice(askPrice);
                                logger.debug("Bitget卖价解析成功: {}", askPrice);
                            } else {
                                logger.warn("Bitget ask price is empty string");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid Bitget ask price format: {}", orderBook.getAsks().get(0).get(0), e);
                    } catch (Exception e) {
                        logger.warn("Error parsing Bitget ask price", e);
                    }
                }

                // 更新中间价
                if (state.getBitgetBidPrice() > 0 && state.getBitgetAskPrice() > 0) {
                    double midPrice = (state.getBitgetBidPrice() + state.getBitgetAskPrice()) / 2;
                    state.setBitgetMidPrice(midPrice);
                    logger.debug("Bitget中间价更新: {}", midPrice);
                }
            }
        }
    }

    /**
     * 处理币安订单簿增量更新事件 (OrderBookDeltas)
     */
    private class BinanceOrderBookDeltaEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDeltas deltas) {
                // 增量更新：只提取最佳买卖价更新
                if (deltas.getBids() != null && !deltas.getBids().isEmpty()) {
                    try {
                        if (deltas.getBids().get(0) != null && deltas.getBids().get(0).size() > 0) {
                            String bidPriceStr = deltas.getBids().get(0).get(0);
                            if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                                double bidPrice = Double.parseDouble(bidPriceStr);
                                state.setBinanceBidPrice(bidPrice);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing Binance delta bid price", e);
                    }
                }

                if (deltas.getAsks() != null && !deltas.getAsks().isEmpty()) {
                    try {
                        if (deltas.getAsks().get(0) != null && deltas.getAsks().get(0).size() > 0) {
                            String askPriceStr = deltas.getAsks().get(0).get(0);
                            if (askPriceStr != null && !askPriceStr.isEmpty()) {
                                double askPrice = Double.parseDouble(askPriceStr);
                                state.setBinanceAskPrice(askPrice);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing Binance delta ask price", e);
                    }
                }

                // 更新中间价
                if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                    state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
                }
            } else if (marketData.getMessage() instanceof OrderBookDelta delta) {
                // 单一增量更新（bookTicker类型）
                if (delta.getBidPrice() > 0) {
                    state.setBinanceBidPrice(delta.getBidPrice());
                }
                if (delta.getAskPrice() > 0) {
                    state.setBinanceAskPrice(delta.getAskPrice());
                }

                // 更新中间价
                if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                    state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
                }
            }
        }
    }

    /**
     * 处理Bitget订单簿增量更新事件
     */
    private class BitgetOrderBookDeltaEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof OrderBookDeltas deltas) {
                // 增量更新：只提取最佳买卖价更新
                if (deltas.getBids() != null && !deltas.getBids().isEmpty()) {
                    try {
                        if (deltas.getBids().get(0) != null && deltas.getBids().get(0).size() > 0) {
                            String bidPriceStr = deltas.getBids().get(0).get(0);
                            if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                                double bidPrice = Double.parseDouble(bidPriceStr);
                                state.setBitgetBidPrice(bidPrice);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing Bitget delta bid price", e);
                    }
                }

                if (deltas.getAsks() != null && !deltas.getAsks().isEmpty()) {
                    try {
                        if (deltas.getAsks().get(0) != null && deltas.getAsks().get(0).size() > 0) {
                            String askPriceStr = deltas.getAsks().get(0).get(0);
                            if (askPriceStr != null && !askPriceStr.isEmpty()) {
                                double askPrice = Double.parseDouble(askPriceStr);
                                state.setBitgetAskPrice(askPrice);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing Bitget delta ask price", e);
                    }
                }

                // 更新中间价
                if (state.getBitgetBidPrice() > 0 && state.getBitgetAskPrice() > 0) {
                    double midPrice = (state.getBitgetBidPrice() + state.getBitgetAskPrice()) / 2;
                    state.setBitgetMidPrice(midPrice);
                }
            } else if (marketData.getMessage() instanceof OrderBookDelta delta) {
                // 单一增量更新
                if (delta.getBidPrice() > 0) {
                    state.setBitgetBidPrice(delta.getBidPrice());
                }
                if (delta.getAskPrice() > 0) {
                    state.setBitgetAskPrice(delta.getAskPrice());
                }

                // 更新中间价
                if (state.getBitgetBidPrice() > 0 && state.getBitgetAskPrice() > 0) {
                    double midPrice = (state.getBitgetBidPrice() + state.getBitgetAskPrice()) / 2;
                    state.setBitgetMidPrice(midPrice);
                }
            }
        }
    }

    /**
     * 处理币安QuoteTick事件 (来自bookTicker流)
     * 提供最优买卖价的高频实时更新
     */
    private class BinanceQuoteTickEventHandler implements EventHandler<MarketData> {
        @Override
        public void handle(Event<MarketData> event) {
            MarketData marketData = event.payload;
            if (marketData.getMessage() instanceof com.tanggo.fund.jnautilustrader.core.entity.data.QuoteTick quoteTick) {
                // 更新最优买卖价
                if (quoteTick.getBidPrice() > 0) {
                    state.setBinanceBidPrice(quoteTick.getBidPrice());
                }
                if (quoteTick.getAskPrice() > 0) {
                    state.setBinanceAskPrice(quoteTick.getAskPrice());
                }

                // 更新中间价
                if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                    state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
                }

                if (params.isDebugMode()) {
                    logger.debug("币安bookTicker更新: bid={}, ask={}", quoteTick.getBidPrice(), quoteTick.getAskPrice());
                }
            }
        }
    }


}

