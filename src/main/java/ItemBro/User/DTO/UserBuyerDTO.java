package ItemBro.User.DTO;

import ItemBro.User.UserRole;
import ItemBro.User.User;

public record UserBuyerDTO(String username, UserRole userRole, String avatarUrl) {
    public UserBuyerDTO(User user) {
        this(user.getUsername(), user.getRole(), user.getAvatarUrl());
    }
}
