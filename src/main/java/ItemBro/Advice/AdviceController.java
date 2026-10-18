package ItemBro.Advice;

import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Advice.DTO.AdvicePageDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

@RestController
public class AdviceController {
    private final AdviceService adviceService;
    public AdviceController(AdviceService adviceService) {
        this.adviceService = adviceService;
    }

    @PostMapping("/api/advices")
    public ResponseEntity<AdviceDTO> createAdvice(@Valid @RequestBody AdviceBody adviceBody, Principal principal) {
        AdviceDTO adviceDTO = adviceService.createAdvice(adviceBody, principal);
        URI location = URI.create("api/advices/"+adviceDTO.id());
        return ResponseEntity.created(location).body(adviceDTO);
    }

    @DeleteMapping("/api/advices/{id}")
    public ResponseEntity<Void> deleteAdvice(@PathVariable Long id, Principal principal) {
        adviceService.deleteAdvice(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/advices")
    public ResponseEntity<AdvicePageDTO> getAdvicesByGameId(
            @RequestParam(value = "gameId", required = true) Long gameId,
            @RequestParam(value = "page", required = false) Integer page) {
        return ResponseEntity.ok(adviceService.getAdvicesByGame(gameId, page));
    }

    @PutMapping("/api/advices/{id}/image")
    public ResponseEntity<AdviceDTO> uploadImage(Principal principal,
                                                 @PathVariable Long id,
                                                 @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adviceService.uploadImage(id, file, principal));
    }
}
