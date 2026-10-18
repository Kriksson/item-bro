package ItemBro.Order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {
    Optional<OrderMessage> findByOrder_IdAndSender_IdAndClientMessageId(
            Long orderId,
            Long senderId,
            UUID clientMessageId
    );

    Slice<OrderMessage> findByOrder_IdAndIdGreaterThanOrderByIdAsc(Long orderId, Long afterId, Pageable pageable);
    Slice<OrderMessage> findByOrder_IdAndIdLessThanOrderByIdDesc(Long orderId, Long beforeId, Pageable pageable);
    Slice<OrderMessage> findByOrder_IdOrderByIdDesc(Long orderId, Pageable pageable);
}
