package com.tanggo.fund.jnautilustrader.core.actor;

import com.tanggo.fund.jnautilustrader.core.actor.exp.RequestMessage;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用ActorBuilder快速创建Actor
 */
public class ActorBuilderExample {

    // 用于ask模式的请求消息类
    public static class CalculatorRequest implements RequestMessage {
        private String requestId;
        private int a;
        private int b;
        private Operation operation;

        public CalculatorRequest(int a, int b, Operation operation) {
            this.a = a;
            this.b = b;
            this.operation = operation;
        }

        public int getA() {
            return a;
        }

        public int getB() {
            return b;
        }

        public Operation getOperation() {
            return operation;
        }

        @Override
        public String getRequestId() {
            return requestId;
        }

        @Override
        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    // 计算操作枚举
    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    public static void main(String[] args) throws Exception {
        // 示例1: 简单的消息处理器
        MessageActor<String> stringProcessor = ActorBuilder.create(message -> {
            System.out.println(Thread.currentThread().getName() + " - 处理字符串消息: " + message);
            // 模拟处理时间
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, error -> {
            System.err.println("处理消息时出错: " + error.getMessage());
            error.printStackTrace();
        });

        // 示例2: 带状态的消息处理器
        AtomicLong messageCount = new AtomicLong(0);

        MessageActor<String> statefulProcessor = ActorBuilder.create(message -> {
            long count = messageCount.incrementAndGet();
            System.out.printf("处理第 %d 条消息: %s%n", count, message);

            // 根据消息内容执行不同操作
            if (message.startsWith("ERROR")) {
                throw new RuntimeException("模拟错误: " + message);
            } else if (message.equals("GET_COUNT")) {
                System.out.println("已处理消息总数: " + count);
            }
        }, error -> {
            System.err.println("发生错误，当前计数: " + messageCount.get() + ", 错误: " + error.getMessage());
        });

        // 示例3: 支持ask模式的计算器Actor
        // 注意：需要使用AbstractActor的子类来访问reply方法，或者使用ActorBuilder的自定义实现
        MessageActor<CalculatorRequest> calculatorActor = new AbstractActor<CalculatorRequest>() {
            @Override
            protected void processMessage(CalculatorRequest message) throws Exception {
                System.out.println(Thread.currentThread().getName() + " - 计算请求: " + message.getA() + " " +
                        message.getOperation() + " " + message.getB());

                int result;
                switch (message.getOperation()) {
                    case ADD:
                        result = message.getA() + message.getB();
                        break;
                    case SUBTRACT:
                        result = message.getA() - message.getB();
                        break;
                    case MULTIPLY:
                        result = message.getA() * message.getB();
                        break;
                    case DIVIDE:
                        if (message.getB() == 0) {
                            throw new ArithmeticException("除数不能为0");
                        }
                        result = message.getA() / message.getB();
                        break;
                    default:
                        throw new IllegalArgumentException("未知操作: " + message.getOperation());
                }

                // 发送响应
                reply(message, result);
                System.out.println("计算完成，结果: " + result);
            }
        };

        // 启动Actor
        stringProcessor.start();
        statefulProcessor.start();
        calculatorActor.start();

        // 发送消息
        for (int i = 1; i <= 10; i++) {
            stringProcessor.tell("消息" + i);
            statefulProcessor.tell("数据" + i);
        }

        // 示例4: 使用ask模式发送请求并等待响应
        System.out.println("\n--- 开始使用ask模式进行计算 ---");

        // 加法
        CalculatorRequest addRequest = new CalculatorRequest(10, 5, Operation.ADD);
        Integer addResult = calculatorActor.ask(addRequest, Integer.class, 1000);
        System.out.println("10 + 5 = " + addResult);

        // 乘法
        CalculatorRequest multiplyRequest = new CalculatorRequest(10, 5, Operation.MULTIPLY);
        Integer multiplyResult = calculatorActor.ask(multiplyRequest, Integer.class, 1000);
        System.out.println("10 * 5 = " + multiplyResult);

        // 除法
        CalculatorRequest divideRequest = new CalculatorRequest(10, 5, Operation.DIVIDE);
        Integer divideResult = calculatorActor.ask(divideRequest, Integer.class, 1000);
        System.out.println("10 / 5 = " + divideResult);

        System.out.println("--- ask模式计算完成 ---");

        // 发送特殊消息
        statefulProcessor.tell("ERROR_SIMULATION");
        statefulProcessor.tell("GET_COUNT");

        // 等待处理完成
        Thread.sleep(2000);

        // 关闭Actor
        stringProcessor.close();
        statefulProcessor.close();
        calculatorActor.close();
    }
}
