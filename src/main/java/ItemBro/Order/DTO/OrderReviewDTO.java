package ItemBro.Order.DTO;

import ItemBro.Order.Order;

public record OrderReviewDTO(Integer rating, String feedback, String sellerResponse) {
    public OrderReviewDTO(Order order) {
        this(order.getRating(), order.getFeedback(), order.getSellerResponse());
    }
}
