package com.tanggo.fund.jnautilustrader.core.actor;

/**
 * 通用Actor接口
 * 简化版本，适合大多数应用场景
 */
public interface MessageActor<T> extends Runnable, AutoCloseable {

    /**
     * 发送消息到Actor
     */
    void tell(T message);

    /**
     * 发送消息并等待响应
     */
    default <R> R ask(T message, Class<R> responseType, long timeoutMs)
            throws InterruptedException {
        // 实现请求-响应模式
        throw new UnsupportedOperationException("需要具体实现");
    }

    /**
     * 启动Actor的消息处理循环
     */
    void start();

    /**
     * 停止Actor
     */
    @Override
    void close();

    /**
     * 获取Actor状态
     */
    ActorStatus getStatus();

    /**
     * Actor状态枚举
     */
    enum ActorStatus {
        IDLE, RUNNING, STOPPED, FAILED
    }
}
