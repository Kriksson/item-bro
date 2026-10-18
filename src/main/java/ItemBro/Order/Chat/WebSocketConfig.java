package ItemBro.Order.Chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderChatHandshakeInterceptor handshakeInterceptor;
    private final OrderChatWebSocketHandler webSocketHandler;
    public WebSocketConfig(OrderChatHandshakeInterceptor handshakeInterceptor,
                           OrderChatWebSocketHandler webSocketHandler) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/api/orders/{id}/chat/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                );
    }
}
