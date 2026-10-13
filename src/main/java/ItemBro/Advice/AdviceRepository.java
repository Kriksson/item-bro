package ItemBro.Advice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdviceRepository extends JpaRepository<Advice, Long> {
    long countAdvicesBySellerId(long sellerId);
}
