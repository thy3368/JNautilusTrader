package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.core.entity.*;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;

//todo 实现 Avellaneda-Stoikov 策略 基于币安websocket
public class Process {

    private EventRepo<MarketData> eventRepo;
    private EventRepo<TradeCmd> eventRepo2;
    private EventHandlerRepo<MarketData> eventHandlerRepo;

    private AvellanedaStoikovStrategy strategy;

    public void init() {

        eventRepo = new EventRepo<MarketData>() {
            @Override
            public Event<MarketData> receive() {
                return null;
            }
        };

        eventRepo2 = new EventRepo<TradeCmd>() {
            @Override
            public Event<TradeCmd> receive() {
                return null;
            }
        };

        eventHandlerRepo = new EventHandlerRepo<MarketData>() {
            @Override
            public EventHandler<MarketData> queryBy(String type) {
                return null;
            }
        };

        // 初始化 Avellaneda-Stoikov 策略
        strategy = new AvellanedaStoikovStrategy(eventRepo, eventRepo2, eventHandlerRepo);

        System.out.println("Process 初始化成功，策略已创建");
    }

    public void loop() {
        // 启动策略
        strategy.start();

        while (true) {
            try {
                // 策略已经在独立线程中运行，这里保持主线程运行
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Loop 线程被中断");
                break;
            }
        }

        // 停止策略
        strategy.stop();
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
