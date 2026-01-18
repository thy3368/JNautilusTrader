package com.tanggo.fund.jnautilustrader.core.actor;

import com.tanggo.fund.jnautilustrader.core.actor.exp.RequestMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * 方案1：组合模式 - 委托式Actor实现
 * 核心Actor功能被封装，通过Consumer函数式接口处理消息
 */
public class DelegatingActor<T> implements MessageActor<T> {

    private final BlockingQueue<T> mailbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile ActorStatus status = ActorStatus.IDLE;

    // 用于存储请求-响应的未来结果
    private final ConcurrentMap<String, CompletableFuture<Object>> responseFutures = new ConcurrentHashMap<>();

    // 消息处理器（通过构造函数注入）
    private final Consumer<T> messageHandler;
    private final Consumer<Exception> errorHandler;

    /**
     * 构造函数
     * @param messageHandler 消息处理函数
     */
    public DelegatingActor(Consumer<T> messageHandler) {
        this(messageHandler, e -> {
            System.err.println("Actor处理消息时出错: " + e.getMessage());
            e.printStackTrace();
        });
    }

    /**
     * 构造函数（含错误处理）
     * @param messageHandler 消息处理函数
     * @param errorHandler 错误处理函数
     */
    public DelegatingActor(Consumer<T> messageHandler, Consumer<Exception> errorHandler) {
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
    }

    @Override
    public void tell(T message) {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }
        mailbox.offer(message);
    }

    @Override
    public <R> R ask(T message, Class<R> responseType, long timeoutMs) throws InterruptedException {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        responseFutures.put(requestId, future);

        try {
            if (message instanceof RequestMessage) {
                ((RequestMessage) message).setRequestId(requestId);
            }
            mailbox.offer(message);

            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (result == null) {
                throw new InterruptedException("响应超时");
            }

            if (!responseType.isInstance(result)) {
                throw new ClassCastException("响应类型不匹配，期望: " + responseType + ", 实际: " + result.getClass());
            }

            return responseType.cast(result);
        } catch (Exception e) {
            responseFutures.remove(requestId);
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送响应
     */
    public void reply(String requestId, Object response) {
        CompletableFuture<Object> future = responseFutures.remove(requestId);
        if (future != null && !future.isDone()) {
            future.complete(response);
        }
    }

    /**
     * 发送响应（通过消息对象）
     */
    public void reply(RequestMessage message, Object response) {
        if (message.getRequestId() != null) {
            reply(message.getRequestId(), response);
        }
    }

    @Override
    public void run() {
        status = ActorStatus.RUNNING;
        running.set(true);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                T message = mailbox.take();
                messageHandler.accept(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                errorHandler.accept(e);
            }
        }

        status = ActorStatus.STOPPED;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this);
        }
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
        status = ActorStatus.STOPPED;
    }

    @Override
    public ActorStatus getStatus() {
        return status;
    }
}