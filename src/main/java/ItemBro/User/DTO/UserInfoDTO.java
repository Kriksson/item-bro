package ItemBro.User.DTO;

import ItemBro.User.User;
import ItemBro.User.UserRole;

public record UserInfoDTO(String username, UserRole userRole) {
    public UserInfoDTO(User user) {
        this(user.getUsername(), user.getRole());
    }
}
