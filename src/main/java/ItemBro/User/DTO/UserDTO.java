package ItemBro.User.DTO;

import ItemBro.User.UserRole;

import java.math.BigDecimal;

public record UserDTO(String name, UserRole userRole, BigDecimal balance) {
}
