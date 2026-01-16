package com.tanggo.fund.jnautilustrader.adapter.tradegw.bitget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.TradeCmd;
import com.tanggo.fund.jnautilustrader.core.entity.data.PlaceOrder;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bitget WebSocket交易网关客户端
 * 负责连接Bitget交易WebSocket API，发送交易命令并处理响应
 */
@Component
public class BTTradeGWWebSocketClient implements Actor {

    private static final Logger logger = LoggerFactory.getLogger(BTTradeGWWebSocketClient.class);

    private final BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo;

    private final BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient httpClient;
    @Value("${bitget.websocket.trade.url: wss://ws.bitget.com/v2/ws/private}")
    private String baseWebSocketUrl;
    private String listenKey; // Bitget WebSocket用户数据流监听密钥
    private WebSocket webSocket;
    private volatile boolean connected = false;

    public BTTradeGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo, BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo) {
        this.marketDataBlockingQueueEventRepo = marketDataBlockingQueueEventRepo;
        this.tradeCmdEventRepo = tradeCmdEventRepo;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 初始化连接
     */
    @PostConstruct
    public void init() {
        logger.info("初始化Bitget交易WebSocket客户端");
        connect();
        startCommandProcessing();
    }

    /**
     * 连接到Bitget交易WebSocket
     */
    private void connect() {
        try {
            // 首先获取监听密钥（需要通过REST API获取）
            listenKey = getListenKey();

            String tradeWebSocketUrl = baseWebSocketUrl;
            logger.info("连接到Bitget交易WebSocket: {}", tradeWebSocketUrl);

            webSocket = httpClient.newWebSocketBuilder().buildAsync(URI.create(tradeWebSocketUrl), new TradeWebSocketListener()).get();

            connected = true;
            logger.info("Bitget交易WebSocket连接成功");

            // 认证（需要发送API密钥信息）
            authenticate();
        } catch (Exception e) {
            logger.error("连接Bitget交易WebSocket失败: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * 获取Bitget WebSocket监听密钥（简化实现）
     */
    private String getListenKey() {
        // 实际应该通过Bitget REST API获取
        // POST /api/v2/user/listenKey
        return "mock_listen_key";
    }

    /**
     * 认证WebSocket连接
     */
    private void authenticate() {
        try {
            String authMsg = "{\n" +
                    "    \"op\": \"auth\",\n" +
                    "    \"args\": {\n" +
                    "        \"apiKey\": \"your_api_key\",\n" +
                    "        \"passphrase\": \"your_passphrase\",\n" +
                    "        \"timestamp\": \"" + System.currentTimeMillis() + "\",\n" +
                    "        \"sign\": \"your_signature\"\n" +
                    "    }\n" +
                    "}";
            webSocket.sendText(authMsg, true);
            logger.info("Sent authentication message to Bitget");
        } catch (Exception e) {
            logger.error("Failed to authenticate Bitget WebSocket: {}", e.getMessage(), e);
            handleConnectionError();
        }
    }

    /**
     * 启动命令处理线程
     */
    private void startCommandProcessing() {
        Thread commandThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Event<TradeCmd> event = tradeCmdEventRepo.receive();
                    if (event != null && connected) {
                        handleTradeCommand(event.getPayload());
                    }
                } catch (Exception e) {
                    logger.error("处理交易命令失败: {}", e.getMessage(), e);
                    if (!connected) {
                        logger.warn("WebSocket未连接，等待重连");
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }, "TradeCommandProcessor");
        commandThread.start();
    }

    /**
     * 处理交易命令
     */
    private void handleTradeCommand(TradeCmd tradeCmd) {
        switch (tradeCmd) {
            case PLACE_ORDER:
                Object message = tradeCmd.getMessage();
                if (message instanceof PlaceOrder) {
                    sendOrderCommand((PlaceOrder) message);
                } else {
                    logger.error("PLACE_ORDER命令的消息类型不正确: {}", message.getClass().getName());
                }
                break;
            case CANCEL_ORDER:
                // 处理取消订单命令
                logger.debug("收到取消订单命令: {}", tradeCmd.getMessage());
                break;
            case MODIFY_ORDER:
                // 处理修改订单命令
                logger.debug("收到修改订单命令: {}", tradeCmd.getMessage());
                break;
            case QUERY_ORDER:
                // 处理查询订单命令
                logger.debug("收到查询订单命令: {}", tradeCmd.getMessage());
                break;
            case QUERY_ACCOUNT:
                // 处理查询账户命令
                logger.debug("收到查询账户命令: {}", tradeCmd.getMessage());
                break;
            case QUERY_POSITION:
                // 处理查询仓位命令
                logger.debug("收到查询仓位命令: {}", tradeCmd.getMessage());
                break;
            case CANCEL_ALL_ORDERS:
                // 处理取消所有订单命令
                logger.debug("收到取消所有订单命令: {}", tradeCmd.getMessage());
                break;
            case CLOSE_POSITION:
                // 处理平仓命令
                logger.debug("收到平仓命令: {}", tradeCmd.getMessage());
                break;
            default:
                logger.error("未知的交易命令类型: {}", tradeCmd);
        }
    }

    /**
     * 发送订单命令
     */
    private void sendOrderCommand(PlaceOrder placeOrder) {
        try {
            // 转换PlaceOrder到Bitget API格式
            String orderJson = convertToBitgetOrderFormat(placeOrder);
            logger.debug("发送交易命令: {}", orderJson);

            webSocket.sendText(orderJson, true);
        } catch (Exception e) {
            logger.error("发送交易命令失败: {}", e.getMessage(), e);
            handleConnectionError();
        }
    }

    /**
     * 转换PlaceOrder到Bitget API格式
     */
    private String convertToBitgetOrderFormat(PlaceOrder placeOrder) throws JsonProcessingException {
        String side = placeOrder.isBuy() ? "buy" : "sell";
        String orderType = "limit";

        return "{\n" +
                "    \"op\": \"order\",\n" +
                "    \"args\": [\n" +
                "        {\n" +
                "            \"instType\": \"SPOT\",\n" +
                "            \"instId\": \"BTCUSDT\",\n" +
                "            \"side\": \"" + side + "\",\n" +
                "            \"ordType\": \"" + orderType + "\",\n" +
                "            \"px\": \"" + placeOrder.getPrice() + "\",\n" +
                "            \"sz\": \"" + placeOrder.getQuantity() + "\",\n" +
                "            \"timeInForce\": \"GTC\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
    }

    /**
     * 处理连接错误
     */
    private void handleConnectionError() {
        connected = false;
        closeWebSocket();
        scheduleReconnect();
    }

    /**
     * 关闭WebSocket连接
     */
    private void closeWebSocket() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "关闭连接").get(5, TimeUnit.SECONDS);
                logger.info("WebSocket连接已关闭");
            } catch (Exception e) {
                logger.error("关闭WebSocket连接失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        logger.info("5秒后尝试重连...");
        reconnectExecutor.schedule(() -> {
            logger.info("尝试重连...");
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * 资源清理
     */
    @PreDestroy
    public void destroy() {
        logger.info("正在关闭Bitget交易WebSocket客户端");
        connected = false;
        closeWebSocket();
        reconnectExecutor.shutdown();
        try {
            if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Bitget交易WebSocket客户端已关闭");
    }

    /**
     * 处理交易响应
     */
    private void handleTradeResponse(String responseJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            String op = rootNode.path("op").asText();

            if ("auth".equals(op)) {
                handleAuthResponse(rootNode);
            } else if ("order".equals(op)) {
                handleOrderResponse(rootNode);
            } else if ("account".equals(op)) {
                handleAccountResponse(rootNode);
            } else {
                logger.debug("未知的操作类型: {}", op);
            }
        } catch (Exception e) {
            logger.error("解析交易响应失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理认证响应
     */
    private void handleAuthResponse(JsonNode rootNode) {
        JsonNode codeNode = rootNode.path("code");
        if (codeNode.asInt() == 0) {
            logger.info("Bitget WebSocket认证成功");
        } else {
            String msg = rootNode.path("msg").asText();
            logger.error("Bitget WebSocket认证失败: {}", msg);
            handleConnectionError();
        }
    }

    /**
     * 处理订单响应
     */
    private void handleOrderResponse(JsonNode rootNode) {
        JsonNode codeNode = rootNode.path("code");
        if (codeNode.asInt() == 0) {
            logger.info("订单发送成功");
            // 解析订单信息并创建TradeTick事件
            JsonNode dataNode = rootNode.path("data");
            if (dataNode.isArray() && dataNode.size() > 0) {
                JsonNode orderNode = dataNode.get(0);
                TradeTick tradeTick = parseOrderToTradeTick(orderNode);
                if (tradeTick != null) {
                    Event<MarketData> marketEvent = new Event<>();
                    marketEvent.setType("BITGET_EXECUTION_REPORT");
                    marketEvent.setPayload(MarketData.createWithData(tradeTick));
                    marketDataBlockingQueueEventRepo.send(marketEvent);
                }
            }
        } else {
            String msg = rootNode.path("msg").asText();
            logger.error("订单发送失败: {}", msg);
        }
    }

    /**
     * 解析订单信息到TradeTick
     */
    private TradeTick parseOrderToTradeTick(JsonNode orderNode) {
        TradeTick tick = new TradeTick();
        tick.setTradeId(orderNode.path("ordId").asText());
        tick.setSymbol("BTCUSDT");
        tick.setPrice(orderNode.path("px").asDouble());
        tick.setQuantity(orderNode.path("sz").asDouble());
        tick.setTimestampMs(orderNode.path("ts").asLong());
        tick.setBuyerMaker("buy".equals(orderNode.path("side").asText()));
        return tick;
    }

    /**
     * 处理账户响应
     */
    private void handleAccountResponse(JsonNode accountNode) {
        logger.debug("收到账户更新: {}", accountNode);
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
     * Bitget交易WebSocket监听器
     */
    private class TradeWebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("交易WebSocket连接已打开");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                logger.debug("收到交易响应: {}", data);
                handleTradeResponse(data.toString());
            } catch (Exception e) {
                logger.error("处理交易响应失败: {}", e.getMessage(), e);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("交易WebSocket错误: {}", error.getMessage(), error);
            handleConnectionError();
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.warn("交易WebSocket已关闭: 状态码={}, 原因={}", statusCode, reason);
            connected = false;
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}