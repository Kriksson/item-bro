package ItemBro.Order;

import ItemBro.Advice.DTO.AdviceIdBody;
import ItemBro.Order.DTO.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final OrderMessageService orderMessageService;
    public OrderController(OrderService orderService,  OrderMessageService orderMessageService) {
        this.orderService = orderService;
        this.orderMessageService = orderMessageService;
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

    @PatchMapping("/api/orders/{id}/response")
    public ResponseEntity<Void> addSellerResponse(Principal principal, @PathVariable("id") Long id,
                                            @Valid @RequestBody OrderResponseBody responseBody) {
        String responseText =  responseBody.responseText();
        orderService.addSellerResponse(principal, id, responseText);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<OrderDTO>> getOrders(Principal principal) {
        return ResponseEntity.ok(orderService.getOrders(principal));
    }

    @GetMapping("/api/admin/orders")
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PostMapping("/api/orders/{id}/messages")
    public ResponseEntity<MessageDTO> sendMessage(Principal principal, @PathVariable("id") Long id,
                                                  @Valid @RequestBody MessageBody messageBody) {
        AddMessageResult result = orderMessageService.addMessage(id, principal, messageBody);

        if (result.result()) {
            return ResponseEntity.status(201).body(result.message());
        }

        return ResponseEntity.ok(result.message());
    }

    @GetMapping("/api/orders/{id}/messages")
    public ResponseEntity<MessagesDTO> getMessages(
            Principal principal, @PathVariable("id") Long id,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "afterId", required = false) Long afterId) {
        return ResponseEntity.ok(orderMessageService.getMessages(principal, id, limit, beforeId, afterId));
    }
}
