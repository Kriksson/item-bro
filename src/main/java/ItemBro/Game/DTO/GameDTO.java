package ItemBro.Game.DTO;

import ItemBro.Game.Game;

public record GameDTO(Long id, String name, String imageUrl) {
    public GameDTO(Game game) {
        this(game.getId(), game.getName(), game.getImageUrl());
    }
}
