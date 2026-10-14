package ItemBro.Advice;

import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Exception.*;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public AdviceDTO createAdvice(AdviceBody adviceBody, Principal principal) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isPresent()) {
            User user = u.get();
            long countOfAdvices = adviceRepository.countAdvicesBySellerIdAndStatusEquals(user.getId(),
                    AdviceStatus.ACTIVE);
            if (countOfAdvices < 5) {
                Optional<Game> g = gameRepository.findById(adviceBody.game_id());
                if (g.isPresent()) {
                    Game game = g.get();
                    Advice advice = new Advice(adviceBody.name(), game, user, adviceBody.price(), AdviceStatus.ACTIVE);
                    Advice adviceFromDB =  adviceRepository.save(advice);
                    UserSellerDTO userSellerDTO = new UserSellerDTO(user.getUsername());
                    GameDTO gameDTO = new GameDTO(game.getId(), game.getName());
                    return new AdviceDTO(adviceFromDB.getId(), adviceFromDB.getName(), gameDTO,
                            userSellerDTO, adviceFromDB.getPrice());
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

    @Transactional
    public void deleteAdvice(Long id, Principal principal) {
        Optional<Advice> a = adviceRepository.findById(id);
        if (a.isPresent()) {
            Advice advice = a.get();
            Optional<User> u = userRepository.findByUsername(principal.getName());
            if (u.isPresent()) {
                User user = u.get();
                if (Objects.equals(user.getId(), advice.getSeller().getId())) {
                    advice.setStatus(AdviceStatus.ARCHIVED);
                } else {
                    throw new NoPermissionsException("No permissions");
                }
            } else {
                throw new UserNotFoundException("User not found");
            }
        } else {
            throw new AdviceNotFoundException("Advice not found");
        }
    }
}
