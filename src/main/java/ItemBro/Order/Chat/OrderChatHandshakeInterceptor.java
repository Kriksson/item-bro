package ItemBro.Order.Chat;

import ItemBro.Exception.NoPermissionsException;
import ItemBro.Exception.OrderNotFoundException;
import ItemBro.Exception.UserNotFoundException;
import ItemBro.Order.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OrderChatHandshakeInterceptor implements HandshakeInterceptor {

    private static final Pattern CHAT_PATH = Pattern.compile("^/api/orders/([^/]+)/chat/ws$");
    private final OrderService orderService;
    public OrderChatHandshakeInterceptor(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        Principal principal = request.getPrincipal();

        if (principal == null) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        Matcher matcher = CHAT_PATH.matcher(request.getURI().getPath());

        if (!matcher.matches()) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }

        long orderId;

        try {
            orderId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }

        if (orderId <= 0) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }

        try {
            orderService.getOrderById(principal, orderId);
        } catch (OrderNotFoundException e) {
            return reject(response, HttpStatus.NOT_FOUND);
        } catch (NoPermissionsException e) {
            return reject(response, HttpStatus.FORBIDDEN);
        } catch (UserNotFoundException e) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        attributes.put("orderId", orderId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
