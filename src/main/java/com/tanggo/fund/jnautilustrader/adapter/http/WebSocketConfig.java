package com.tanggo.fund.jnautilustrader.adapter.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于广播消息到客户端
        config.enableSimpleBroker("/topic");
        // 设置应用程序前缀，用于客户端发送消息到服务器
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，允许客户端连接
        registry.addEndpoint("/events")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // 启用SockJS支持，提供备选传输方式
    }
}