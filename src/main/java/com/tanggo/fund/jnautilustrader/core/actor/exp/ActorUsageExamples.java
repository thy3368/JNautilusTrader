package com.tanggo.fund.jnautilustrader.core.actor.exp;


import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor;

/**
 * 非继承Actor实现方式的使用示例
 */
public class ActorUsageExamples {

    public static void main(String[] args) {


        System.out.println("\n=== 演示方案3：StrategyActor（策略模式） ===");
        demoStrategyActor();
    }


    /**
     * 演示StrategyActor（策略模式）的使用
     */
    private static void demoStrategyActor() {
        // 1. 先创建消息处理器（避免内部类访问未初始化变量）
        StrategyActor.MessageHandler<Object, String> messageHandler = new StrategyActor.MessageHandler<Object, String>() {
            @Override
            public void handle(Object message, StrategyActor.State<String> state) throws Exception {
                System.out.println("处理消息: " + message + ", 当前状态: " + state.getState());

                // 示例：处理请求-响应消息
                if (message instanceof RequestMessage request) {
                    String response = "Pong from StrategyActor!";
                    // 注意：这里无法直接访问外部actor变量，因为会有初始化问题
                    // 如果需要回复功能，需要通过其他方式传递actor引用
                }

                // 更新状态
                state.setState("处理消息: " + message);
            }
        };

        StrategyActor.ErrorHandler errorHandler = new StrategyActor.ErrorHandler() {
            @Override
            public void handle(Exception e) {
                System.err.println("错误处理: " + e.getMessage());
            }
        };

        // 2. 使用策略接口创建Actor（处理Object类型的消息）
        StrategyActor<Object, String> actor = new StrategyActor<>(messageHandler, "初始状态", errorHandler);

        // 3. 启动Actor
        actor.start();

        // 4. 发送消息
        actor.tell("Hello from StrategyActor!");
        actor.tell("这是另一个测试消息");

        // 5. 发送请求-响应消息（注意：由于内部类访问外部变量问题，这里不演示reply功能）
        try {
            DefaultRequestMessage request = new DefaultRequestMessage("Ping");
            // 由于内部类访问外部变量的限制，这里的ask可能不会收到回复
            String response = actor.ask(request, String.class, 1000);
            System.out.println("收到响应: " + response);
        } catch (InterruptedException e) {
            System.err.println("请求超时");
        }

        // 6. 关闭Actor
        actor.close();
    }


}
