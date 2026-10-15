package ItemBro.Order.DTO;

import jakarta.validation.constraints.*;

public record OrderFeedbackBody(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max=300) String feedbackText) {
}
