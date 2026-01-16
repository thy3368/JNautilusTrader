package com.tanggo.fund.jnautilustrader.adapter.event_repo;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import org.springframework.stereotype.Component;

/**
 * 事件仓储实现 - 通过ParquetFile读写事件
 */
@Component

//todo 通过ParquetFile读写事件
public class ParquetFileQueueEventRepo<T> implements EventRepo<T> {



    /**
     * 接收事件（从队列头中取出直到文件尾）
     */
    @Override
    public Event<T> receive() {

        //todo
        return null;
    }

    /**
     * 发送事件（添加到队列-文件尾）
     */
    @Override
    public boolean send(Event<T> event) {

        //todo
        return false;
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
}
