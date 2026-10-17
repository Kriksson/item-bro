package ItemBro.User;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateBody(
        @Min(value = 1, message = "Возраст строго от 1 года")
        @Max(value = 120, message = "Возраст строго до 120 лет.") Integer age,
        @Size(max=100, message = "Максимум 100 символов") String city,
        @Size(max=1000, message = "Максимум 1000 символов") String about) {
    public UserUpdateBody {
        if (city != null) {
            city = city.trim();
            if (city.isEmpty()) {
                city = null;
            }
        }

        if (about != null) {
            about = about.trim();
            if (about.isEmpty()) {
                about = null;
            }
        }
    }
}
