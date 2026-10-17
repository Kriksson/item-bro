package ItemBro.User.DTO;

import ItemBro.User.UserRole;
import ItemBro.User.User;

public record UserBuyerDTO(String username, UserRole userRole) {
    public UserBuyerDTO(User user) {
        this(user.getUsername(), user.getRole());
    }
}
