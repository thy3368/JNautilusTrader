package com.tanggo.fund.jnautilustrader.adapter.event_repo.event;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 LMAX Disruptor 的高性能事件仓库实现
 *
 * Disruptor 是目前业界最高性能的事件发布/订阅框架，专为极低延迟系统设计。
 * 特点：
 * - 环形缓冲区架构，无锁操作
 * - 预分配内存，无 GC 开销
 * - 缓存友好的设计
 * - 支持单生产者和多生产者模式
 * - 多种等待策略（Blocking, Sleeping, Yielding, Busy Spin）
 *
 * 适用场景：
 * - 高频交易系统（HFT）
 * - 金融风险控制系统
 * - 实时数据分析系统
 * - 网络协议处理
 *
 * @param <T> 事件类型参数
 */
public class DisruptorEventRepo<T> implements EventRepo<T> {

    private static final Logger logger = LoggerFactory.getLogger(DisruptorEventRepo.class);

    // 默认缓冲区大小（必须是2的幂）
    private static final int DEFAULT_RING_BUFFER_SIZE = 4096;

    // Disruptor 实例
    private final Disruptor<DisruptorEventWrapper<T>> disruptor;

    // 环形缓冲区
    private final RingBuffer<DisruptorEventWrapper<T>> ringBuffer;

    // 事件处理线程池
    private final ExecutorService executorService;

    // 消费就绪标志
    private final AtomicBoolean isConsumerReady;

    // 最后消费的事件
    private volatile Event<T> lastConsumedEvent;

    // 消费超时时间（毫秒）
    private static final long CONSUME_TIMEOUT = 100;

    /**
     * 事件包装类，用于 Disruptor 的事件发布
     */
    @SuppressWarnings("unchecked")
    public static class DisruptorEventWrapper<T> {
        private Event<T> event;

        public Event<T> getEvent() {
            return event;
        }

        public void setEvent(Event<T> event) {
            this.event = event;
        }

        public void clear() {
            this.event = null;
        }
    }

    /**
     * 构造函数（单生产者模式）
     */
    public DisruptorEventRepo() {
        this(DEFAULT_RING_BUFFER_SIZE, ProducerType.SINGLE);
    }

    /**
     * 构造函数（指定容量和生产者类型）
     *
     * @param ringBufferSize 环形缓冲区大小（必须是2的幂）
     * @param producerType 生产者类型（SINGLE 或 MULTI）
     */
    @SuppressWarnings("unchecked")
    public DisruptorEventRepo(int ringBufferSize, ProducerType producerType) {
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "disruptor-event-consumer");
            t.setDaemon(true);
            return t;
        });

        this.isConsumerReady = new AtomicBoolean(false);

        // 初始化 Disruptor
        this.disruptor = new Disruptor<>(
                DisruptorEventWrapper::new,
                ringBufferSize,
                executorService,
                producerType,
                new BlockingWaitStrategy()  // 使用 Blocking 策略，平衡延迟和CPU消耗
        );

        // 设置事件处理器
        this.disruptor.handleEventsWith((EventHandler<DisruptorEventWrapper<T>>) (eventWrapper, sequence, endOfBatch) -> {
            lastConsumedEvent = eventWrapper.getEvent();
            isConsumerReady.set(true);
            eventWrapper.clear();  // 帮助GC
        });

        // 启动 Disruptor
        this.ringBuffer = disruptor.start();
        logger.info("DisruptorEventRepo initialized with size: {}, producer type: {}",
                ringBufferSize, producerType);
    }

    /**
     * 发送事件（生产者操作）
     *
     * @param event 要发送的事件
     * @return 是否成功发送
     */
    @Override
    public boolean send(Event<T> event) {
        if (event == null) {
            logger.warn("Attempting to send null event");
            return false;
        }

        try {
            // 获取下一个可用的序列
            long sequence = ringBuffer.next();
            try {
                // 获取事件对象并设置数据
                DisruptorEventWrapper<T> eventWrapper = ringBuffer.get(sequence);
                eventWrapper.setEvent(event);
            } finally {
                // 发布事件
                ringBuffer.publish(sequence);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to send event: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 接收事件（消费者操作）
     *
     * @return 接收到的事件，如果没有事件返回null
     */
    @Override
    public Event<T> receive() {
        if (!isConsumerReady.get()) {
            return null;
        }

        Event<T> event = lastConsumedEvent;
        if (event != null) {
            lastConsumedEvent = null;
            isConsumerReady.set(false);
        }
        return event;
    }

    /**
     * 阻塞式接收事件（带超时）
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 接收到的事件，如果超时返回null
     */
    public Event<T> receive(long timeoutMs) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Event<T> event = receive();
            if (event != null) {
                return event;
            }
            Thread.onSpinWait();  // 忙等待优化
        }

        return null;
    }

    /**
     * 无限阻塞接收事件
     *
     * @return 接收到的事件
     */
    public Event<T> receiveBlocking() {
        while (true) {
            Event<T> event = receive();
            if (event != null) {
                return event;
            }
            Thread.onSpinWait();
        }
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        long cursor = ringBuffer.getCursor();
        // 简化实现
        return 0;
    }

    /**
     * 获取缓冲区容量
     */
    public int getCapacity() {
        return ringBuffer.getBufferSize();
    }

    /**
     * 获取剩余空间
     */
    public long getRemainingCapacity() {
        return ringBuffer.remainingCapacity();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return getQueueSize() == 0;
    }

    /**
     * 检查是否已满
     */
    public boolean isFull() {
        return ringBuffer.remainingCapacity() == 0;
    }

    /**
     * 清空队列
     */
    public void clear() {
        lastConsumedEvent = null;
        isConsumerReady.set(false);
        // Disruptor 无法直接清空，但可以通过重置或创建新实例实现
        logger.warn("Disruptor buffer cannot be explicitly cleared. " +
                "Events may still be processed.");
    }

    /**
     * 获取使用率（百分比）
     */
    public double getUsagePercent() {
        int used = getQueueSize();
        int capacity = getCapacity();
        return (double) used / capacity * 100;
    }

    /**
     * 获取当前序号
     */
    public long getCurrentSequence() {
        return ringBuffer.getCursor();
    }

    /**
     * 关闭 Disruptor 和资源
     */
    public void shutdown() {
        logger.info("Shutting down DisruptorEventRepo");
        try {
            disruptor.shutdown();
            executorService.shutdown();
            if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate gracefully");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down", e);
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    /**
     * 打印调试信息
     */
    public void printDebugInfo() {
        int size = getQueueSize();
        int capacity = getCapacity();
        long sequence = getCurrentSequence();

        logger.debug("Disruptor Status: size={}/{}, usage={:.1f}%, sequence={}",
                size, capacity, getUsagePercent(), sequence);
    }

    /**
     * 内部类：事件处理器
     */
    private static class EventConsumer<T> implements EventHandler<DisruptorEventWrapper<T>> {
        private final DisruptorEventRepo<T> repo;

        public EventConsumer(DisruptorEventRepo<T> repo) {
            this.repo = repo;
        }

        @Override
        public void onEvent(DisruptorEventWrapper<T> eventWrapper, long sequence, boolean endOfBatch) {
            repo.lastConsumedEvent = eventWrapper.getEvent();
            repo.isConsumerReady.set(true);
            eventWrapper.clear();
        }
    }
}
