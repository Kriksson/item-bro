package ItemBro.Game;

import ItemBro.Exception.GameAlreadyCreatedException;
import ItemBro.Exception.NoPermissionsException;
import ItemBro.Exception.UserNotFoundException;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.Principal;
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
}
