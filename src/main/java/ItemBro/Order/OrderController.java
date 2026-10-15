package ItemBro.Order;

import ItemBro.Advice.DTO.AdviceIdBody;
import ItemBro.Order.DTO.OrderDTO;
import ItemBro.Order.DTO.OrderFeedbackBody;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
public class OrderController {

    private final OrderService orderService;
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders/")
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody AdviceIdBody adviceIdBody, Principal principal) {
        OrderDTO orderDTO = orderService.createOrder(principal, adviceIdBody.id());
        URI location = URI.create("/api/orders/"+orderDTO.id());
        return ResponseEntity.created(location).body(orderDTO);
    }

    @GetMapping("/api/orders/{id}")
    public ResponseEntity<OrderDTO> getOrderById(Principal principal, @PathVariable("id") Long id) {
        OrderDTO orderDTO = orderService.getOrderById(principal, id);
        return ResponseEntity.ok(orderDTO);
    }

    @PatchMapping("/api/orders/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(Principal principal, @PathVariable("id") Long id) {
        orderService.cancelOrder(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/orders/{id}/confirm")
    public ResponseEntity<Void> confirmOrder(Principal principal, @PathVariable("id") Long id) {
        orderService.confirmOrder(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/orders/{id}/feedback")
    public ResponseEntity<Void> addFeedback(Principal principal, @PathVariable("id") Long id,
                                         @Valid @RequestBody OrderFeedbackBody feedbackBody) {
        Integer rating = feedbackBody.rating();
        String feedbackText =  feedbackBody.feedbackText();
        orderService.addFeedback(principal, id, rating, feedbackText);
        return ResponseEntity.noContent().build();
    }
}
