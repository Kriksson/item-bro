package ItemBro.Game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GameBody(
        @NotBlank @Size(max=50) String name) {
}
