package ItemBro.Order.DTO;

import ItemBro.Advice.Advice;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Game.Game;
import ItemBro.Order.Order;
import ItemBro.Order.OrderStatus;
import ItemBro.User.DTO.UserBuyerDTO;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.User;

import java.math.BigDecimal;

public record OrderDTO(Long id, AdviceDTO adviceDTO, UserBuyerDTO userBuyerDTO,
                       BigDecimal price, Integer rating, String feedbackText,
                       String sellerResponseText, OrderStatus orderStatus) {
    public OrderDTO(Order order, Advice advice, User userBuyer, User userSeller, Game game) {
        this(order.getId(), new AdviceDTO(advice, game, userSeller), new UserBuyerDTO(userBuyer),
                order.getPrice(), order.getRating(), order.getFeedback(), order.getSellerResponse(), order.getStatus());
    }
}
