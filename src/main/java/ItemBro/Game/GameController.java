package ItemBro.Game;

import ItemBro.Game.DTO.GameDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;

@RestController
public class GameController {

    private final GameService gameService;
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/api/games")
    public ResponseEntity<GameDTO> createGame(@Valid @RequestBody GameBody gameBody, Principal principal) {
        GameDTO gameDTO = gameService.createGame(principal, gameBody);
        URI location = URI.create("/api/games/"+gameDTO.id());
        return ResponseEntity.created(location).body(gameDTO);
    }
}
