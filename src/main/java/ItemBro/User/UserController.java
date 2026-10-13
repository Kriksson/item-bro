package ItemBro.User;

import ItemBro.Exception.UserAlreadyRegisterException;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.DTO.UserDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/user")
    public ResponseEntity<UserDTO> registerUser(@Valid @RequestBody UserBody user) {
        User userFromService = userService.createUser(user);
        URI location = URI.create("/api/user/" + userFromService.getId());
        UserDTO userDTO = new UserDTO(userFromService.getUsername(), userFromService.getBalance());
        return ResponseEntity.created(location).body(userDTO);
    }

}
