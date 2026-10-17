package ItemBro.Game;

import ItemBro.Exception.*;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GameService {

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
            return new GameDTO(gameFromDB.getId(), gameFromDB.getName());
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
                .map(e -> new GameDTO(e.getId(), e.getName()))
                .toList();

    }
}
