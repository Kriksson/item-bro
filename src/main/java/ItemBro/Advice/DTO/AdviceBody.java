package ItemBro.Advice.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdviceBody(
        @NotBlank @Size(max=50) String name,
        @NotNull Long game_id,
        @NotNull @Min(1)BigDecimal price) {
}
