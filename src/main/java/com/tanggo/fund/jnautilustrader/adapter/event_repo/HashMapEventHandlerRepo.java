package com.tanggo.fund.jnautilustrader.adapter.event_repo;

import com.tanggo.fund.jnautilustrader.core.entity.EventHandler;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件处理器仓储实现 - 使用内存存储事件处理器
 */
@Component
public class HashMapEventHandlerRepo<T> implements EventHandlerRepo<T> {

    private final Map<String, EventHandler<T>> handlers;

    public HashMapEventHandlerRepo() {
        this.handlers = new HashMap<>();
    }

    @Override
    public EventHandler<T> queryBy(String type) {
        return handlers.get(type);
    }

    @Override
    public void addHandler(String type, EventHandler<T> handler) {
        handlers.put(type, handler);
    }
}