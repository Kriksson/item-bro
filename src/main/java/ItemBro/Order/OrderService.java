package ItemBro.Order;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Advice.DTO.AdviceDTO;
import ItemBro.Exception.*;
import ItemBro.Game.DTO.GameDTO;
import ItemBro.Game.Game;
import ItemBro.Game.GameStatus;
import ItemBro.Order.DTO.OrderDTO;
import ItemBro.User.DTO.UserBuyerDTO;
import ItemBro.User.DTO.UserSellerDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final AdviceRepository adviceRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,  UserRepository userRepository,
                        AdviceRepository adviceRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.adviceRepository = adviceRepository;
    }

    @Transactional
    public OrderDTO createOrder(Principal principal, Long adviceId) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        Optional<Advice> a = adviceRepository.findById(adviceId);
        if (u.isEmpty()) throw new UserNotFoundException("User not found");
        if (a.isEmpty()) throw new AdviceNotFoundException("Advice not found");


        User userBuyer = u.get();
        Advice advice = a.get();
        User userSeller = advice.getSeller();
        Game game =  advice.getGame();
        GameStatus gameStatus = game.getStatus();
        ItemBro.Advice.AdviceStatus adviceStatus = advice.getStatus();

        if (adviceStatus == AdviceStatus.ARCHIVED) throw new AdviceNotFoundException("Advice has been deleted");
        if (gameStatus == GameStatus.ARCHIVED) throw new GameArchivedException("game in archive");

        BigDecimal price = advice.getPrice();
        BigDecimal userBuyerBalance =  userBuyer.getBalance();

        if (userBuyerBalance.compareTo(price) < 0) throw new InsufficientBalanceException("Insufficient balance");

        Order order = new Order(advice, userBuyer, userSeller, price, OrderStatus.OPENED);

        BigDecimal newUserBuyerBalance =  userBuyerBalance.subtract(price);
        userBuyer.setBalance(newUserBuyerBalance);

        Order orderFromDB = orderRepository.save(order);

        UserBuyerDTO userBuyerDTO = new UserBuyerDTO(userBuyer.getUsername());
        UserSellerDTO userSellerDTO = new UserSellerDTO(userSeller.getUsername());
        GameDTO gameDTO = new GameDTO(game.getId(), game.getName());
        AdviceDTO adviceDTO = new AdviceDTO(advice.getId(), advice.getName(), gameDTO, userSellerDTO, price);

        return new OrderDTO(orderFromDB.getId(), adviceDTO, userBuyerDTO, price, order.getRating(), order.getFeedback(),
                order.getSellerResponse());
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Principal principal, Long id) {
        Optional<Order> o = orderRepository.findById(id);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Order not found");
        if (u.isEmpty()) throw new UserNotFoundException("User not found");

        Order order = o.get();
        Advice advice = order.getAdvice();
        Game game = advice.getGame();

        User userFromRequest = u.get();
        User userBuyer = order.getBuyer();
        User userSeller = order.getSeller();
        BigDecimal price = order.getPrice();

        if ( !(Objects.equals(userFromRequest.getId(), userBuyer.getId())
                || Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("No have permissions");
            }
        }

        UserBuyerDTO userBuyerDTO = new UserBuyerDTO(userBuyer.getUsername());
        UserSellerDTO userSellerDTO = new UserSellerDTO(userSeller.getUsername());
        GameDTO gameDTO = new GameDTO(game.getId(), game.getName());
        AdviceDTO adviceDTO = new AdviceDTO(advice.getId(), advice.getName(), gameDTO, userSellerDTO, price);

        return new OrderDTO(order.getId(), adviceDTO, userBuyerDTO, price, order.getRating(), order.getFeedback(),
                order.getSellerResponse());
    }

    @Transactional
    public void cancelOrder(Principal principal, Long orderId) {
        Optional<Order> o = orderRepository.findById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Order not found");
        if (u.isEmpty()) throw new UserNotFoundException("User not found");
        Order order = o.get();
        User userFromRequest = u.get();
        BigDecimal price = order.getPrice();
        OrderStatus  orderStatus = order.getStatus();

        User userSeller =  order.getSeller();
        User userBuyer =  order.getBuyer();
        if (!(Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("No have permissions");
            }
        }

        if (orderStatus != OrderStatus.OPENED) throw new OrderNotOpenedException("order not open");

        BigDecimal buyerBalance =  userBuyer.getBalance();
        BigDecimal newUserBuyerBalance =  buyerBalance.add(price);

        userBuyer.setBalance(newUserBuyerBalance);
        order.setStatus(OrderStatus.REFUND);
    }

    @Transactional
    public void confirmOrder(Principal principal, Long orderId) {
        Optional<Order> o = orderRepository.findById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Order not found");
        if (u.isEmpty()) throw new UserNotFoundException("User not found");

        Order order = o.get();
        User userFromRequest = u.get();
        BigDecimal price = order.getPrice();
        OrderStatus  orderStatus = order.getStatus();

        User userSeller =  order.getSeller();
        User userBuyer =  order.getBuyer();
        if (!(Objects.equals(userFromRequest.getId(), userBuyer.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("No have permissions");
            }
        }

        if (orderStatus != OrderStatus.OPENED) throw new OrderNotOpenedException("order not open");

        BigDecimal sellerBalance =  userSeller.getBalance();
        BigDecimal newUserSellerBalance =  sellerBalance.add(price);

        userSeller.setBalance(newUserSellerBalance);
        order.setStatus(OrderStatus.CLOSED);
    }

    @Transactional
    public void addFeedback(Principal principal, Long orderId, Integer rating, String feedbackText) {
        Optional<Order> o = orderRepository.findById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Order not found");
        if (u.isEmpty()) throw new UserNotFoundException("User not found");

        Order order = o.get();
        User userFromRequest = u.get();

        User buyerUser =  order.getBuyer();

        if (!(Objects.equals(userFromRequest.getId(), buyerUser.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("No have permissions");
            }
        }

        order.setRating(rating);
        order.setFeedback(feedbackText);
    }

    @Transactional
    public void addSellerResponse(Principal principal, Long orderId, String sellerResponseText) {
        Optional<Order> o = orderRepository.findById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Order not found");
        if (u.isEmpty()) throw new UserNotFoundException("User not found");

        Order order = o.get();
        User userFromRequest = u.get();

        User userSeller = order.getSeller();

        if (!(Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("No have permissions");
            }
        }

        if (order.getRating() == -1) throw new FeedbackNotCreatedException("Feedback not created");

        order.setSellerResponse(sellerResponseText);
    }
}
