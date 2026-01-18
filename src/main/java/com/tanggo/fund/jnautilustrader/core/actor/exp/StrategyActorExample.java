package com.tanggo.fund.jnautilustrader.core.actor.exp;

import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor;
import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor.*;

/**
 * StrategyActor 使用示例 - 演示状态管理抽象的用法
 */
public class StrategyActorExample {

    public static void main(String[] args) {
        // 1. 示例1: 使用简单状态的Actor
        System.out.println("=== 示例1: 使用简单状态的Actor ===");
        exampleWithSimpleState();

        System.out.println("\n=== 示例2: 使用自定义状态管理器的Actor ===");
        exampleWithCustomState();

        System.out.println("\n=== 示例3: 使用复杂状态对象的Actor ===");
        exampleWithComplexState();
    }

    /**
     * 示例1: 使用简单状态（Integer类型）
     */
    private static void exampleWithSimpleState() {
        // 定义消息处理策略
        MessageHandler<String, Integer> handler = (message, state) -> {
            Integer currentState = state.getState();
            System.out.println("处理消息: " + message + ", 当前状态: " + currentState);

            // 根据消息更新状态
            if (message.equals("increment")) {
                state.setState(currentState + 1);
            } else if (message.equals("decrement")) {
                state.setState(currentState - 1);
            } else if (message.equals("reset")) {
                state.reset();
            }

            System.out.println("处理后状态: " + state.getState());
        };

        // 创建Actor（初始状态为0）
        StrategyActor<String, Integer> actor = new StrategyActor<>(handler, 0);
        actor.start();

        // 发送消息
        actor.tell("increment");
        actor.tell("increment");
        actor.tell("decrement");

        // 停止Actor
        actor.close();
    }

    /**
     * 示例2: 使用自定义状态管理器
     */
    private static void exampleWithCustomState() {
        // 自定义状态管理器
        State<String> customState = new State<>() {
            private String value = "初始状态";
            private int counter = 0;

            @Override
            public String getState() {
                return value + " (计数: " + counter + ")";
            }

            @Override
            public void setState(String state) {
                this.value = state;
                this.counter++;
            }

            @Override
            public void initialize() {
                System.out.println("自定义状态管理器初始化");
                this.counter = 0;
            }

            @Override
            public void reset() {
                System.out.println("自定义状态管理器重置");
                this.value = "重置状态";
                this.counter = 0;
            }

            @Override
            public void persist() throws Exception {
                System.out.println("自定义状态持久化: " + getState());
            }

            @Override
            public void load() throws Exception {
                System.out.println("自定义状态加载");
            }

            @Override
            public boolean supportsPersistence() {
                return false; // 自定义状态管理器不支持持久化
            }
        };

        // 消息处理策略
        MessageHandler<String, String> handler = (message, state) -> {
            System.out.println("处理消息: " + message + ", 当前状态: " + state.getState());

            if (message.startsWith("set:")) {
                String newValue = message.substring(4);
                state.setState(newValue);
            } else if (message.equals("reset")) {
                state.reset();
            }

            System.out.println("处理后状态: " + state.getState());
        };

        // 创建Actor（使用自定义状态管理器）
        StrategyActor<String, String> actor = new StrategyActor<>(
                handler,
                customState,
                e -> System.err.println("错误: " + e.getMessage())
        );
        actor.start();

        actor.tell("set:新状态1");
        actor.tell("set:新状态2");
        actor.tell("reset");

        actor.close();
    }

    /**
     * 示例3: 使用复杂状态对象
     */
    private static void exampleWithComplexState() {
        // 复杂状态对象
        class TradingState {
            private double balance;
            private int position;
            private boolean isActive;

            public TradingState(double balance, int position, boolean isActive) {
                this.balance = balance;
                this.position = position;
                this.isActive = isActive;
            }

            @Override
            public String toString() {
                return String.format("TradingState{balance=%.2f, position=%d, isActive=%b}",
                        balance, position, isActive);
            }
        }

        // 消息处理策略
        MessageHandler<String, TradingState> handler = (message, state) -> {
            TradingState current = state.getState();
            System.out.println("处理消息: " + message + ", 当前状态: " + current);

            // 根据消息更新交易状态
            if (message.equals("start")) {
                state.setState(new TradingState(current.balance, current.position, true));
            } else if (message.equals("stop")) {
                state.setState(new TradingState(current.balance, current.position, false));
            } else if (message.startsWith("deposit:")) {
                double amount = Double.parseDouble(message.substring(8));
                state.setState(new TradingState(current.balance + amount, current.position, current.isActive));
            } else if (message.startsWith("trade:")) {
                int quantity = Integer.parseInt(message.substring(6));
                if (current.isActive) {
                    state.setState(new TradingState(current.balance, current.position + quantity, current.isActive));
                } else {
                    System.out.println("交易系统未激活，无法交易");
                }
            }

            System.out.println("处理后状态: " + state.getState());
        };

        // 创建Actor（初始状态：1000.00余额，0仓位，未激活）
        StrategyActor<String, TradingState> actor = new StrategyActor<>(
                handler,
                new TradingState(1000.00, 0, false)
        );
        actor.start();

        actor.tell("deposit:500");
        actor.tell("start");
        actor.tell("trade:10");
        actor.tell("trade:-5");
        actor.tell("stop");

        actor.close();
    }
}
