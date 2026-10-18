package ItemBro.Order;

import ItemBro.Order.DTO.MessageDTO;

public record AddMessageResult(MessageDTO message, boolean result) {
}
