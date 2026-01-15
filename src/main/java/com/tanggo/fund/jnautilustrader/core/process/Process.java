package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandler;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;

public class Process {

    private EventRepo<TradeTick> eventRepo;
    private EventHandlerRepo<TradeTick> eventHandlerRepo;


    void init() {

        eventRepo = new EventRepo<TradeTick>() {
            @Override
            public Event<TradeTick> receive() {
                return null;
            }
        };

        eventHandlerRepo = new EventHandlerRepo<TradeTick>() {
            @Override
            public EventHandler<TradeTick> queryBy(String type) {
                return null;
            }
        };

    }

    void loop() {


        while (true) {

            // 使用通配符接收任意类型的事件
            Event<TradeTick> event = eventRepo.receive();
            EventHandler<TradeTick> eventHandler = eventHandlerRepo.queryBy(event.type);

            eventHandler.handle(event);


        }


    }


    void market() {


        EventHandler<TradeTick> eventHandler = new EventHandler<TradeTick>() {

            @Override
            public void handle(Event<TradeTick> event) {

                TradeTick tradeTick = event.payload;

            }
        };


        EventHandler<OrderBookDelta> eventHandler2 = new EventHandler<OrderBookDelta>() {

            @Override
            public void handle(Event<OrderBookDelta> event) {

                OrderBookDelta orderBookDelta = event.payload;

            }
        };


    }

}
