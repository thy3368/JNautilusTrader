package com.tanggo.fund.jnautilustrader.core.stragety;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.event.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bitget.BTMDGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.util.ThreadLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bitget WebSocket 客户端集成测试
 * <p>
 * 测试场景：
 * 1. WebSocket 客户端在独立线程中运行，接收Bitget实时市场数据
 * 2. 市场数据事件消费者线程处理接收到的事件
 * 3. 支持优雅的启动和关闭
 * 4. 测试订单簿深度数据和交易数据的接收
 *
 * @author JNautilusTrader
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class ProcessTest4 {

    private static final Logger logger = LoggerFactory.getLogger(ProcessTest4.class);

    // 运行状态标志
    private final AtomicBoolean running = new AtomicBoolean(true);

    // 事件计数器
    private final AtomicLong eventCount = new AtomicLong(0);

    // 不同类型事件的计数器
    private final AtomicLong tradeTickCount = new AtomicLong(0);
    private final AtomicLong orderBookCount = new AtomicLong(0);

    private Actor btmdgwWebSocketClient;
    private BlockingQueueEventRepo<MarketData> mdEventRepo;

    // 线程池
    private ExecutorService executorService;
    private ScheduledExecutorService timerExecutorService;

    /**
     * IO工作线程工厂
     */
    public ThreadFactory ioThreadFactory() {
        return new CustomizableThreadFactory("io-worker-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("io-worker-bitget-消费-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 定时器线程工厂
     */
    public ThreadFactory timerThreadFactory() {
        return new CustomizableThreadFactory("timer-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("timer-bitget-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    @BeforeEach
    void setUp() {
        ThreadLogger.info(logger, "=== 初始化Bitget WebSocket测试环境 ===");

        // 创建定时器线程池用于重连调度
        timerExecutorService = Executors.newScheduledThreadPool(2, timerThreadFactory());

        // 创建IO工作线程池
        executorService = Executors.newSingleThreadExecutor(ioThreadFactory());

        // 创建市场数据事件仓库
        mdEventRepo = new BlockingQueueEventRepo<>();

        // 创建 Bitget WebSocket 客户端
        btmdgwWebSocketClient = new BTMDGWWebSocketClient(mdEventRepo, timerExecutorService);

        ThreadLogger.info(logger, "Bitget测试环境初始化完成");
    }

    @AfterEach
    void tearDown() {
        ThreadLogger.info(logger, "=== 清理Bitget测试环境 ===");

        // 停止运行
        running.set(false);

        // 关闭 WebSocket 连接
        if (btmdgwWebSocketClient != null) {
            btmdgwWebSocketClient.stop();
        }

        // 关闭定时器线程池
        if (timerExecutorService != null) {
            timerExecutorService.shutdown();
            try {
                if (!timerExecutorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    timerExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                timerExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 关闭工作线程池
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        ThreadLogger.info(logger, "测试环境清理完成 - 总事件数: {}, 交易数据: {}, 订单簿数据: {}",
            eventCount.get(), tradeTickCount.get(), orderBookCount.get());
    }

    /**
     * 测试 Bitget WebSocket 客户端在独立线程中运行
     * <p>
     * 测试流程：
     * 1. 启动 WebSocket 客户端（自动连接到Bitget）
     * 2. 启动市场数据消费者线程
     * 3. 持续接收和处理市场数据
     * 4. 统计不同类型的事件
     */
    @Test
    void testBitgetWebSocketClientInSeparateThread() throws InterruptedException {
        ThreadLogger.info(logger, "=== 开始测试 Bitget WebSocket 客户端多线程运行 ===");

        // 启动 WebSocket 客户端连接
        ThreadLogger.info(logger, "正在启动 Bitget WebSocket 客户端...");
        btmdgwWebSocketClient.start_link();

        // 等待连接建立
        Thread.sleep(3000);

        // 启动市场数据事件消费者线程
        executorService.submit(() -> {
            ThreadLogger.info(logger, "Bitget市场数据消费者线程已启动");

            while (running.get()) {
                try {
                    var event = mdEventRepo.receive();
                    if (event != null) {
                        eventCount.incrementAndGet();

                        // 根据事件类型分类统计
                        if (event.type != null) {
                            if (event.type.contains("TRADE")) {
                                tradeTickCount.incrementAndGet();
                                ThreadLogger.info(logger,
                                    "[交易数据] type={}, payload={}",
                                    event.type, event.payload);
                            } else if (event.type.contains("ORDER_BOOK")) {
                                orderBookCount.incrementAndGet();
                                ThreadLogger.info(logger,
                                    "[订单簿] type={}, payload={}",
                                    event.type, event.payload);
                            } else {
                                ThreadLogger.info(logger,
                                    "[其他数据] type={}, payload={}",
                                    event.type, event.payload);
                            }
                        }

                        // 每100个事件打印一次统计
                        if (eventCount.get() % 100 == 0) {
                            ThreadLogger.info(logger,
                                "=== 统计信息 === 总计: {}, 交易: {}, 订单簿: {}",
                                eventCount.get(), tradeTickCount.get(), orderBookCount.get());
                        }
                    }
                } catch (Exception e) {
                    ThreadLogger.error(logger, "处理市场数据事件时出错", e);
                }
            }

            ThreadLogger.info(logger, "市场数据消费者线程已停止");
        });

        // 让测试持续运行，直到手动中断
        ThreadLogger.info(logger, "测试正在运行...按 Ctrl+C 停止");
        Thread.currentThread().join();  // 永远等待，直到线程被中断
    }

    /**
     * 测试 Bitget WebSocket 客户端的短期运行
     * <p>
     * 此测试运行固定时间后自动停止，用于验证基本功能
     */
    @Test
    void testBitgetWebSocketClientShortRun() throws InterruptedException {
        ThreadLogger.info(logger, "=== 开始测试 Bitget WebSocket 短期运行 ===");

        // 启动 WebSocket 客户端
        ThreadLogger.info(logger, "正在启动 Bitget WebSocket 客户端...");
        btmdgwWebSocketClient.start_link();

        // 等待连接建立
        Thread.sleep(2000);

        // 启动消费者线程
        Future<?> consumerFuture = executorService.submit(() -> {
            ThreadLogger.info(logger, "市场数据消费者线程已启动");

            while (running.get()) {
                try {
                    var event = mdEventRepo.receive();
                    if (event != null) {
                        eventCount.incrementAndGet();

                        if (event.type != null && event.type.contains("TRADE")) {
                            tradeTickCount.incrementAndGet();
                        } else if (event.type != null && event.type.contains("ORDER_BOOK")) {
                            orderBookCount.incrementAndGet();
                        }

                        ThreadLogger.debug(logger, "收到事件: {}", event.type);
                    }
                } catch (Exception e) {
                    ThreadLogger.error(logger, "处理事件时出错", e);
                }
            }
        });

        // 运行30秒后停止
        ThreadLogger.info(logger, "测试将运行30秒...");
        Thread.sleep(30000);

        // 停止测试
        running.set(false);

        // 等待消费者线程结束
        try {
            consumerFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            ThreadLogger.warn(logger, "等待消费者线程结束时出错", e);
        }

        ThreadLogger.info(logger,
            "=== 测试完成 === 总事件数: {}, 交易数据: {}, 订单簿数据: {}",
            eventCount.get(), tradeTickCount.get(), orderBookCount.get());
    }

    /**
     * 测试 Bitget WebSocket 客户端的连接状态
     */
    @Test
    void testBitgetWebSocketConnectionStatus() throws InterruptedException {
        ThreadLogger.info(logger, "=== 测试 Bitget WebSocket 连接状态 ===");

        // 初始状态应该是未连接
        if (btmdgwWebSocketClient instanceof BTMDGWWebSocketClient) {
            BTMDGWWebSocketClient client = (BTMDGWWebSocketClient) btmdgwWebSocketClient;
            ThreadLogger.info(logger, "初始连接状态: {}",
                client.isConnected() ? "已连接" : "未连接");
        }

        // 启动连接
        btmdgwWebSocketClient.start_link();

        // 等待连接建立
        Thread.sleep(3000);

        // 检查连接状态
        if (btmdgwWebSocketClient instanceof BTMDGWWebSocketClient) {
            BTMDGWWebSocketClient client = (BTMDGWWebSocketClient) btmdgwWebSocketClient;
            boolean isConnected = client.isConnected();
            ThreadLogger.info(logger, "连接建立后状态: {}",
                isConnected ? "已连接" : "未连接");

            if (isConnected) {
                ThreadLogger.info(logger, "✓ WebSocket 连接成功建立");
            } else {
                ThreadLogger.warn(logger, "✗ WebSocket 连接未能建立");
            }
        }

        // 运行5秒接收数据
        Thread.sleep(5000);
    }
}
