package com.tanggo.fund.jnautilustrader.core.actor.exp;


import com.tanggo.fund.jnautilustrader.core.actor.StrategyActor;
import java.util.concurrent.atomic.AtomicReference;

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
        // 使用 AtomicReference 来允许内部类修改和访问外部变量
        final AtomicReference<StrategyActor<Object, String>> actorRef = new AtomicReference<>();

        // 1. 先创建消息处理器（避免内部类访问未初始化变量）
        StrategyActor.MessageHandler<Object, String> messageHandler = new StrategyActor.MessageHandler<Object, String>() {
            @Override
            public void handle(Object message, StrategyActor.State<String> state) throws Exception {
                System.out.println("处理消息: " + message + ", 当前状态: " + state.getState());

                // 示例：处理请求-响应消息
                if (message instanceof RequestMessage request) {
                    String response = "Pong from StrategyActor!";
                    // 使用 AtomicReference 访问 actor 引用
                    StrategyActor<Object, String> actor = actorRef.get();
                    if (actor != null) {
                        actor.reply(request.getRequestId(), response);
                    }
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
        actorRef.set(actor);  // 初始化完成后设置引用

        // 3. 启动Actor
        actor.start();

        // 4. 发送消息
        actor.tell("Hello from StrategyActor!");
        actor.tell("这是另一个测试消息");

        // 5. 发送请求-响应消息（现在可以正常工作）
        try {
            DefaultRequestMessage request = new DefaultRequestMessage("Ping");
            String response = actor.ask(request, String.class, 1000);
            System.out.println("收到响应: " + response);
        } catch (InterruptedException e) {
            System.err.println("请求超时");
        }

        // 6. 关闭Actor
        actor.close();
    }


}
