package com.tanggo.fund.jnautilustrader.core.actor;



import java.util.function.Consumer;

/**
 * 函数式Actor构建器
 */
public class ActorBuilder {

    public static <T> MessageActor<T> create(
            Consumer<T> messageHandler,
            Consumer<Exception> errorHandler) {

        return new AbstractActor<T>() {
            @Override
            protected void processMessage(T message) throws Exception {
                messageHandler.accept(message);
            }

            @Override
            protected void handleError(Exception e) {
                errorHandler.accept(e);
            }
        };
    }

    public static <T> MessageActor<T> create(Consumer<T> messageHandler) {
        return create(messageHandler, e -> {
            System.err.println("Actor错误: " + e.getMessage());
        });
    }
}
