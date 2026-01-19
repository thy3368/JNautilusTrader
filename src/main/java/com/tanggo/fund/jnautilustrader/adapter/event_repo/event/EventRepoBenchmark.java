package com.tanggo.fund.jnautilustrader.adapter.event_repo.event;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.event.data.TradeTick;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * EventRepo 性能基准测试
 *
 * 比较不同事件仓库实现的性能：
 * 1. BlockingQueueEventRepo (LinkedBlockingQueue)
 * 2. CacheAlignedRingBufferEventRepo (环形队列，缓存行对齐)
 * 3. DisruptorEventRepo (LMAX Disruptor)
 * 4. AdvancedDisruptorEventRepo (高级Disruptor版本)
 *
 * 测试场景：
 * - 单生产者单消费者
 * - 多生产者多消费者
 * - 不同消息大小
 *
 * 运行方式：
 * mvn clean install -DskipTests
 * mvn exec:java -Dexec.mainClass="com.tanggo.fund.jnautilustrader.adapter.event_repo.event.EventRepoBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class EventRepoBenchmark {

    private EventRepo<TradeTick> blockingQueueRepo;
    private EventRepo<TradeTick> ringBufferRepo;
    private EventRepo<TradeTick> disruptorRepo;
    private EventRepo<TradeTick> advancedDisruptorRepo;

    private TradeTick testData;

    @Setup(Level.Trial)
    public void setup() {
        blockingQueueRepo = new BlockingQueueEventRepo<>();
        ringBufferRepo = new CacheAlignedRingBufferEventRepo<>();
        disruptorRepo = new DisruptorEventRepo<>();
        advancedDisruptorRepo = new AdvancedDisruptorEventRepo<>();

        testData = new TradeTick();
        testData.setSymbol("BTCUSDT");
        testData.setPrice(50000.0);
        testData.setQuantity(0.001);
        testData.setTimestampMs(System.currentTimeMillis());
        testData.setBuyerMaker(true);
        testData.setTradeId("12345");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (disruptorRepo instanceof DisruptorEventRepo) {
            ((DisruptorEventRepo<TradeTick>) disruptorRepo).shutdown();
        }
        if (advancedDisruptorRepo instanceof AdvancedDisruptorEventRepo) {
            ((AdvancedDisruptorEventRepo<TradeTick>) advancedDisruptorRepo).shutdown();
        }
    }

    @Benchmark
    @Threads(1)
    public void testBlockingQueueSingleProducer(Blackhole blackhole) {
        Event<TradeTick> event = new Event<>();
        event.setType("TEST_EVENT");
        event.setPayload(testData);

        boolean sent = blockingQueueRepo.send(event);
        if (sent) {
            Event<TradeTick> received = blockingQueueRepo.receive();
            blackhole.consume(received);
        }
    }

    @Benchmark
    @Threads(2)
    public void testBlockingQueueProducerConsumer(Blackhole blackhole) {
        if (Thread.currentThread().getId() % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            blockingQueueRepo.send(event);
        } else {
            Event<TradeTick> received = blockingQueueRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(1)
    public void testRingBufferSingleProducer(Blackhole blackhole) {
        Event<TradeTick> event = new Event<>();
        event.setType("TEST_EVENT");
        event.setPayload(testData);

        boolean sent = ringBufferRepo.send(event);
        if (sent) {
            Event<TradeTick> received = ringBufferRepo.receive();
            blackhole.consume(received);
        }
    }

    @Benchmark
    @Threads(2)
    public void testRingBufferProducerConsumer(Blackhole blackhole) {
        if (Thread.currentThread().getId() % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            ringBufferRepo.send(event);
        } else {
            Event<TradeTick> received = ringBufferRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(1)
    public void testDisruptorSingleProducer(Blackhole blackhole) {
        Event<TradeTick> event = new Event<>();
        event.setType("TEST_EVENT");
        event.setPayload(testData);

        boolean sent = disruptorRepo.send(event);
        if (sent) {
            Event<TradeTick> received = disruptorRepo.receive();
            blackhole.consume(received);
        }
    }

    @Benchmark
    @Threads(2)
    public void testDisruptorProducerConsumer(Blackhole blackhole) {
        if (Thread.currentThread().getId() % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            disruptorRepo.send(event);
        } else {
            Event<TradeTick> received = disruptorRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(1)
    public void testAdvancedDisruptorSingleProducer(Blackhole blackhole) {
        Event<TradeTick> event = new Event<>();
        event.setType("TEST_EVENT");
        event.setPayload(testData);

        boolean sent = advancedDisruptorRepo.send(event);
        if (sent) {
            Event<TradeTick> received = advancedDisruptorRepo.receive();
            blackhole.consume(received);
        }
    }

    @Benchmark
    @Threads(2)
    public void testAdvancedDisruptorProducerConsumer(Blackhole blackhole) {
        if (Thread.currentThread().getId() % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            advancedDisruptorRepo.send(event);
        } else {
            Event<TradeTick> received = advancedDisruptorRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(4)
    public void testBlockingQueueMultiProducerConsumer(Blackhole blackhole) {
        long threadId = Thread.currentThread().getId();
        if (threadId % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            blockingQueueRepo.send(event);
        } else {
            Event<TradeTick> received = blockingQueueRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(4)
    public void testRingBufferMultiProducerConsumer(Blackhole blackhole) {
        long threadId = Thread.currentThread().getId();
        if (threadId % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            ringBufferRepo.send(event);
        } else {
            Event<TradeTick> received = ringBufferRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(4)
    public void testDisruptorMultiProducerConsumer(Blackhole blackhole) {
        long threadId = Thread.currentThread().getId();
        if (threadId % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            disruptorRepo.send(event);
        } else {
            Event<TradeTick> received = disruptorRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    @Benchmark
    @Threads(4)
    public void testAdvancedDisruptorMultiProducerConsumer(Blackhole blackhole) {
        long threadId = Thread.currentThread().getId();
        if (threadId % 2 == 0) {
            Event<TradeTick> event = new Event<>();
            event.setType("TEST_EVENT");
            event.setPayload(testData);
            advancedDisruptorRepo.send(event);
        } else {
            Event<TradeTick> received = advancedDisruptorRepo.receive();
            if (received != null) {
                blackhole.consume(received);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
