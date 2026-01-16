package com.tanggo.fund.jnautilustrader.adapter.http;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * WebSocket事件控制器
 */
@Controller
public class WebSocketEventController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventController.class);

    private final BlockingQueueEventRepo<TradeTick> eventRepo;

    @Autowired
    public WebSocketEventController(BlockingQueueEventRepo<TradeTick> eventRepo) {
        this.eventRepo = eventRepo;
    }

    /**
     * 接收客户端发送的事件
     */
    @MessageMapping("/sendEvent")
    @SendTo("/topic/events")
    public Event<TradeTick> receiveEvent(Event<TradeTick> event) {
        logger.info("Received event via WebSocket: type={}", event.type);
        eventRepo.send(event);
        return event;
    }


}
