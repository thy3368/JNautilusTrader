package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.core.process.AvellanedaStoikovParams;
import com.tanggo.fund.jnautilustrader.core.process.AvellanedaStoikovStrategy;
import com.tanggo.fund.jnautilustrader.core.process.Process;

import java.util.Scanner;

/**
 * 策略测试类
 * 提供交互式命令来测试 Avellaneda-Stoikov 策略
 */
public class StrategyTester {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Process process = new Process();

        System.out.println("JNautilusTrader 策略测试工具");
        System.out.println("==============================");

        // 初始化 Process
        System.out.println("\n1. 初始化 Process...");
        process.init();

        // 显示菜单
        while (true) {
            System.out.println("\n请选择操作:");
            System.out.println("1. 启动策略循环");
            System.out.println("2. 查看策略状态");
            System.out.println("3. 停止策略");
            System.out.println("4. 退出");
            System.out.print("请输入选择: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // 消耗换行符

            switch (choice) {
                case 1:
                    System.out.println("启动策略循环...");
                    startStrategyLoop(process);
                    break;

                case 2:
                    System.out.println("策略正在运行中...");
                    break;

                case 3:
                    System.out.println("停止策略...");
                    break;

                case 4:
                    System.out.println("正在清理资源...");
                    System.exit(0);

                default:
                    System.out.println("无效选择，请重新输入");
                    break;
            }
        }
    }

    /**
     * 启动策略循环线程
     */
    private static void startStrategyLoop(Process process) {
        Thread loopThread = new Thread(() -> {
            try {
                process.loop();
            } catch (Exception e) {
                System.err.println("策略执行出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
        loopThread.start();

        System.out.println("策略循环已启动，按 Ctrl+C 停止");
    }
}