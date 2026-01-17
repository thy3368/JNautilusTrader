package com.tanggo.fund.jnautilustrader.adapter.mdgw.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanggo.fund.jnautilustrader.adapter.event_repo.event.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bitget WebSocket客户端 - 订阅实时交易数据
 */
public class BTMDGWWebSocketClient implements Actor {

    private static final Logger logger = LoggerFactory.getLogger(BTMDGWWebSocketClient.class);
    // Bitget WebSocket API地址 - 同时订阅交易、订单簿深度和增量更新
    private static final String BITGET_WS_URL = "wss://ws.bitget.com/v2/ws/public";
    // 重连间隔（秒）
    private static final int RECONNECT_DELAY = 5;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BlockingQueueEventRepo<MarketData> mdEventRepo;
    private ScheduledExecutorService timerExecutorService;
    private WebSocket webSocket;
    private volatile boolean reconnecting = false;

    /**
     * 无参构造函数 - Spring需要
     */
    public BTMDGWWebSocketClient() {
        this.reconnecting = false;
    }

    /**
     * 构造函数 - 用于注入依赖
     */
    public BTMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> mdEventRepo) {
        this();
        this.mdEventRepo = mdEventRepo;
    }

    /**
     * 构造函数 - 包含所有依赖
     */
    public BTMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> mdEventRepo, ScheduledExecutorService timerExecutorService) {
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
            URI uri = new URI(BITGET_WS_URL);
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            webSocket = client.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).buildAsync(uri, new WebSocketListener()).join();

            logger.info("Connected to Bitget WebSocket: {}", BITGET_WS_URL);

            // 订阅BTC/USDT现货的交易和订单簿深度数据
            subscribeToMarkets();
        } catch (URISyntaxException e) {
            logger.error("Invalid Bitget WebSocket URL: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to connect to Bitget WebSocket: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * 订阅市场数据
     */
    private void subscribeToMarkets() {
        try {
            String subscribeMsg = "{\n" + "    \"op\": \"subscribe\",\n" + "    \"args\": [\n" + "        {\n" + "            \"instType\": \"SPOT\",\n" + "            \"instId\": \"BTCUSDT\",\n" + "            \"channel\": \"trade\"\n" + "        },\n" + "        {\n" + "            \"instType\": \"SPOT\",\n" + "            \"instId\": \"BTCUSDT\",\n" + "            \"channel\": \"books\",\n" + "            \"sz\": \"10\"\n" + "        }\n" + "    ]\n" + "}";
            webSocket.sendText(subscribeMsg, true);
            logger.info("Subscribed to Bitget market data channels");
        } catch (Exception e) {
            logger.error("Failed to subscribe to Bitget market data: {}", e.getMessage(), e);
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
                logger.info("Bitget WebSocket connection closed");
            } catch (Exception e) {
                logger.error("Failed to close Bitget WebSocket connection: {}", e.getMessage(), e);
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
            logger.info("Attempting to reconnect to Bitget WebSocket");
            connect();
        }, RECONNECT_DELAY, TimeUnit.SECONDS);
    }

    @Override
    public void start_link() {
        connect();

    }

    @Override
    public void stop() {
        destroy();

    }

    /**
     * 解析Bitget WebSocket返回的各种类型的消息
     */
    private Object parseMessage(String message) throws Exception {
        JsonNode rootNode = objectMapper.readTree(message);

        // 处理订阅确认和心跳
        if (rootNode.has("event")) {
            String event = rootNode.path("event").asText();
            if ("subscribe".equals(event)) {
                logger.debug("Subscription confirmed: {}", message);
                return null;
            } else if ("pong".equals(event)) {
                logger.debug("Received pong from Bitget");
                return null;
            }
        }

        // 处理业务数据
        if (rootNode.has("arg") && rootNode.has("data")) {
            JsonNode argNode = rootNode.path("arg");
            String channel = argNode.path("channel").asText();
            JsonNode dataNode = rootNode.path("data");

            switch (channel) {
                case "trade":
                    return parseTradeTick(dataNode);
                case "books":
                    return parseOrderBookDepth(dataNode);
                default:
                    logger.debug("Received unsupported channel: {}", channel);
                    return null;
            }
        }

        logger.debug("Received unrecognized message format: {}", message);
        return null;
    }

    /**
     * 确定事件类型字符串
     */
    private String determineEventType(Object message) {
        if (message instanceof TradeTick) {
            return "BITGET_TRADE_TICK";
        } else if (message instanceof QuoteTick) {
            return "BITGET_QUOTE_TICK";
        } else if (message instanceof Bar) {
            return "BITGET_BAR";
        } else if (message instanceof OrderBookDepth10) {
            return "BITGET_ORDER_BOOK_DEPTH";
        } else if (message instanceof OrderBookDeltas || message instanceof OrderBookDelta) {
            return "BITGET_ORDER_BOOK_DELTA";
        } else if (message instanceof MarkPriceUpdate) {
            return "BITGET_MARK_PRICE_UPDATE";
        } else if (message instanceof IndexPriceUpdate) {
            return "BITGET_INDEX_PRICE_UPDATE";
        } else if (message instanceof FundingRateUpdate) {
            return "BITGET_FUNDING_RATE_UPDATE";
        }
        return "BITGET_UNKNOWN";
    }

    /**
     * 解析Bitget WebSocket返回的交易数据
     */
    private TradeTick parseTradeTick(JsonNode dataNode) {
        TradeTick tick = new TradeTick();

        if (dataNode.isArray() && dataNode.size() > 0) {
            JsonNode tradeNode = dataNode.get(0);
            tick.setTradeId(tradeNode.path("tradeId").asText());
            tick.setSymbol("BTCUSDT");
            tick.setPrice(tradeNode.path("px").asDouble());
            tick.setQuantity(tradeNode.path("sz").asDouble());
            tick.setTimestampMs(tradeNode.path("ts").asLong());
            tick.setBuyerMaker("buy".equals(tradeNode.path("side").asText()));
        }

        return tick;
    }

    /**
     * 解析Bitget WebSocket返回的订单簿深度数据
     */
    private OrderBookDepth10 parseOrderBookDepth(JsonNode dataNode) {
        OrderBookDepth10 orderBook = new OrderBookDepth10();
        orderBook.setSymbol("BTCUSDT");

        if (dataNode.isArray() && dataNode.size() > 0) {
            JsonNode bookNode = dataNode.get(0);

            // 解析买盘
            JsonNode bidsNode = bookNode.path("bids");
            orderBook.setBids(parseBookEntries(bidsNode));

            // 解析卖盘
            JsonNode asksNode = bookNode.path("asks");
            orderBook.setAsks(parseBookEntries(asksNode));
        }

        return orderBook;
    }

    /**
     * 解析订单簿条目
     */
    private java.util.List<java.util.List<String>> parseBookEntries(JsonNode entriesNode) {
        java.util.List<java.util.List<String>> entries = new java.util.ArrayList<>();

        if (entriesNode.isArray()) {
            for (JsonNode entryNode : entriesNode) {
                java.util.List<String> entry = new java.util.ArrayList<>();

                // Bitget books频道返回的格式是 ["价格", "数量"] 数组
                if (entryNode.isArray() && entryNode.size() >= 2) {
                    String price = entryNode.get(0).asText();
                    String quantity = entryNode.get(1).asText();

                    // 检查价格和数量是否为空字符串
                    if (!price.isEmpty() && !quantity.isEmpty()) {
                        entry.add(price);
                        entry.add(quantity);
                        entries.add(entry);
                    } else {
                        logger.debug("忽略无效的订单簿条目: 价格={}, 数量={}", price, quantity);
                    }
                } else {
                    logger.debug("忽略无效的订单簿条目格式: {}", entryNode);
                }
            }
        }

        return entries;
    }

    /**
     * 解析Bitget WebSocket返回的K线数据
     */
    private Bar parseBar(String message) throws Exception {
        return objectMapper.readValue(message, Bar.class);
    }

    /**
     * 解析Bitget WebSocket返回的报价数据
     */
    private QuoteTick parseQuoteTick(String message) throws Exception {
        return objectMapper.readValue(message, QuoteTick.class);
    }

    /**
     * 解析Bitget WebSocket返回的标记价格更新数据
     */
    private MarkPriceUpdate parseMarkPriceUpdate(String message) throws Exception {
        return objectMapper.readValue(message, MarkPriceUpdate.class);
    }

    /**
     * 解析Bitget WebSocket返回的指数价格更新数据
     */
    private IndexPriceUpdate parseIndexPriceUpdate(String message) throws Exception {
        return objectMapper.readValue(message, IndexPriceUpdate.class);
    }

    /**
     * 解析Bitget WebSocket返回的资金费率更新数据
     */
    private FundingRateUpdate parseFundingRateUpdate(String message) throws Exception {
        return objectMapper.readValue(message, FundingRateUpdate.class);
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
            logger.info("Bitget WebSocket connection opened");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = data.toString();
            logger.debug("Received Bitget WebSocket message fragment: {}", message);

            try {
                // 累积消息片段
                messageBuffer.append(message);

                // 如果是完整消息，处理它
                if (last) {
                    String completeMessage = messageBuffer.toString();
                    logger.debug("Received complete Bitget WebSocket message: {}", completeMessage);

                    // 解析Bitget WebSocket消息
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
                logger.error("Failed to process Bitget WebSocket message: {}", e.getMessage(), e);
                // 清空缓冲区以防止后续消息解析错误
                messageBuffer.setLength(0);
            }

            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("Bitget WebSocket connection closed: status={}, reason={}", statusCode, reason);
            BTMDGWWebSocketClient.this.webSocket = null;
            // 自动重连
            scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("Bitget WebSocket error: {}", error.getMessage(), error);
        }
    }
}
