package ItemBro.User.DTO;

import ItemBro.User.User;
import ItemBro.User.UserRole;

public record UserInfoDTO(String username, UserRole userRole, String avatarUrl, Integer age, String city, String about) {
    public UserInfoDTO(User user) {
        this(user.getUsername(), user.getRole(), user.getAvatarUrl(), user.getAge(), user.getCity(), user.getAbout());
    }
}
