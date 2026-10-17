package ItemBro.User.DTO;

import ItemBro.User.UserRole;
import ItemBro.User.User;

public record UserSellerDTO(String username, UserRole userRole) {
    public UserSellerDTO(User user) {
        this(user.getUsername(), user.getRole());
    }
}
