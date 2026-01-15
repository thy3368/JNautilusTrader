package com.tanggo.fund.jnautilustrader.adapter.http;

import com.tanggo.fund.jnautilustrader.adapter.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final BlockingQueueEventRepo<TradeTick> eventRepo;

    public EventController(BlockingQueueEventRepo<TradeTick> eventRepo) {
        this.eventRepo = eventRepo;
    }


    /**
     * 接收事件
     */
    @PostMapping("/trade-tick")
    public ResponseEntity<String> receiveEvent(@RequestBody Event<TradeTick> event) {
        if (event == null || event.payload == null) {
            return ResponseEntity.badRequest().body("Invalid event: event or data cannot be null");
        }

        boolean success = eventRepo.send(event);
        if (success) {
            return ResponseEntity.ok("Event received successfully. Queue size: " + eventRepo.getQueueSize());
        } else {
            return ResponseEntity.status(503).body("Event queue is full");
        }
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Event queue size: " + eventRepo.getQueueSize());
    }
}
