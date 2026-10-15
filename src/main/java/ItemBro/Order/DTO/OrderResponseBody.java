package ItemBro.Order.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderResponseBody(
        @NotBlank @Size(max=300) String responseText) {
}
