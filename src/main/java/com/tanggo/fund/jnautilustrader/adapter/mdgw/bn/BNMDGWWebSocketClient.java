package com.tanggo.fund.jnautilustrader.adapter.mdgw.bn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 币安WebSocket客户端 - 订阅实时交易数据
 */
public class BNMDGWWebSocketClient implements Actor {

    private static final Logger logger = LoggerFactory.getLogger(BNMDGWWebSocketClient.class);
    // 币安WebSocket API地址 - 同时订阅交易、订单簿深度和增量更新
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@trade/btcusdt@depth/btcusdt@depthUpdate";
    // 重连间隔（秒）
    private static final int RECONNECT_DELAY = 5;

    private BlockingQueueEventRepo<MarketData> mdEventRepo;
    private ObjectMapper objectMapper;
    private ScheduledExecutorService timerExecutorService;
    private WebSocket webSocket;
    private volatile boolean reconnecting;

    /**
     * 无参构造函数 - Spring需要
     */
    public BNMDGWWebSocketClient() {
        this.objectMapper = new ObjectMapper();
        this.reconnecting = false;
    }

    /**
     * 构造函数 - 用于注入依赖
     */
    public BNMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> mdEventRepo) {
        this();
        this.mdEventRepo = mdEventRepo;
    }

    /**
     * 构造函数 - 包含所有依赖
     */
    public BNMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> mdEventRepo,
                                  ScheduledExecutorService timerExecutorService) {
        this(mdEventRepo);
        this.timerExecutorService = timerExecutorService;
    }


    /**
     * 建立WebSocket连接
     */
    private void connect() {
        if (webSocket != null) {
            logger.warn("WebSocket connection already established");
            return;
        }

        try {
            URI uri = new URI(BINANCE_WS_URL);
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            webSocket = client.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).buildAsync(uri, new WebSocketListener()).join();

            logger.info("Connected to Binance WebSocket: {}", BINANCE_WS_URL);
        } catch (URISyntaxException e) {
            logger.error("Invalid Binance WebSocket URL: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to connect to Binance WebSocket: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * 关闭WebSocket连接
     */

    private void destroy() {
        // 只关闭自己创建的调度器
        if (timerExecutorService != null) {
            timerExecutorService.shutdown();
        }
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown").join();
                logger.info("Binance WebSocket connection closed");
            } catch (Exception e) {
                logger.error("Failed to close Binance WebSocket connection: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        if (reconnecting) {
            return;
        }

        reconnecting = true;
        logger.info("Scheduling reconnection in {} seconds", RECONNECT_DELAY);
        timerExecutorService.schedule(() -> {
            reconnecting = false;
            logger.info("Attempting to reconnect to Binance WebSocket");
            connect();
        }, RECONNECT_DELAY, TimeUnit.SECONDS);
    }

    @Override
    public void start() {
        connect();

    }

    @Override
    public void stop() {
        destroy();

    }

    /**
     * 解析币安WebSocket返回的各种类型的消息
     */
    private Object parseMessage(String message) throws Exception {
        // 首先解析根节点
        JsonNode rootNode = objectMapper.readTree(message);

        // 检查是否是组合流格式（包含stream和data字段）
        if (rootNode.has("stream") && rootNode.has("data")) {
            JsonNode dataNode = rootNode.path("data");
            String eventType = dataNode.path("e").asText();

            switch (eventType) {
                case "trade":
                    return parseTradeTick(dataNode.toString());
                case "aggTrade":
                    return parseAggregateTradeTick(dataNode.toString());
                case "depth":
                    return parseOrderBookDepth(dataNode.toString());
                case "depthUpdate":
                    return parseOrderBookDelta(dataNode.toString());
                case "kline":
                    return parseBar(dataNode.toString());
                case "miniTicker":
                case "ticker":
                    return parseQuoteTick(dataNode.toString());
                case "markPriceUpdate":
                    return parseMarkPriceUpdate(dataNode.toString());
                case "indexPriceUpdate":
                    return parseIndexPriceUpdate(dataNode.toString());
                case "fundingRate":
                    return parseFundingRateUpdate(dataNode.toString());
                case "bookTicker":
                    return parseBookTicker(dataNode.toString());
                default:
                    logger.debug("Received unsupported event type: {}", eventType);
                    return null;
            }
        } else {
            // 单流格式
            String eventType = rootNode.path("e").asText();

            switch (eventType) {
                case "trade":
                    return parseTradeTick(message);
                case "aggTrade":
                    return parseAggregateTradeTick(message);
                case "depth":
                    return parseOrderBookDepth(message);
                case "depthUpdate":
                    return parseOrderBookDelta(message);
                case "kline":
                    return parseBar(message);
                case "miniTicker":
                case "ticker":
                    return parseQuoteTick(message);
                case "markPriceUpdate":
                    return parseMarkPriceUpdate(message);
                case "indexPriceUpdate":
                    return parseIndexPriceUpdate(message);
                case "fundingRate":
                    return parseFundingRateUpdate(message);
                case "bookTicker":
                    return parseBookTicker(message);
                default:
                    logger.debug("Received unsupported event type: {}", eventType);
                    return null;
            }
        }
    }

    /**
     * 确定事件类型字符串
     */
    private String determineEventType(Object message) {
        if (message instanceof TradeTick) {
            return "BINANCE_TRADE_TICK";
        } else if (message instanceof QuoteTick) {
            return "BINANCE_QUOTE_TICK";
        } else if (message instanceof Bar) {
            return "BINANCE_BAR";
        } else if (message instanceof OrderBookDepth10) {
            return "BINANCE_ORDER_BOOK_DEPTH";
        } else if (message instanceof OrderBookDeltas || message instanceof OrderBookDelta) {
            return "BINANCE_ORDER_BOOK_DELTA";
        } else if (message instanceof MarkPriceUpdate) {
            return "BINANCE_MARK_PRICE_UPDATE";
        } else if (message instanceof IndexPriceUpdate) {
            return "BINANCE_INDEX_PRICE_UPDATE";
        } else if (message instanceof FundingRateUpdate) {
            return "BINANCE_FUNDING_RATE_UPDATE";
        }
        return "BINANCE_UNKNOWN";
    }

    /**
     * 解析币安WebSocket返回的交易数据
     */
    private TradeTick parseTradeTick(String message) throws Exception {
        return objectMapper.readValue(message, TradeTick.class);
    }

    /**
     * 解析币安WebSocket返回的聚合交易数据
     */
    private TradeTick parseAggregateTradeTick(String message) throws Exception {
        return objectMapper.readValue(message, TradeTick.class);
    }

    /**
     * 解析币安WebSocket返回的订单簿深度数据
     */
    private OrderBookDepth10 parseOrderBookDepth(String message) throws Exception {
        return objectMapper.readValue(message, OrderBookDepth10.class);
    }

    /**
     * 解析币安WebSocket返回的订单簿增量更新数据
     */
    private OrderBookDeltas parseOrderBookDelta(String message) throws Exception {
        return objectMapper.readValue(message, OrderBookDeltas.class);
    }

    /**
     * 解析币安WebSocket返回的K线数据
     */
    private Bar parseBar(String message) throws Exception {
        return objectMapper.readValue(message, Bar.class);
    }

    /**
     * 解析币安WebSocket返回的报价数据
     */
    private QuoteTick parseQuoteTick(String message) throws Exception {
        return objectMapper.readValue(message, QuoteTick.class);
    }

    /**
     * 解析币安WebSocket返回的标记价格更新数据
     */
    private MarkPriceUpdate parseMarkPriceUpdate(String message) throws Exception {
        return objectMapper.readValue(message, MarkPriceUpdate.class);
    }

    /**
     * 解析币安WebSocket返回的指数价格更新数据
     */
    private IndexPriceUpdate parseIndexPriceUpdate(String message) throws Exception {
        return objectMapper.readValue(message, IndexPriceUpdate.class);
    }

    /**
     * 解析币安WebSocket返回的资金费率更新数据
     */
    private FundingRateUpdate parseFundingRateUpdate(String message) throws Exception {
        return objectMapper.readValue(message, FundingRateUpdate.class);
    }

    /**
     * 解析币安WebSocket返回的最优买卖盘数据
     */
    private QuoteTick parseBookTicker(String message) throws Exception {
        return objectMapper.readValue(message, QuoteTick.class);
    }

    /**
     * 获取连接状态
     */
    public boolean isConnected() {
        return webSocket != null;
    }

    /**
     * WebSocket监听器
     */
    private class WebSocketListener implements WebSocket.Listener {
        // 用于累积分片消息
        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Binance WebSocket connection opened");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = data.toString();
            logger.debug("Received Binance WebSocket message fragment: {}", message);

            try {
                // 累积消息片段
                messageBuffer.append(message);

                // 如果是完整消息，处理它
                if (last) {
                    String completeMessage = messageBuffer.toString();
                    logger.debug("Received complete Binance WebSocket message: {}", completeMessage);

                    // 解析币安WebSocket消息
                    Object parsedMessage = parseMessage(completeMessage);
                    if (parsedMessage != null) {
                        // 创建MarketData实例并发送到仓储
                        MarketData marketData = MarketData.createWithData(parsedMessage);
                        Event<MarketData> event = new Event<>();
                        event.type = determineEventType(parsedMessage);
                        event.payload = marketData;
                        mdEventRepo.send(event);
                        logger.debug("Sent market data event: {}", event.type);
                    }

                    // 清空缓冲区
                    messageBuffer.setLength(0);
                }
            } catch (Exception e) {
                logger.error("Failed to process Binance WebSocket message: {}", e.getMessage(), e);
                // 清空缓冲区以防止后续消息解析错误
                messageBuffer.setLength(0);
            }

            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("Binance WebSocket connection closed: status={}, reason={}", statusCode, reason);
            BNMDGWWebSocketClient.this.webSocket = null;
            // 自动重连
            scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("Binance WebSocket error: {}", error.getMessage(), error);
        }
    }
}
