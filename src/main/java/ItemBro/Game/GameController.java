package ItemBro.Game;

import ItemBro.Game.DTO.GameDTO;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.List;

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

    @PatchMapping("/api/games/{id}/delete")
    public ResponseEntity<Void> patch(Principal principal, @PathVariable("id") Long id) {
        gameService.deleteGame(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/games")
    public ResponseEntity<List<GameDTO>> getAllGames() {
        return ResponseEntity.ok(gameService.getOpenedGames());
    }

    @PutMapping("/api/games/{id}/image")
    public ResponseEntity<GameDTO> uploadImage(@PathVariable("id") Long id, @RequestPart("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(gameService.uploadImage(id, file));
    }
}
