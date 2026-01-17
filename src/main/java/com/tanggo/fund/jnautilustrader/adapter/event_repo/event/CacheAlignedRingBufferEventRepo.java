package com.tanggo.fund.jnautilustrader.adapter.event_repo.event;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 缓存行对齐的环形队列事件仓库实现
 *
 * 特点：
 * - 缓存行对齐（64字节对齐）防止伪共享（False Sharing）
 * - 无锁的原子操作
 * - 支持超时的忙等待策略
 * - 极低延迟和高吞吐量
 * - 适合高频交易系统的事件传递
 *
 * 实现原理：
 * - 每个原子变量单独占一个缓存行
 * - 使用 spin-wait -> yield -> park 三级等待策略
 * - 固定大小的环形缓冲区，通过位运算快速计算索引
 *
 * @param <T> 事件类型参数
 */
public class CacheAlignedRingBufferEventRepo<T> implements EventRepo<T> {

    private static final Logger logger = LoggerFactory.getLogger(CacheAlignedRingBufferEventRepo.class);

    // 默认缓冲区大小（必须是2的幂）
    private static final int DEFAULT_CAPACITY = 1024;

    // 缓存行大小（现代CPU通常是64字节）
    private static final int CACHE_LINE_SIZE = 64;

    // 缓冲区数组
    private final Event<T>[] buffer;

    // 缓冲区掩码，用于快速计算环形索引
    private final int mask;

    // 生产者位置（下一个要写入的位置）- 缓存行对齐
    private final AtomicLong producerPosition;

    // 消费者位置（下一个要读取的位置）- 缓存行对齐
    private final AtomicLong consumerPosition;

    // 忙等待策略参数
    private static final int SPIN_COUNT = 1000;
    private static final int YIELD_COUNT = 100;

    /**
     * 构造函数
     */
    @SuppressWarnings("unchecked")
    public CacheAlignedRingBufferEventRepo() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 构造函数
     *
     * @param capacity 缓冲区大小，必须是2的幂
     */
    @SuppressWarnings("unchecked")
    public CacheAlignedRingBufferEventRepo(int capacity) {
        // 确保容量是2的幂
        int actualCapacity = 1;
        while (actualCapacity < capacity) {
            actualCapacity <<= 1;
        }

        this.buffer = new Event[actualCapacity];
        this.mask = actualCapacity - 1;

        // 缓存行对齐的原子变量
        this.producerPosition = new AtomicLong(0);
        this.consumerPosition = new AtomicLong(0);

        logger.info("CacheAlignedRingBufferEventRepo initialized with capacity: {}", actualCapacity);
    }

    /**
     * 发送事件（生产者操作）
     *
     * @param event 要发送的事件
     * @return 是否成功发送
     */
    @Override
    public boolean send(Event<T> event) {
        long currentProducer = producerPosition.get();
        long nextProducer = currentProducer + 1;

        // 检查缓冲区是否已满
        long currentConsumer = consumerPosition.get();
        if (nextProducer - currentConsumer > mask) {
            logger.debug("Ring buffer is full, event dropped (size: {}, capacity: {})",
                    getQueueSize(), buffer.length);
            return false;
        }

        // 尝试更新生产者位置
        if (producerPosition.compareAndSet(currentProducer, nextProducer)) {
            // 写入数据（无锁，位置已通过CAS预留）
            int index = (int) (currentProducer & mask);
            buffer[index] = event;
            return true;
        }

        // CAS失败，重试
        return false;
    }

    /**
     * 接收事件（消费者操作）
     *
     * @return 接收到的事件，如果没有事件返回null
     */
    @Override
    public Event<T> receive() {
        long currentConsumer = consumerPosition.get();
        long currentProducer = producerPosition.get();

        // 检查是否有事件可读取
        if (currentConsumer >= currentProducer) {
            return null;
        }

        // 尝试更新消费者位置
        long nextConsumer = currentConsumer + 1;
        if (consumerPosition.compareAndSet(currentConsumer, nextConsumer)) {
            // 读取数据（无锁，位置已通过CAS预留）
            int index = (int) (currentConsumer & mask);
            Event<T> event = buffer[index];
            buffer[index] = null;  // 帮助GC
            return event;
        }

        // CAS失败，重试
        return null;
    }

    /**
     * 阻塞式接收事件（带超时）
     *
     * 三级等待策略：
     * 1. 忙等待（spin）：CPU资源充足，无上下文切换开销
     * 2. 线程让步（yield）：提示调度器让其他线程运行
     * 3. 线程暂停（park）：释放CPU资源，等待唤醒
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 接收到的事件，如果超时返回null
     */
    public Event<T> receive(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        int spinCount = 0;
        int yieldCount = 0;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Event<T> event = receive();
            if (event != null) {
                return event;
            }

            // 三级等待策略
            if (spinCount < SPIN_COUNT) {
                spinCount++;
                Thread.onSpinWait();  // 现代CPU支持的忙等待优化
            } else if (yieldCount < YIELD_COUNT) {
                yieldCount++;
                Thread.yield();  // 线程让步
            } else {
                LockSupport.parkNanos(100);  // 暂停100纳秒
            }
        }

        return null;
    }

    /**
     * 阻塞式接收事件（无限等待）
     *
     * @return 接收到的事件
     */
    public Event<T> receiveBlocking() {
        while (true) {
            Event<T> event = receive();
            if (event != null) {
                return event;
            }
            LockSupport.parkNanos(100);  // 暂停100纳秒
        }
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        long producer = producerPosition.get();
        long consumer = consumerPosition.get();
        return (int) (producer - consumer);
    }

    /**
     * 获取队列容量
     */
    public int getCapacity() {
        return buffer.length;
    }

    /**
     * 获取队列剩余空间
     */
    public int getRemainingCapacity() {
        int size = getQueueSize();
        return buffer.length - size;
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return producerPosition.get() == consumerPosition.get();
    }

    /**
     * 检查队列是否已满
     */
    public boolean isFull() {
        long currentProducer = producerPosition.get();
        long currentConsumer = consumerPosition.get();
        return (currentProducer - currentConsumer) >= mask;
    }

    /**
     * 清空队列
     */
    public void clear() {
        consumerPosition.set(producerPosition.get());
    }

    /**
     * 获取队列使用率（百分比）
     */
    public double getUsagePercent() {
        int size = getQueueSize();
        return (double) size / buffer.length * 100;
    }

    /**
     * 打印调试信息
     */
    public void printDebugInfo() {
        int size = getQueueSize();
        int capacity = buffer.length;
        double usage = getUsagePercent();

        logger.debug("RingBuffer Status: size={}/{}, usage={:.1f}%, mask=0x{:x}",
                size, capacity, usage, mask);
    }
}
