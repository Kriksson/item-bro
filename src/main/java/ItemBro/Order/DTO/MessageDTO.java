package ItemBro.Order.DTO;

import ItemBro.Order.EventType;
import ItemBro.Order.MessageType;
import ItemBro.Order.Order;
import ItemBro.Order.OrderMessage;
import ItemBro.User.DTO.UserSellerDTO;

import java.time.Instant;

public record MessageDTO(Long id, Long orderId, MessageType messageType, EventType eventType, UserSellerDTO sender, String text, Instant sentAt) {
    public MessageDTO(OrderMessage orderMessage, Order order) {
        this(orderMessage.getId(), order.getId(), orderMessage.getMessageType(), orderMessage.getEventType(),
                new UserSellerDTO(orderMessage.getSender()), orderMessage.getText(), orderMessage.getSentAt());
    }
}
