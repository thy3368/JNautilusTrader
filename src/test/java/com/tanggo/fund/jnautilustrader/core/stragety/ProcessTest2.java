package com.tanggo.fund.jnautilustrader.core.stragety;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.event.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bn.BNMDGWWebSocketClient;
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    private ExecutorService wsExecutorService;


    public ThreadFactory ioThreadFactory() {
        return new CustomizableThreadFactory("io-worker-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("io-worker-消费" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    public ThreadFactory timerThreadFactory() {
        return new CustomizableThreadFactory("timer-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("timer-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }


    @BeforeEach
    void setUp() {
        ThreadLogger.info(logger, "=== 初始化测试环境 ===");


        ScheduledExecutorService timerExecutorService= Executors.newScheduledThreadPool(4, timerThreadFactory());



        // 创建线程池
        executorService = Executors.newSingleThreadExecutor(ioThreadFactory());

        wsExecutorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BN-MDGW-WS-Thread");
            t.setDaemon(true);
            return t;
        });


        // 创建市场数据事件仓库
        mdEventRepo = new BlockingQueueEventRepo<>();

        // 创建 WebSocket 客户端
        bnmdgwWebSocketClient = new BNMDGWWebSocketClient(mdEventRepo, timerExecutorService, wsExecutorService);

        logger.info("测试环境初始化完成");
    }

    @AfterEach
    void tearDown() {

        ThreadLogger.info(logger, "=== 清理测试环境 ===");

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

        ThreadLogger.info(logger, "测试环境清理完成，总共接收事件数: {}", eventCount.get());
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
        ThreadLogger.info(logger, "=== 开始测试 WebSocket 客户端多线程运行 ===");

        // 启动 WebSocket 客户端连接
        ThreadLogger.info(logger, "正在启动 WebSocket 客户端...");
        bnmdgwWebSocketClient.start_link();

        // 等待连接建立
        Thread.sleep(2000);


        // 启动市场数据事件消费者线程，打印接收到的行情
        executorService.submit(() -> {
            ThreadLogger.info(logger, "市场数据消费者线程已启动");

            while (running.get()) {
                var event = mdEventRepo.receive();
                if (event != null) {
                    eventCount.incrementAndGet();
                    ThreadLogger.info(logger, "收到市场数据事件: type={}, payload={}", event.type, event.payload);


                }
            }
        });

        // 让测试一直运行，直到手动中断
        ThreadLogger.info(logger, "测试正在运行...按 Ctrl+C 停止");
        // 打印线程树
//        printThreadTree();
        Thread.currentThread().join();  // 永远等待，直到线程被中断
    }

    /**
     * 打印线程树结构（从 main 线程开始）
     */
    private void printThreadTree() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] allThreads = threadMXBean.dumpAllThreads(false, false);

        // 构建线程ID到线程信息的映射
        Map<Long, ThreadInfo> threadMap = new HashMap<>();
        for (ThreadInfo info : allThreads) {
            threadMap.put(info.getThreadId(), info);
        }

        // 尝试根据线程组和名称建立关系
        // 1. 首先找到 main 线程
        ThreadInfo mainThread = null;
        for (ThreadInfo info : allThreads) {
            if ("main".equals(info.getThreadName())) {
                mainThread = info;
                break;
            }
        }

        if (mainThread == null) {
            ThreadLogger.warn(logger, "未找到 main 线程");
            return;
        }

        ThreadLogger.info(logger, "=== 线程树结构 (根线程: main) ===");
        printThreadTreeNode(mainThread, threadMap, 0);

        // 打印未关联到 main 线程的其他线程
        Set<Long> printedThreadIds = new HashSet<>();
        collectPrintedThreadIds(mainThread, threadMap, printedThreadIds);

        List<ThreadInfo> orphanThreads = new ArrayList<>();
        for (ThreadInfo info : allThreads) {
            if (!printedThreadIds.contains(info.getThreadId()) && !"main".equals(info.getThreadName())) {
                orphanThreads.add(info);
            }
        }

        if (!orphanThreads.isEmpty()) {
            ThreadLogger.info(logger, "=== 孤立线程 ===");
            for (ThreadInfo info : orphanThreads) {
                printThreadTreeNode(info, threadMap, 0);
            }
        }
    }

    /**
     * 递归打印线程树节点
     */
    private void printThreadTreeNode(ThreadInfo info, Map<Long, ThreadInfo> threadMap, int depth) {
        String indent = "  ".repeat(depth);
        String stateStr = info.getThreadState().toString();
        String priorityStr = "P" + info.getPriority();
        String daemonStr = info.isDaemon() ? "[Daemon]" : "";

        ThreadLogger.info(logger, "{}[{}] {} ({}) {} {}", indent, info.getThreadId(), info.getThreadName(), stateStr, priorityStr, daemonStr);

        // 递归打印子线程（这里我们基于线程组和名称关联）
        // 简单的启发式规则：如果线程名称包含父线程的名称特征，或者线程组相同
        for (ThreadInfo childInfo : threadMap.values()) {
            if (childInfo.getThreadId() != info.getThreadId()) {
                boolean isChild = isChildThread(info, childInfo);
                if (isChild) {
                    printThreadTreeNode(childInfo, threadMap, depth + 1);
                }
            }
        }
    }

    /**
     * 判断是否为子线程的简单启发式方法
     */
    private boolean isChildThread(ThreadInfo parentInfo, ThreadInfo childInfo) {
        // 检查线程组是否相同
        ThreadGroup parentGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup childGroup = Thread.currentThread().getThreadGroup();
        if (parentGroup != null && childGroup != null && parentGroup == childGroup) {
            // 检查子线程是否可能是父线程创建的
            // 例如：pool-1-thread-1 是 Executors.newFixedThreadPool 创建的
            return childInfo.getThreadName().startsWith("pool-") || childInfo.getThreadName().startsWith("BN-MDGW-WS-");
        }
        return false;
    }

    /**
     * 收集已打印的线程ID
     */
    private void collectPrintedThreadIds(ThreadInfo info, Map<Long, ThreadInfo> threadMap, Set<Long> printedIds) {
        printedIds.add(info.getThreadId());
        for (ThreadInfo childInfo : threadMap.values()) {
            if (childInfo.getThreadId() != info.getThreadId() && isChildThread(info, childInfo)) {
                collectPrintedThreadIds(childInfo, threadMap, printedIds);
            }
        }
    }


}
