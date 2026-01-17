package com.tanggo.fund.jnautilustrader.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


// 1. 市场行情接收解码(行情适配器 每适配器一个线程） eventloop; 2.行情策略及策略计算发出交易指令（策略执行 每个策略一个线程）eventloop; 交易编码及发送（交易适配器 每适配器一个线程）eventloop

/**
 * 高性能线程模型配置
 * 针对低时延交易系统优化的线程池配置
 * 遵循Clean Architecture原则,隔离市场数据和交易执行路径
 */
public class ThreadConfig {

    /**
     * 线程池默认参数
     */
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int QUEUE_CAPACITY = 1024;
    private static final long KEEP_ALIVE_TIME = 60L;

    // ==================== 市场数据路径线程池 ====================

    /**
     * 市场数据接收线程工厂 - 专用于WebSocket市场数据接收
     * 优先级: 高 (仅次于交易执行)
     */
    @Bean("marketDataThreadFactory")
    public ThreadFactory marketDataThreadFactory() {
        return new CustomizableThreadFactory("md-receiver-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("md-receiver-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.MAX_PRIORITY - 1); // 高优先级,但低于交易执行
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 市场数据处理线程工厂 - 用于市场数据解析和验证
     * 优先级: 中高
     */
    @Bean("marketDataProcessorThreadFactory")
    public ThreadFactory marketDataProcessorThreadFactory() {
        return new CustomizableThreadFactory("md-processor-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("md-processor-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY + 2);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 市场数据接收线程池
     * 固定大小线程池,每个交易所一个专用线程,确保数据接收稳定性
     */
    @Bean("marketDataExecutorService")
    public ExecutorService marketDataExecutorService() {
        int poolSize = 4; // 预留4个交易所的接收线程
        return new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY * 2), // 市场数据量大,队列容量加倍
                marketDataThreadFactory(), new ThreadPoolExecutor.DiscardOldestPolicy() // 丢弃最旧数据,保证实时性
        );
    }

    /**
     * 市场数据处理线程池
     * 动态线程池,根据市场数据流量自动调整
     */
    @Bean("marketDataProcessorExecutorService")
    public ExecutorService marketDataProcessorExecutorService() {
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY * 2), marketDataProcessorThreadFactory(), new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    // ==================== 交易执行路径线程池 ====================

    /**
     * 交易指令执行线程工厂 - 专用于交易指令发送和确认
     * 优先级: 最高 (关键路径,目标时延<1μs)
     */
    @Bean("tradingThreadFactory")
    public ThreadFactory tradingThreadFactory() {
        return new CustomizableThreadFactory("trading-executor-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("trading-executor-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.MAX_PRIORITY); // 最高优先级
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 交易指令执行线程池
     * 固定小容量线程池,确保交易执行的确定性和低时延
     * 每个交易所一个专用线程,避免竞争
     */
    @Bean("tradingExecutorService")
    public ExecutorService tradingExecutorService() {
        int poolSize = 4; // 预留4个交易所的交易线程
        return new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256), // 小队列,快速失败
                tradingThreadFactory(), new ThreadPoolExecutor.AbortPolicy() // 拒绝策略:抛出异常,绝不丢弃交易指令
        );
    }

    // ==================== 策略计算路径线程池 ====================

    /**
     * 策略计算线程工厂 - 用于套利策略计算
     * 优先级: 中高
     */
    @Bean("strategyThreadFactory")
    public ThreadFactory strategyThreadFactory() {
        return new CustomizableThreadFactory("strategy-calc-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("strategy-calc-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY + 1); // 策略计算优先级较高
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 策略计算线程池 - 执行跨交易所套利策略计算
     * 使用动态线程池,根据负载自动调整
     */
    @Bean("strategyExecutorService")
    public ExecutorService strategyExecutorService() {
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), strategyThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy() // 调用者运行策略,防止任务丢失
        );
    }

    // ==================== 辅助路径线程池 ====================

    /**
     * IO操作线程工厂 - 用于网络通信和文件I/O
     */
    @Bean("ioThreadFactory")
    public ThreadFactory ioThreadFactory() {
        return new CustomizableThreadFactory("io-worker-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("io-worker-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * 定时器线程工厂 - 用于定时任务
     */
    @Bean("timerThreadFactory")
    public ThreadFactory timerThreadFactory() {
        return new CustomizableThreadFactory("timer-task-") {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = super.newThread(r);
                thread.setName("timer-task-" + threadNumber.getAndIncrement());
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * IO操作线程池 - 处理网络通信和文件I/O
     * 使用虚拟线程池(Java 21+)或CachedThreadPool
     */
    @Bean("ioExecutorService")
    public ExecutorService ioExecutorService() {
        // Java 21+ 推荐使用虚拟线程
        // return Executors.newVirtualThreadPerTaskExecutor();

        return new ThreadPoolExecutor(CORE_POOL_SIZE, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(), ioThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 定时器线程池 - 执行定时任务(心跳、重连、健康检查等)
     */
    @Bean("timerExecutorService")
    public ExecutorService timerExecutorService() {
        return Executors.newScheduledThreadPool(4, timerThreadFactory());
    }

    /**
     * 单线程执行器 - 用于需要串行处理的任务
     */
    @Bean("singleThreadExecutorService")
    public ExecutorService singleThreadExecutorService() {
        return Executors.newSingleThreadExecutor(ioThreadFactory());
    }

    @Bean("wsExecutorService")
    public ExecutorService wsExecutorService() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BN-MDGW-WS-Thread");
            t.setDaemon(true);
            return t;
        });

    }
}


