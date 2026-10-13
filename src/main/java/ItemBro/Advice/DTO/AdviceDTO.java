package ItemBro.Advice.DTO;

import java.math.BigDecimal;

public record AdviceDTO(String name, Long game_id, Long seller_id, BigDecimal price) {
}
