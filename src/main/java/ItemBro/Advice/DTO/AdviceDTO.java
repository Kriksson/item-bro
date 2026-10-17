package ItemBro.Advice.DTO;

import ItemBro.Advice.Advice;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.User;

import java.math.BigDecimal;

public record AdviceDTO(Long id, String name, GameDTO game, UserSellerDTO userSeller, BigDecimal price,
                        String imageUrl) {
    public AdviceDTO(Advice advice, Game game, User userSeller) {
        this(advice.getId(), advice.getName(), new GameDTO(game), new UserSellerDTO(userSeller),
                advice.getPrice(), advice.getImageUrl());
    }
}
