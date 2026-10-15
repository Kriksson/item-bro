package ItemBro.Advice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdviceRepository extends JpaRepository<Advice, Long> {
    long countAdvicesBySellerIdAndStatusEquals(Long sellerId, AdviceStatus status);
    Optional<Advice> findById(long adviceId);
    @EntityGraph(attributePaths = {"seller", "game"})
    Page<Advice> findAdviceByGame_IdAndStatusOrderByIdDesc(Long gameId, AdviceStatus status, Pageable pageable);
}
