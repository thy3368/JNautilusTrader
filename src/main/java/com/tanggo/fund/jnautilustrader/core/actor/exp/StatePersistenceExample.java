package com.tanggo.fund.jnautilustrader.core.actor.exp;

import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.*;

import java.io.Serializable;

/**
 * 策略状态持久化使用示例
 * 演示如何使用 StrategyActor 的状态持久化功能
 */
public class StatePersistenceExample {

    public static void main(String[] args) {
        System.out.println("=== StrategyActor 状态持久化示例 ===");

        // 演示文件系统持久化
        demonstrateFilePersistence();

        System.out.println("\n=== 演示完毕 ===");
    }

    /**
     * 演示文件系统持久化功能
     */
    private static void demonstrateFilePersistence() {
        System.out.println("\n--- 演示文件系统持久化 ---");

        // 创建可序列化的状态类
        class TradingState implements Serializable {
            private static final long serialVersionUID = 1L;
            private String symbol;
            private double price;
            private int quantity;
            private boolean active;

            public TradingState(String symbol, double price, int quantity, boolean active) {
                this.symbol = symbol;
                this.price = price;
                this.quantity = quantity;
                this.active = active;
            }

            public void updatePrice(double newPrice) {
                this.price = newPrice;
            }

            public void updateQuantity(int newQuantity) {
                this.quantity = newQuantity;
            }

            public void activate() {
                this.active = true;
            }

            public void deactivate() {
                this.active = false;
            }

            @Override
            public String toString() {
                return String.format("TradingState{symbol='%s', price=%.2f, quantity=%d, active=%s}",
                        symbol, price, quantity, active);
            }
        }

        // 定义消息处理策略
        MessageHandler<String, TradingState> messageHandler = (message, state) -> {
            TradingState currentState = state.getState();
            System.out.println("收到消息: " + message);
            System.out.println("当前状态: " + currentState);

            // 根据消息更新状态
            if (message.startsWith("UPDATE_PRICE:")) {
                double newPrice = Double.parseDouble(message.substring("UPDATE_PRICE:".length()));
                currentState.updatePrice(newPrice);
                state.setState(currentState);
            } else if (message.startsWith("UPDATE_QUANTITY:")) {
                int newQuantity = Integer.parseInt(message.substring("UPDATE_QUANTITY:".length()));
                currentState.updateQuantity(newQuantity);
                state.setState(currentState);
            } else if (message.equals("ACTIVATE")) {
                currentState.activate();
                state.setState(currentState);
            } else if (message.equals("DEACTIVATE")) {
                currentState.deactivate();
                state.setState(currentState);
            } else if (message.equals("PRINT_STATE")) {
                System.out.println("状态检查: " + state.getState());
            }

            System.out.println("处理后状态: " + state.getState());
            System.out.println("---");
        };

        // 错误处理策略
        ErrorHandler errorHandler = e -> {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        };

        // 创建文件持久化器
        StatePersister<TradingState> filePersister = new FilePersister<>("trading-state.ser", TradingState.class);

        // 创建支持持久化的 StrategyActor
        StrategyActor<String, TradingState> actor = new StrategyActor<>(
                messageHandler,
                new TradingState("BTC-USD", 50000.0, 10, false),
                filePersister,
                errorHandler
        );

        System.out.println("Actor 创建成功，支持持久化: " + actor.supportsPersistence());
        System.out.println("初始状态: " + actor.getState());

        // 启动 Actor
        actor.start();

        try {
            // 发送消息更新状态
            actor.tell("UPDATE_PRICE:51000.50");
            Thread.sleep(500);

            actor.tell("UPDATE_QUANTITY:15");
            Thread.sleep(500);

            actor.tell("ACTIVATE");
            Thread.sleep(500);

            // 检查当前状态
            System.out.println("\n状态持久化后:");
            System.out.println("当前状态: " + actor.getState());

            // 手动触发状态持久化
            System.out.println("\n手动持久化状态...");
            actor.persistState();

            // 停止 Actor
            actor.close();
            System.out.println("Actor 已停止");

            // 重新创建 Actor，测试状态恢复
            System.out.println("\n--- 测试状态恢复 ---");
            StrategyActor<String, TradingState> recoveredActor = new StrategyActor<>(
                    messageHandler,
                    new TradingState("EMPTY", 0.0, 0, false), // 初始状态（会被持久化状态覆盖）
                    filePersister,
                    errorHandler
            );

            System.out.println("恢复后的状态: " + recoveredActor.getState());
            recoveredActor.start();

            // 验证恢复的状态
            recoveredActor.tell("PRINT_STATE");
            Thread.sleep(500);

            // 清理资源
            recoveredActor.close();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 清理持久化文件
        try {
            filePersister.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 演示内存状态持久化（仅用于演示，实际生产环境应使用文件或数据库）
     */
    private static void demonstrateMemoryPersistence() {
        System.out.println("\n--- 演示内存状态持久化 ---");

        class CounterState implements Serializable {
            private static final long serialVersionUID = 1L;
            private int count;

            public CounterState(int initialCount) {
                this.count = initialCount;
            }

            public void increment() {
                count++;
            }

            public void decrement() {
                count--;
            }

            @Override
            public String toString() {
                return String.format("CounterState{count=%d}", count);
            }
        }

        // 内存持久化实现（仅用于演示）
        StatePersister<CounterState> memoryPersister = new StatePersister<>() {
            private CounterState savedState;

            @Override
            public void save(CounterState state) throws Exception {
                System.out.println("保存状态到内存: " + state);
                savedState = state;
            }

            @Override
            public CounterState load() throws Exception {
                System.out.println("从内存加载状态: " + savedState);
                return savedState;
            }

            @Override
            public void delete() throws Exception {
                System.out.println("删除内存中的状态");
                savedState = null;
            }

            @Override
            public boolean exists() {
                return savedState != null;
            }
        };

        // 创建 Actor
        StrategyActor<String, CounterState> actor = new StrategyActor<>(
                (message, state) -> {
                    CounterState currentState = state.getState();
                    System.out.println("收到消息: " + message + ", 当前状态: " + currentState);

                    if (message.equals("INCREMENT")) {
                        currentState.increment();
                        state.setState(currentState);
                    } else if (message.equals("DECREMENT")) {
                        currentState.decrement();
                        state.setState(currentState);
                    }

                    System.out.println("处理后状态: " + state.getState());
                },
                new CounterState(0),
                memoryPersister,
                e -> System.err.println("错误: " + e.getMessage())
        );

        actor.start();

        try {
            actor.tell("INCREMENT");
            Thread.sleep(200);
            actor.tell("INCREMENT");
            Thread.sleep(200);
            actor.tell("DECREMENT");
            Thread.sleep(200);

            // 测试状态恢复
            System.out.println("\n--- 测试内存状态恢复 ---");
            StrategyActor<String, CounterState> recoveredActor = new StrategyActor<>(
                    (m, s) -> {}, // 空处理策略
                    new CounterState(-1),
                    memoryPersister,
                    e -> {}
            );

            System.out.println("恢复的状态: " + recoveredActor.getState());

            actor.close();
            recoveredActor.close();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
