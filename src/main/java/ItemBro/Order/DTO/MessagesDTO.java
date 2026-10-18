package ItemBro.Order.DTO;

import java.util.List;

public record MessagesDTO(List<MessageDTO> items, boolean hasMore)  {
}
