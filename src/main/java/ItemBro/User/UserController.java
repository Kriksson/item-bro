package ItemBro.User;

import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Exception.UserAlreadyRegisterException;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.DTO.UserDTO;
import ItemBro.User.DTO.UserInfoDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

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
        UserDTO userDTO = new UserDTO(userFromService.getUsername(), userFromService.getRole(), userFromService.getBalance());
        return ResponseEntity.created(location).body(userDTO);
    }

    @GetMapping("/api/user/me")
    public ResponseEntity<UserDTO> getMe(Principal principal) {
        return ResponseEntity.ok(userService.getMe(principal));
    }

    @GetMapping("/api/users/{username}")
    public ResponseEntity<UserInfoDTO> getUserInfo(@PathVariable String username) {
        return  ResponseEntity.ok(userService.getUserInfo(username));
    }

    @GetMapping("/api/users/{username}/advices")
    public ResponseEntity<List<AdviceDTO>> getUserAdvices(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserAdvices(username));
    }

    @GetMapping("/api/users/{username}/reviews")
    public ResponseEntity<List<OrderReviewDTO>> getUserReviews(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserOrderReviews(username));
    }
}
