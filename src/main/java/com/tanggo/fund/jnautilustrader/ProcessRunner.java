package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.core.process.Process;

/**
 * 应用程序入口类
 * 用于启动 Process 和 Avellaneda-Stoikov 策略
 */
public class ProcessRunner {

    public static void main(String[] args) {
        System.out.println("启动 JNautilusTrader 应用程序...");

        // 创建 Process 实例
        Process process = new Process();

        // 初始化 Process
        System.out.println("正在初始化 Process...");
        process.init();

        // 启动 Process 循环
        System.out.println("正在启动 Process 循环...");
        try {
            process.loop();
        } catch (Exception e) {
            System.err.println("Process 执行过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("应用程序结束。");
    }
}