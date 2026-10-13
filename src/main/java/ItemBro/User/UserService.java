package ItemBro.User;

import ItemBro.Exception.UserAlreadyRegisterException;
import ItemBro.User.DTO.UserBody;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@org.springframework.stereotype.Service
public class UserService {

    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User createUser(UserBody user) {
        if (userRepository.existsByUsername(user.name())) throw new UserAlreadyRegisterException("Имя занято!");
        User newUser = new User(user.name(), passwordEncoder.encode(user.password()), new BigDecimal("0"));
        return userRepository.save(newUser);
    }
}