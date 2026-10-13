package ItemBro.Advice;

import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Advice.DTO.AdviceDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        Advice adviceFromService = adviceService.createAdvice(adviceBody, principal);
        AdviceDTO adviceDTO = new AdviceDTO(adviceFromService.getName(),
                adviceBody.game_id(), adviceFromService.getSeller().getId(), adviceFromService.getPrice());
        URI location = URI.create("api/advices/"+adviceFromService.getId());
        return ResponseEntity.created(location).body(adviceDTO);
    }

    @DeleteMapping("/api/advices/{id}")
    public ResponseEntity<Void> deleteAdvice(@PathVariable Long id, Principal principal) {
        adviceService.deleteAdvice(id, principal);
        return ResponseEntity.noContent().build();
    }
}
