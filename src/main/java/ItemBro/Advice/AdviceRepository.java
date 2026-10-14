package ItemBro.Advice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdviceRepository extends JpaRepository<Advice, Long> {
    long countAdvicesBySellerIdAndStatusEquals(Long sellerId, AdviceStatus status);
    Optional<Advice> findById(long adviceId);
}
