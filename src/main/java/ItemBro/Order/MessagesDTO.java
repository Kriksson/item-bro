package ItemBro.Order;

import ItemBro.Order.DTO.MessageDTO;

import java.util.List;

public record MessagesDTO(List<MessageDTO> items, boolean hasMore)  {
}
