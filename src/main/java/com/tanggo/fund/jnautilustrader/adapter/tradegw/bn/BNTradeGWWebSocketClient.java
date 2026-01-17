package com.tanggo.fund.jnautilustrader.adapter.tradegw.bn;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 币安WebSocket交易网关客户端
 * 负责连接币安交易WebSocket API，发送交易命令并处理响应
 */
public class BNTradeGWWebSocketClient implements Actor {

    private static final Logger logger = LoggerFactory.getLogger(BNTradeGWWebSocketClient.class);

    private BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo;


    private BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo;
    private ObjectMapper objectMapper;
    private ScheduledExecutorService reconnectExecutor;
    private boolean ownScheduler; // 标记是否自己创建的调度器
    private HttpClient httpClient;
    @Value("${binance.websocket.trade.url:wss://stream.binance.com:9443/ws}")
    private String baseWebSocketUrl;
    private String listenKey; // 币安WebSocket用户数据流监听密钥
    private WebSocket webSocket;
    private volatile boolean connected = false;

    /**
     * 无参构造函数 - Spring需要
     */
    public BNTradeGWWebSocketClient() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.ownScheduler = true;
    }

    /**
     * 构造函数 - 用于注入依赖
     */
    public BNTradeGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo,
                                     BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo) {
        this();
        this.marketDataBlockingQueueEventRepo = marketDataBlockingQueueEventRepo;
        this.tradeCmdEventRepo = tradeCmdEventRepo;
    }

    /**
     * 构造函数 - 包含所有依赖
     */
    public BNTradeGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo,
                                     BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo,
                                     ScheduledExecutorService reconnectExecutor) {
        this(marketDataBlockingQueueEventRepo, tradeCmdEventRepo);
        this.reconnectExecutor = reconnectExecutor;
        this.ownScheduler = false;
    }


    /**
     * 初始化连接
     */
    public void init() {
        logger.info("初始化币安交易WebSocket客户端");
        connect();
        startCommandProcessing();
    }

    /**
     * 连接到币安交易WebSocket
     */
    private void connect() {
        try {
            // 首先获取监听密钥（需要通过REST API获取）
            // 这里简化处理，实际应该调用币安API获取listenKey
            listenKey = getListenKey();

            String tradeWebSocketUrl = baseWebSocketUrl + "/" + listenKey;
            logger.info("连接到币安交易WebSocket: {}", tradeWebSocketUrl);

            webSocket = httpClient.newWebSocketBuilder().buildAsync(URI.create(tradeWebSocketUrl), new TradeWebSocketListener()).get();

            connected = true;
            logger.info("币安交易WebSocket连接成功");
        } catch (Exception e) {
            logger.error("连接币安交易WebSocket失败: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * 获取币安WebSocket监听密钥（简化实现）
     */
    private String getListenKey() {
        // 实际应该通过币安REST API获取
        // POST /api/v3/userDataStream
        return "mock_listen_key";
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
            // 转换PlaceOrder到币安API格式
            String orderJson = convertToBinanceOrderFormat(placeOrder);
            logger.debug("发送交易命令: {}", orderJson);

            webSocket.sendText(orderJson, true);
        } catch (Exception e) {
            logger.error("发送交易命令失败: {}", e.getMessage(), e);
            handleConnectionError();
        }
    }

    /**
     * 转换PlaceOrder到币安API格式
     */
    private String convertToBinanceOrderFormat(PlaceOrder placeOrder) throws JsonProcessingException {
        // 手动构建币安API格式的JSON，不依赖JsonProperty标签
        com.fasterxml.jackson.databind.node.ObjectNode orderNode = objectMapper.createObjectNode();
        orderNode.put("symbol", placeOrder.getSymbol());
        orderNode.put("side", placeOrder.getSide());
        orderNode.put("type", placeOrder.getType());
        if (placeOrder.getTimeInForce() != null) {
            orderNode.put("timeInForce", placeOrder.getTimeInForce());
        }
        orderNode.put("quantity", placeOrder.getQuantity());
        orderNode.put("price", placeOrder.getPrice());
        if (placeOrder.getNewClientOrderId() != null) {
            orderNode.put("newClientOrderId", placeOrder.getNewClientOrderId());
        }
        return objectMapper.writeValueAsString(orderNode);
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
    public void destroy() {
        logger.info("正在关闭币安交易WebSocket客户端");
        connected = false;
        closeWebSocket();
        // 只关闭自己创建的调度器
        if (ownScheduler && reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("币安交易WebSocket客户端已关闭");
    }

    /**
     * 处理交易响应
     */
    private void handleTradeResponse(String responseJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            String eventType = rootNode.path("e").asText();

            switch (eventType) {
                case "executionReport":
                    handleExecutionReport(rootNode);
                    break;
                case "outboundAccountPosition":
                    handleAccountPosition(rootNode);
                    break;
                case "balanceUpdate":
                    handleBalanceUpdate(rootNode);
                    break;
                default:
                    logger.debug("未知的交易事件类型: {}", eventType);
            }
        } catch (Exception e) {
            logger.error("解析交易响应失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理订单执行报告
     */
    private void handleExecutionReport(JsonNode report) {
        try {
            // 解析执行报告并转换为TradeTick
            TradeTick tradeTick = parseExecutionReportToTradeTick(report);
            if (tradeTick != null) {
                Event<TradeTick> event = new Event<>();
                event.setType("executionReport");
                event.setPayload(tradeTick);
                Event<MarketData> marketEvent = new Event<>();
                marketEvent.setType("executionReport");
                marketEvent.setPayload(MarketData.createWithData(tradeTick));
                marketDataBlockingQueueEventRepo.send(marketEvent);
            }
        } catch (Exception e) {
            logger.error("解析执行报告失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析执行报告到TradeTick
     */
    private TradeTick parseExecutionReportToTradeTick(JsonNode report) {
        TradeTick tick = new TradeTick();
        tick.setTradeId(report.path("t").asText());
        tick.setSymbol(report.path("s").asText());
        tick.setPrice(report.path("p").asDouble());
        tick.setQuantity(report.path("q").asDouble());
        tick.setTimestampMs(report.path("T").asLong());
        tick.setBuyerMaker(report.path("m").asBoolean());
        return tick;
    }

    /**
     * 处理账户仓位更新
     */
    private void handleAccountPosition(JsonNode position) {
        logger.debug("收到账户仓位更新: {}", position);
    }

    /**
     * 处理余额更新
     */
    private void handleBalanceUpdate(JsonNode balance) {
        logger.debug("收到余额更新: {}", balance);
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
     * 币安交易WebSocket监听器
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
