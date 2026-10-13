package ItemBro.User.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserBody(
        @NotBlank @Size(max=20) String name,
        @NotBlank @Size(min = 8, max=64)@Pattern(regexp = "^[A-Za-z0-9!@#$%^&*()_+\\-=]+$") String password) {
}
