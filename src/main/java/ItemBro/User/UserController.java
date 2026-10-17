package ItemBro.User;

import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Order.DTO.OrderReviewDTO;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.DTO.UserDTO;
import ItemBro.User.DTO.UserInfoDTO;
import jakarta.validation.Valid;
import org.springframework.cglib.SpringCglibInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
        UserDTO userDTO = new UserDTO(userFromService);
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

    @PutMapping("/api/user/me/avatar")
    public ResponseEntity<UserDTO> uploadAvatar(@RequestPart("file") MultipartFile file, Principal principal) throws IOException {
        return ResponseEntity.ok(userService.uploadAvatar(principal, file));
    }

    @DeleteMapping("/api/user/me/avatar")
    public ResponseEntity<UserDTO> deleteAvatar(Principal principal) throws IOException {
        return ResponseEntity.ok(userService.deleteAvatar(principal));
    }

    @PatchMapping("/api/user/me")
    public ResponseEntity<UserDTO> updateProfileInfo(@Valid @RequestBody UserUpdateBody userUpdateBody,
                                                     Principal principal) {
        return ResponseEntity.ok(userService.updateProfileInfo(principal, userUpdateBody));
    }
}
