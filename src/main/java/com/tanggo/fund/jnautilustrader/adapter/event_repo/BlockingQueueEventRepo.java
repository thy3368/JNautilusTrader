package com.tanggo.fund.jnautilustrader.adapter.event_repo;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 事件仓储实现 - 通过HTTP服务器接收事件
 */
@Component
public class BlockingQueueEventRepo<T> implements EventRepo<T> {

    private final BlockingQueue<Event<T>> eventQueue;

    public BlockingQueueEventRepo() {
        this.eventQueue = new LinkedBlockingQueue<>();
    }

    /**
     * 接收事件（从队列中取出）
     */
    @Override
    public Event<T> receive() {
        try {
            // 阻塞等待事件
            return eventQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 发送事件（添加到队列）
     */
    @Override
    public boolean send(Event<T> event) {
        return eventQueue.offer(event);
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
}
