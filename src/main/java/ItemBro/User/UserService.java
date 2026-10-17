package ItemBro.User;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Exception.UserAlreadyRegisterException;
import ItemBro.Exception.UserNotFoundException;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.Order.DTO.OrderDTO;
import ItemBro.Order.Order;
import ItemBro.Order.OrderRepository;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.DTO.UserDTO;
import ItemBro.User.DTO.UserInfoDTO;
import ItemBro.User.DTO.UserSellerDTO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class UserService {

    private final UserRepository userRepository;
    private final AdviceRepository adviceRepository;
    private final OrderRepository orderRepository;
    public UserService(UserRepository userRepository,  AdviceRepository adviceRepository,
                       OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.adviceRepository = adviceRepository;
        this.orderRepository = orderRepository;
    }
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User createUser(UserBody user) {
        if (userRepository.existsByUsername(user.name())) throw new UserAlreadyRegisterException("Имя занято!");
        User newUser = new User(user.name(), passwordEncoder.encode(user.password()), new BigDecimal("0"));
        return userRepository.save(newUser);
    }

    @Transactional(readOnly = true)
    public UserDTO getMe(Principal principal) {
        Optional<User> u =  userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException(principal.getName());

        User user = u.get();

        return new UserDTO(user.getUsername(), user.getRole(), user.getBalance());
    }
    @Transactional(readOnly = true)
    public UserInfoDTO getUserInfo(String username) {
        Optional<User> u =  userRepository.findByUsername(username);
        if (u.isEmpty()) throw new UserNotFoundException(username);

        User user = u.get();
        return new UserInfoDTO(user);
    }

    @Transactional(readOnly = true)
    public List<AdviceDTO> getUserAdvices(String username) {
        Optional<User> u =  userRepository.findByUsername(username);
        if (u.isEmpty()) throw new UserNotFoundException(username);

        User user = u.get();

        List<Advice> adviceList = adviceRepository.findAllBySellerAndStatus(user, AdviceStatus.ACTIVE);
        return adviceList.stream()
                .map(e -> {
                    Game game = e.getGame();
                    GameDTO gameDTO = new GameDTO(game.getId(), game.getName());
                    User userSeller = e.getSeller();
                    UserSellerDTO userSellerDTO = new UserSellerDTO(userSeller);
                    return new AdviceDTO(e.getId(), e.getName(), gameDTO, userSellerDTO, e.getPrice());
                })
                .toList();
    }
    @Transactional(readOnly = true)
    public List<OrderReviewDTO> getUserOrderReviews(String username) {
        Optional<User> u =  userRepository.findByUsername(username);
        if (u.isEmpty()) throw new UserNotFoundException(username);

        User user = u.get();

        List<Order> orderList = orderRepository.findAllBySellerAndRatingNotNullAndRatingNot(user, -1);
        return orderList.stream()
                .map(OrderReviewDTO::new).toList();
    }
}