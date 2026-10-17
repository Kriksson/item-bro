package ItemBro.Game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findById(long id);
    boolean existsByName(String name);
    List<Game> findAllByStatus(GameStatus status);
}
