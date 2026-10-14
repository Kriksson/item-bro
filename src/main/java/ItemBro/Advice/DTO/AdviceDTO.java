package ItemBro.Advice.DTO;

import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.User.DTO.UserSellerDTO;

import java.math.BigDecimal;

public record AdviceDTO(Long id, String name, GameDTO game, UserSellerDTO userSeller, BigDecimal price) {
}
