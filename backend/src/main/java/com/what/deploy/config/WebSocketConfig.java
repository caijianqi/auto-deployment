package com.what.deploy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    private final @NonNull LogWebSocketHandler logWebSocketHandler;
    private final @NonNull NginxLogWebSocketHandler nginxLogWebSocketHandler;
    private final @NonNull TerminalWebSocketHandler terminalWebSocketHandler;

    @Autowired
    public WebSocketConfig(@NonNull LogWebSocketHandler logWebSocketHandler, 
                           @NonNull NginxLogWebSocketHandler nginxLogWebSocketHandler,
                           @NonNull TerminalWebSocketHandler terminalWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
        this.nginxLogWebSocketHandler = nginxLogWebSocketHandler;
        this.terminalWebSocketHandler = terminalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        
        // 1. 应用日志 (使用注入的 Bean)
        registry.addHandler(logWebSocketHandler, "/api/ws/logs/**")
                .addInterceptors(new LogHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
        
        // 2. Nginx 日志 (包含绝对路径和通配符兼容)
        registry.addHandler(nginxLogWebSocketHandler, "/api/ws/nginx-log/access", "/api/ws/nginx-log/error", "/api/ws/nginx-log/**")
                .addInterceptors(new NginxLogHandshakeInterceptor())
                .setAllowedOriginPatterns("*");

        // 3. Web 交互式终端 (Windows / Linux)
        registry.addHandler(terminalWebSocketHandler, "/api/ws/terminal", "/api/ws/terminal/**")
                .setAllowedOriginPatterns("*");
    }

    private static class LogHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(@NonNull ServerHttpRequest request, 
                                     @NonNull ServerHttpResponse response, 
                                     @NonNull WebSocketHandler wsHandler, 
                                     @NonNull Map<String, Object> attributes) {
            String path = request.getURI().getPath();
            // 鲁棒性提取：获取最后一个斜杠后的内容
            String appId = path.substring(path.lastIndexOf("/") + 1);
            attributes.put("appId", appId);
            return true;
        }

        @Override
        public void afterHandshake(@NonNull ServerHttpRequest request, 
                                 @NonNull ServerHttpResponse response, 
                                 @NonNull WebSocketHandler wsHandler, 
                                 @Nullable Exception exception) {
            if (exception != null) {
                System.err.println("WebSocket Handshake Failed: " + exception.getMessage());
            }
        }
    }

    private static class NginxLogHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(@NonNull ServerHttpRequest request, 
                                     @NonNull ServerHttpResponse response, 
                                     @NonNull WebSocketHandler wsHandler, 
                                     @NonNull Map<String, Object> attributes) {
            String path = request.getURI().getPath();
            String logType = path.substring(path.lastIndexOf("/") + 1);
            attributes.put("logType", logType);
            return true;
        }

        @Override
        public void afterHandshake(@NonNull ServerHttpRequest request, 
                                 @NonNull ServerHttpResponse response, 
                                 @NonNull WebSocketHandler wsHandler, 
                                 @Nullable Exception exception) {
        }
    }
}
