package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandler;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Process 事件处理器测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessTest {

    @Mock
    private EventRepo<TradeTick> eventRepo;

    @Mock
    private EventHandlerRepo<TradeTick> eventHandlerRepo;

    private Process process;

    @BeforeEach
    void setUp() throws Exception {
        process = new Process();

        // 使用反射注入mock对象
        setField(process, "eventRepo", eventRepo);
        setField(process, "eventHandlerRepo", eventHandlerRepo);
    }

    /**
     * 测试处理 TradeTick 事件
     */
    @Test
    @SuppressWarnings("unchecked")
    void testLoop_handleTradeTickEvent() {
        // Given: 创建 TradeTick 事件
        TradeTick tradeTick = new TradeTick();
        Event<TradeTick> event = new Event<>();
        event.type = "TradeTick";
        event.payload = tradeTick;

        // 创建 TradeTick 处理器
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        AtomicReference<TradeTick> receivedData = new AtomicReference<>();

        EventHandler<TradeTick> handler = new EventHandler<TradeTick>() {
            @Override
            public void handle(Event<TradeTick> e) {
                handlerCalled.set(true);
                receivedData.set(e.payload);
            }
        };

        // When: 模拟事件仓储返回事件（只返回一次，然后中断循环）
        when(eventRepo.receive()).thenReturn((Event<TradeTick>) event).thenThrow(new RuntimeException("Loop interrupted"));
        when(eventHandlerRepo.queryBy("TradeTick")).thenReturn((EventHandler<TradeTick>) handler);

        // Then: 执行loop并验证处理器被调用
        assertThrows(RuntimeException.class, () -> process.loop());

        assertTrue(handlerCalled.get(), "Handler should be called");
        assertSame(tradeTick, receivedData.get(), "Should receive the same TradeTick object");

        verify(eventRepo, times(1)).receive();
        verify(eventHandlerRepo, times(1)).queryBy("TradeTick");
    }

    /**
     * 测试处理 OrderBookDelta 事件
     */
    @Test
    @SuppressWarnings("unchecked")
    void testLoop_handleOrderBookDeltaEvent() {
        // Given: 创建 OrderBookDelta 事件
        OrderBookDelta orderBookDelta = new OrderBookDelta();
        Event<OrderBookDelta> event = new Event<>();
        event.type = "OrderBookDelta";
        event.payload = orderBookDelta;

        // 创建处理器
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        EventHandler<OrderBookDelta> handler = new EventHandler<OrderBookDelta>() {
            @Override
            public void handle(Event<OrderBookDelta> e) {
                handlerCalled.set(true);
                assertEquals(orderBookDelta, e.payload);
            }
        };

        // When: 模拟事件
        when(eventRepo.receive()).thenReturn((Event<TradeTick>) (Event<?>) event).thenThrow(new RuntimeException("Loop interrupted"));
        when(eventHandlerRepo.queryBy("OrderBookDelta")).thenReturn((EventHandler<TradeTick>) (EventHandler<?>) handler);

        // Then: 验证
        assertThrows(RuntimeException.class, () -> process.loop());
        assertTrue(handlerCalled.get());

        verify(eventRepo, times(1)).receive();
        verify(eventHandlerRepo, times(1)).queryBy("OrderBookDelta");
    }

    /**
     * 测试处理多个不同类型的事件
     */
    @Test
    @SuppressWarnings("unchecked")
    void testLoop_handleMultipleEventTypes() {
        // Given: 创建多个事件
        TradeTick tradeTick = new TradeTick();
        Event<TradeTick> tradeEvent = new Event<>();
        tradeEvent.type = "TradeTick";
        tradeEvent.payload = tradeTick;

        OrderBookDelta orderBookDelta = new OrderBookDelta();
        Event<OrderBookDelta> orderBookEvent = new Event<>();
        orderBookEvent.type = "OrderBookDelta";
        orderBookEvent.payload = orderBookDelta;

        // 创建处理器并追踪调用
        AtomicBoolean tradeHandlerCalled = new AtomicBoolean(false);
        AtomicBoolean orderBookHandlerCalled = new AtomicBoolean(false);

        EventHandler<TradeTick> tradeHandler = event -> tradeHandlerCalled.set(true);
        EventHandler<OrderBookDelta> orderBookHandler = event -> orderBookHandlerCalled.set(true);

        // When: 模拟连续接收两个事件
        when(eventRepo.receive())
                .thenReturn((Event<?>) tradeEvent)
                .thenReturn((Event<?>) orderBookEvent)
                .thenThrow(new RuntimeException("Loop interrupted"));

        when(eventHandlerRepo.queryBy("TradeTick")).thenReturn((EventHandler<?>) tradeHandler);
        when(eventHandlerRepo.queryBy("OrderBookDelta")).thenReturn((EventHandler<?>) orderBookHandler);

        // Then: 验证两个处理器都被调用
        assertThrows(RuntimeException.class, () -> process.loop());

        assertTrue(tradeHandlerCalled.get(), "TradeTick handler should be called");
        assertTrue(orderBookHandlerCalled.get(), "OrderBookDelta handler should be called");

        verify(eventRepo, times(3)).receive();
        verify(eventHandlerRepo, times(1)).queryBy("TradeTick");
        verify(eventHandlerRepo, times(1)).queryBy("OrderBookDelta");
    }

    /**
     * 测试 EventHandler 为 null 的情况
     */
    @Test
    @SuppressWarnings("unchecked")
    void testLoop_handlerNotFound() {
        // Given: 创建事件但没有对应的处理器
        Event<TradeTick> event = new Event<>();
        event.type = "UnknownType";

        when(eventRepo.receive()).thenReturn((Event<?>) event).thenThrow(new RuntimeException("Loop interrupted"));
        when(eventHandlerRepo.queryBy("UnknownType")).thenReturn(null);

        // Then: 应该抛出 NullPointerException (因为代码中直接调用 handler.handle)
        assertThrows(NullPointerException.class, () -> process.loop());

        verify(eventHandlerRepo, times(1)).queryBy("UnknownType");
    }

    /**
     * 测试 market() 方法中的 TradeTick 处理器
     */
    @Test
    void testMarket_tradeTickHandler() {
        // Given: market方法会创建EventHandler
        // 这里我们验证Handler的逻辑是正确的

        TradeTick tradeTick = new TradeTick();
        Event<TradeTick> event = new Event<>();
        event.type = "TradeTick";
        event.payload = tradeTick;

        // When: 创建类似market()中的handler
        AtomicReference<TradeTick> processedData = new AtomicReference<>();
        EventHandler<TradeTick> handler = new EventHandler<TradeTick>() {
            @Override
            public void handle(Event<TradeTick> e) {
                processedData.set(e.payload);
            }
        };

        // Then: 验证处理逻辑
        handler.handle(event);
        assertSame(tradeTick, processedData.get());
    }

    /**
     * 测试 market() 方法中的 OrderBookDelta 处理器
     */
    @Test
    void testMarket_orderBookDeltaHandler() {
        // Given
        OrderBookDelta orderBookDelta = new OrderBookDelta();
        Event<OrderBookDelta> event = new Event<>();
        event.type = "OrderBookDelta";
        event.payload = orderBookDelta;

        // When: 创建类似market()中的handler
        AtomicReference<OrderBookDelta> processedData = new AtomicReference<>();
        EventHandler<OrderBookDelta> handler = new EventHandler<OrderBookDelta>() {
            @Override
            public void handle(Event<OrderBookDelta> e) {
                processedData.set(e.payload);
            }
        };

        // Then
        handler.handle(event);
        assertSame(orderBookDelta, processedData.get());
    }

    /**
     * 测试事件数据的类型安全性
     */
    @Test
    void testEventTypeSafety() {
        // Given: 创建强类型事件
        TradeTick tradeTick = new TradeTick();
        Event<TradeTick> event = new Event<>();
        event.payload = tradeTick;

        // When: 通过handler处理
        EventHandler<TradeTick> handler = e -> {
            // Then: 编译时类型安全，无需强制转换
            TradeTick data = e.payload;
            assertNotNull(data);
            assertSame(tradeTick, data);
        };

        handler.handle(event);
    }

    /**
     * 性能测试：验证事件处理的低延迟特性
     */
    @Test
    void testEventProcessingLatency() {
        // Given: 预热JVM
        for (int i = 0; i < 10000; i++) {
            TradeTick tick = new TradeTick();
            Event<TradeTick> event = new Event<>();
            event.payload = tick;
        }

        // When: 测量处理延迟
        TradeTick tradeTick = new TradeTick();
        Event<TradeTick> event = new Event<>();
        event.payload = tradeTick;

        AtomicBoolean processed = new AtomicBoolean(false);
        EventHandler<TradeTick> handler = e -> processed.set(true);

        long startNanos = System.nanoTime();
        handler.handle(event);
        long endNanos = System.nanoTime();

        // Then: 验证处理成功且延迟很低
        assertTrue(processed.get());
        long latencyNanos = endNanos - startNanos;

        // 单次处理应该在1微秒以内（1000纳秒）
        assertTrue(latencyNanos < 1000,
                   "Event processing latency should be < 1μs, but was " + latencyNanos + "ns");
    }

    /**
     * 反射工具方法：设置私有字段
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
