package ItemBro.Advice;

import ItemBro.Game.GameStatus;
import ItemBro.User.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdviceRepository extends JpaRepository<Advice, Long> {
    long countAdvicesBySellerIdAndStatusEquals(Long sellerId, AdviceStatus status);
    Optional<Advice> findById(long adviceId);
    List<Advice> findAllBySellerAndStatusAndGame_Status(User seller, AdviceStatus status, GameStatus gameStatus);
    @EntityGraph(attributePaths = {"seller", "game"})
    Page<Advice> findAdviceByGame_IdAndStatusOrderByIdDesc(Long gameId, AdviceStatus status, Pageable pageable);
}
