package ItemBro.Order.Chat;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderChatSessionState implements HttpSessionBindingListener {

    public static final String ATTRIBUTE = "orderChatSession";

    private final AtomicBoolean active = new AtomicBoolean(true);

    private final OrderChatWebSocketHandler webSocketHandler;

    public OrderChatSessionState(OrderChatWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public boolean isActive() {
        return active.get();
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        active.set(false);
        webSocketHandler.closeHttpSession(this);
    }
}
