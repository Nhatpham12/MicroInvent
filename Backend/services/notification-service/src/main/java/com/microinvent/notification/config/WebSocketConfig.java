package com.microinvent.notification.config;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.notification.service.NotificationPusher;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * STOMP qua WebSocket tại {@code /ws}. Gateway đã xác thực JWT (cho phép {@code ?access_token=}) và gắn header
 * X-User-Id / X-Tenant-Id vào request bắt tay; ContextHeaderFilter dựng UserContext từ đó. Không có user thì từ
 * chối bắt tay. Client chỉ được SUBSCRIBE {@code /user/queue/**}, không được SEND.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String PRINCIPAL_ATTR = "microinvent.principal";

    private final String[] allowedOrigins;
    private final String defaultTenant;

    public WebSocketConfig(@Value("${microinvent.websocket.allowed-origins:${CORS_ORIGINS:http://localhost:5173}}")
            String[] allowedOrigins, @Value("${microinvent.tenant.default-tenant:tigernet}") String defaultTenant) {
        this.allowedOrigins = allowedOrigins;
        this.defaultTenant = defaultTenant;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .addInterceptors(new UserHandshakeInterceptor(defaultTenant))
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                            Map<String, Object> attributes) {
                        return (Principal) attributes.get(PRINCIPAL_ATTR);
                    }
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new SubscribeOnlyInterceptor());
    }

    /** Lấy người dùng từ UserContext (header gateway) lúc bắt tay; không có thì trả 401. */
    static class UserHandshakeInterceptor implements HandshakeInterceptor {

        private final String defaultTenant;

        UserHandshakeInterceptor(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) {
            UserContext user = UserContextHolder.get();
            if (user.userId() == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            String tenant = TenantContext.get() != null ? TenantContext.get() : defaultTenant;
            String name = NotificationPusher.principalName(tenant, user.userId());
            attributes.put(PRINCIPAL_ATTR, (Principal) () -> name);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Exception exception) {
            // không cần
        }
    }

    /** Chặn client gửi tin hoặc subscribe kênh ngoài hàng đợi riêng của mình. */
    static class SubscribeOnlyInterceptor implements ChannelInterceptor {

        private static final List<String> ALLOWED_PREFIXES = List.of("/user/queue/");

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            StompCommand command = accessor.getCommand();
            if (command == StompCommand.SEND) {
                throw new MessageDeliveryException("Không hỗ trợ gửi tin qua WebSocket");
            }
            if (command == StompCommand.SUBSCRIBE) {
                String dest = accessor.getDestination();
                if (accessor.getUser() == null || dest == null
                        || ALLOWED_PREFIXES.stream().noneMatch(dest::startsWith)) {
                    throw new MessageDeliveryException("Chỉ được subscribe /user/queue/notifications");
                }
            }
            return message;
        }
    }
}
