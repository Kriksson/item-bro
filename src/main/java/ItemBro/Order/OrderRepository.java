package ItemBro.Order;

import ItemBro.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByBuyerOrSeller(User user, User user2);
    List<Order> findAllBySellerAndRatingNotNullAndRatingNot(User user, Integer dRating);
}
