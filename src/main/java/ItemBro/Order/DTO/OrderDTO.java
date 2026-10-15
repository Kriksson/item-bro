package ItemBro.Order.DTO;

import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.User.DTO.UserBuyerDTO;
import ItemBro.User.DTO.UserSellerDTO;

import java.math.BigDecimal;

public record OrderDTO(Long id, AdviceDTO adviceDTO, UserBuyerDTO userBuyerDTO,
                       BigDecimal price, Integer rating, String feedbackText, String sellerResponseText) {
}
