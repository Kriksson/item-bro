package ItemBro.Order.Chat;

import ItemBro.Order.DTO.MessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderMessageCreatedListener {

    private final OrderChatWebSocketHandler webSocketHandler;
    public OrderMessageCreatedListener(OrderChatWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderMessageCreatedEvent(OrderMessageCreatedEvent event) {
        webSocketHandler.sendMessage(event.message());
    }
}
