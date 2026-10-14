package ItemBro.Advice.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdviceIdBody(
        @NotNull @Min(1) Long id) {
}
