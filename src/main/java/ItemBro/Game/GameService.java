package ItemBro.Game;

import ItemBro.Exception.*;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.apache.tika.Tika;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

@Service
public class GameService {

    private final static Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    public GameService(GameRepository gameRepository, UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
    }

    public GameDTO createGame(Principal principal, GameBody gameBody) {
        try {
            Optional<User> u = userRepository.findByUsername(principal.getName());
            if (u.isEmpty()) throw new UserNotFoundException("user not found");

            User user = u.get();

            if (user.getRole() != UserRole.ADMIN) throw new NoPermissionsException("no have permissions to create game");
            Game newGame = new Game(gameBody.name());
            Game gameFromDB = gameRepository.saveAndFlush(newGame);
            return new GameDTO(gameFromDB);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation &&
            "unique_games_name".equals(violation.getConstraintName())) {
                throw new GameAlreadyCreatedException("game already created");
            }
            throw e;
        }
    }

    @Transactional
    public void deleteGame(Principal principal, Long gameId) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException("user not found");

        User user = u.get();

        if (user.getRole() != UserRole.ADMIN) throw new NoPermissionsException("no have permissions to delete game");

        Optional<Game> g = gameRepository.findById(gameId);

        if (g.isEmpty()) throw new GameNotFountException("game not found");

        Game game = g.get();
        GameStatus gameStatus = game.getStatus();

        if (gameStatus != GameStatus.OPENED) throw new GameNotOpenedException("game not opened");

        game.setStatus(GameStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public List<GameDTO> getOpenedGames() {
        List<Game> games = gameRepository.findAllByStatus(GameStatus.OPENED);

        if (games.isEmpty()) return new ArrayList<>();

        return games.stream()
                .map(GameDTO::new)
                .toList();

    }

    @Transactional(rollbackFor =  IOException.class)
    public GameDTO uploadImage(Long gameId, MultipartFile image) throws IOException {
        Optional<Game> g = gameRepository.findById(gameId);
        if (g.isEmpty()) throw new GameNotFountException("game not found");

        Game game = g.get();

        GameStatus gameStatus = game.getStatus();

        if (gameStatus != GameStatus.OPENED) throw new GameNotOpenedException("game not opened");

        if (image.isEmpty()) throw new IllegalArgumentException("Файл не может быть пустым");
        if (image.getSize() > (5L * 1024 * 1024))
            throw new MaxUploadSizeExceededException(5L * 1024 * 1024);

        String contentType = new Tika().detect(image.getBytes());
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Разрешены только: PNG, JPEG, WEBP.");

        String fileID = UUID.randomUUID().toString();

        Path uploadDirectory = Path.of("/app/uploads");
        Files.createDirectories(uploadDirectory);

        Path destination = uploadDirectory.resolve(fileID);

        String oldImageUrl = game.getImageUrl();
        Path oldFile;

        if (oldImageUrl == null) {
            oldFile = null;
        } else {
            if (!oldImageUrl.startsWith("/api/media/"))
                throw new IllegalStateException("Некорректный адрес у старого изображения");

            String oldFileId =  oldImageUrl.substring("/api/media/".length());
            oldFile = uploadDirectory.resolve(oldFileId);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteFile(oldFile);
                } else if  (status == STATUS_ROLLED_BACK) {
                    deleteFile(destination);
                } else {
                    logger.warn("Неизвестный результат. Путь: {}", destination);
                }
            }
        });

        image.transferTo(destination);
        String imageUrl = "/api/media/" + fileID;
        game.setImageUrl(imageUrl);
        return new GameDTO(game);
    }

    public void deleteFile(Path path) {
        if (path == null) return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Не удалось удалить файл {}", path, e);
        }
    }
}
