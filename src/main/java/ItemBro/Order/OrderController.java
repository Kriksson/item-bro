package ItemBro.Order;

import ItemBro.Advice.DTO.AdviceIdBody;
import ItemBro.Order.DTO.OrderDTO;
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
    @PatchMapping("/api/orders/{id}")
    public ResponseEntity<Void> cancelOrder(Principal principal, @PathVariable("id") Long id) {
        orderService.cancelOrder(principal, id);
        return ResponseEntity.noContent().build();
    }
}
