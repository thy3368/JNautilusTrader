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

/**
 * 抽象基础Actor实现
 */
public abstract class AbstractActor<T> implements MessageActor<T> {

    protected final BlockingQueue<T> mailbox = new LinkedBlockingQueue<>();
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected volatile ActorStatus status = ActorStatus.IDLE;

    // 用于存储请求-响应的未来结果
    private final ConcurrentMap<String, CompletableFuture<Object>> responseFutures = new ConcurrentHashMap<>();

    /**
     * 消息处理逻辑（子类必须实现）
     */
    protected abstract void processMessage(T message) throws Exception;

    @Override
    public void tell(T message) {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }
        mailbox.offer(message);
    }

    /**
     * 发送消息并等待响应（实现请求-响应模式）
     */
    @Override
    public <R> R ask(T message, Class<R> responseType, long timeoutMs) throws InterruptedException {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }

        // 生成唯一的请求ID
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        responseFutures.put(requestId, future);

        try {
            // 发送包含请求ID的消息（需要消息类型支持）
            if (message instanceof RequestMessage) {
                ((RequestMessage) message).setRequestId(requestId);
            }
            mailbox.offer(message);

            // 等待响应
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
    protected void reply(String requestId, Object response) {
        CompletableFuture<Object> future = responseFutures.remove(requestId);
        if (future != null && !future.isDone()) {
            future.complete(response);
        }
    }

    /**
     * 发送响应（通过消息对象）
     */
    protected void reply(RequestMessage message, Object response) {
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
                T message = mailbox.take();  // 阻塞直到有消息
                processMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                handleError(e);
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

    protected void handleError(Exception e) {
        // 默认错误处理：记录日志
        System.err.println("Actor处理消息时出错: " + e.getMessage());
        e.printStackTrace();
    }
}
