package ItemBro.Order;

import ItemBro.User.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByBuyerOrSeller(User user, User user2);
    List<Order> findAllBySellerAndRatingNotNullAndRatingNot(User user, Integer dRating);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(Long id);
}
