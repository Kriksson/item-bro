package ItemBro.User;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Exception.UserAlreadyRegisterException;
import ItemBro.Exception.UserNotFoundException;
import ItemBro.Game.Game;
import ItemBro.Game.GameStatus;
import ItemBro.Order.DTO.OrderReviewDTO;
import ItemBro.Order.Order;
import ItemBro.Order.OrderRepository;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.DTO.UserDTO;
import ItemBro.User.DTO.UserInfoDTO;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.web.OidcLogoutDsl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@org.springframework.stereotype.Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

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

        return new UserDTO(user);
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

        List<Advice> adviceList = adviceRepository.findAllBySellerAndStatusAndGame_Status(user, AdviceStatus.ACTIVE,
                GameStatus.OPENED);
        return adviceList.stream()
                .map(e -> {
                    Game game = e.getGame();
                    User userSeller = e.getSeller();
                    return new AdviceDTO(e, game, userSeller);
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

    @Transactional(rollbackFor = IOException.class)
    public UserDTO uploadAvatar(Principal principal, MultipartFile file) throws IOException {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException(principal.getName());

        if (file.isEmpty()) throw new IllegalArgumentException("Файл пустой");
        if (file.getSize() > (5L * 1024 * 1024)) throw new IllegalArgumentException("Файл весом свыше 5МБ");
        String contentType = new Tika().detect(file.getBytes());
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Разрешены только файлы типа: JPEG, PNG, WEBP.");

        User user = u.get();

        String fileId = UUID.randomUUID().toString();

        Path uploadDirectory = Path.of("/app/uploads");
        Files.createDirectories(uploadDirectory);

        Path destination = uploadDirectory.resolve(fileId);

        String oldAvatarUrl = user.getAvatarUrl();
        Path oldFile;

        if (oldAvatarUrl == null) {
            oldFile = null;
        } else {
            if (!oldAvatarUrl.startsWith("/api/media/")) throw new IllegalStateException("Некорректный адрес");

            String oldFileId =  oldAvatarUrl.substring("/api/media/".length());
            oldFile = uploadDirectory.resolve(oldFileId);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    deleteFile(oldFile);
                } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteFile(destination);
                } else {
                    logger.warn("Неизвестный результат. Путь: {}", destination);
                }
            }
        });

        file.transferTo(destination);
        String avatarUrl = "/api/media/"+fileId;
        user.setAvatarUrl(avatarUrl);
        return new UserDTO(user);
    }

    private void deleteFile(Path file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Не удалось удалить файл: {}", file, e);
        }
    }

    @Transactional(rollbackFor = IOException.class)
    public UserDTO deleteAvatar(Principal principal) throws IOException {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException(principal.getName());
        User user = u.get();

        Path uploadDirectory = Path.of("/app/uploads");
        Files.createDirectories(uploadDirectory);

        String oldAvatarUrl = user.getAvatarUrl();
        if (oldAvatarUrl == null) return new UserDTO(user);

        String oldFileId =  oldAvatarUrl.substring("/api/media/".length());
        Path oldFile = uploadDirectory.resolve(oldFileId);
        user.setAvatarUrl(null);


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    deleteFile(oldFile);
                }
            }
        });
        return new UserDTO(user);
    }

    @Transactional
    public UserDTO updateProfileInfo(Principal principal, UserUpdateBody userUpdateBody) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException(principal.getName());
        User user = u.get();

        user.setAge(userUpdateBody.age());
        user.setCity(userUpdateBody.city());
        user.setAbout(userUpdateBody.about());

        return new UserDTO(user);
    }
}