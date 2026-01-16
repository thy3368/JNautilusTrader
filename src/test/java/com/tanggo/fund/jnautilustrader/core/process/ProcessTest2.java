package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bn.BNMDGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 客户端集成测试
 * <p>
 * 测试场景：
 * 1. WebSocket 客户端在独立线程中运行，接收币安实时市场数据
 * 2. 市场数据事件消费者线程处理接收到的事件
 * 3. 支持优雅的启动和关闭
 *
 * @author JNautilusTrader
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class ProcessTest2 {

    private static final Logger logger = LoggerFactory.getLogger(ProcessTest2.class);
    // 运行状态标志
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
    // 事件计数器
    private final java.util.concurrent.atomic.AtomicLong eventCount = new java.util.concurrent.atomic.AtomicLong(0);
    private Actor bnmdgwWebSocketClient;
    private BlockingQueueEventRepo<MarketData> mdEventRepo;
    // 线程池
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        logger.info("=== 初始化测试环境 ===");

        // 创建线程池
        executorService = Executors.newFixedThreadPool(2);

        // 创建市场数据事件仓库
        mdEventRepo = new BlockingQueueEventRepo<>();

        // 创建 WebSocket 客户端
        bnmdgwWebSocketClient = new BNMDGWWebSocketClient(mdEventRepo);

        logger.info("测试环境初始化完成");
    }

    @AfterEach
    void tearDown() {
        logger.info("=== 清理测试环境 ===");

        // 停止运行
        running.set(false);

        // 关闭 WebSocket 连接
        if (bnmdgwWebSocketClient != null) {
            bnmdgwWebSocketClient.stop();
        }

        // 关闭线程池
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

        logger.info("测试环境清理完成，总共接收事件数: {}", eventCount.get());
    }

    /**
     * 测试 WebSocket 客户端在独立线程中运行
     * <p>
     * 测试流程：
     * 1. 启动 WebSocket 客户端（在 @PostConstruct 中自动连接）
     * 2. 启动市场数据消费者线程
     * 3. 运行指定时间后停止
     * 4. 验证接收到的数据
     */
    @Test
    void testWebSocketClientInSeparateThread() throws InterruptedException {
        logger.info("=== 开始测试 WebSocket 客户端多线程运行 ===");

        // 启动 WebSocket 客户端连接
        logger.info("正在启动 WebSocket 客户端...");
        bnmdgwWebSocketClient.start();

        // 等待连接建立
        Thread.sleep(2000);

//        if (bnmdgwWebSocketClient.isConnected()) {
//            logger.info("WebSocket 客户端连接成功");
//        } else {
//            logger.warn("WebSocket 客户端连接失败或正在重连");
//        }

        // 启动市场数据事件消费者线程，打印接收到的行情
        executorService.submit(() -> {
            logger.info("市场数据消费者线程已启动");
            while (running.get()) {
                var event = mdEventRepo.receive();
                if (event != null) {
                    eventCount.incrementAndGet();
                    logger.info("收到市场数据事件: type={}, payload={}", event.type, event.payload);
                }
            }
        });


        // 等待消费者线程处理完剩余事件
        Thread.sleep(20000);
    }


}
