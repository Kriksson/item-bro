package ItemBro.User.DTO;

import ItemBro.User.User;
import ItemBro.User.UserRole;

import java.math.BigDecimal;

public record UserDTO(String name, UserRole userRole, BigDecimal balance, String avatarUrl,
                      Integer age, String city, String about) {
    public UserDTO(User user) {
        this(user.getUsername(), user.getRole(), user.getBalance(), user.getAvatarUrl(),
                user.getAge(), user.getCity(), user.getAbout());
    }
}
