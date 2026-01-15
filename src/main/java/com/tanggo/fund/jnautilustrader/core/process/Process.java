package com.tanggo.fund.jnautilustrader.core.process;

import lombok.Data;
import org.springframework.stereotype.Service;

//todo 实现 Avellaneda-Stoikov 策略 基于币安websocket

@Service
@Data
public class Process {


    private AvellanedaStoikovStrategy strategy;

    private StrategyRepo strategyRepo;

    public void init() {


    }

    public void loop() {

        Strategy strategy = strategyRepo.query("AvellanedaStoikovStrategy");
        System.out.println("Process 初始化成功，策略已创建");

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


}
