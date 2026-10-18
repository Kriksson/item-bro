package ItemBro.Order.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MessageBody(
        @NotBlank @Size(max=1000) String text,
        @NotNull UUID clientMessageId) {
}
