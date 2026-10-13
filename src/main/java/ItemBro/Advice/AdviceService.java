package ItemBro.Advice;

import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Exception.AdviceNotFoundException;
import ItemBro.Exception.GameNotFountException;
import ItemBro.Exception.UserLimitAdviceException;
import ItemBro.Exception.UserNotFoundException;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdviceService {
    private final AdviceRepository adviceRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    public AdviceService(AdviceRepository adviceRepository,  UserRepository userRepository,
                         GameRepository gameRepository) {
        this.adviceRepository = adviceRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
    }

    @EntityGraph(attributePaths = {"seller", "game"})
    public Advice createAdvice(AdviceBody adviceBody, Principal principal) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isPresent()) {
            User user = u.get();
            long countOfAdvices = adviceRepository.countAdvicesBySellerId(user.getId());
            if (countOfAdvices < 5) {
                Optional<Game> g = gameRepository.findById(adviceBody.game_id());
                if (g.isPresent()) {
                    Game game = g.get();
                    Advice advice = new Advice(adviceBody.name(), game, user, adviceBody.price());
                    return adviceRepository.save(advice);
                } else {
                    throw new GameNotFountException("Game not found");
                }
            } else {
                throw new UserLimitAdviceException("Limit advice");
            }
        } else {
            throw new UserNotFoundException("User not found");
        }
    }

    public void deleteAdvice(Long id, Principal principal) {
        Optional<Advice> a = adviceRepository.findById(id);
        if (a.isPresent()) {
            Advice advice = a.get();
            Optional<User> u = userRepository.findByUsername(principal.getName());
            if (u.isPresent()) {
                User user = u.get();
                if (Objects.equals(user.getId(), advice.getSeller().getId())) {
                    adviceRepository.delete(advice);
                }
            } else {
                throw new UserNotFoundException("User not found");
            }
        } else {
            throw new AdviceNotFoundException("Advice not found");
        }
    }
}
