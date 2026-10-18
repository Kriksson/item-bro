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
import org.apache.juli.logging.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final AdviceRepository adviceRepository;
    private final UserRepository userRepository;
    private final OrderMessageService orderMessageService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository,
                        AdviceRepository adviceRepository, OrderMessageService orderMessageService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.adviceRepository = adviceRepository;
        this.orderMessageService = orderMessageService;
    }

    @Transactional
    public OrderDTO createOrder(Principal principal, Long adviceId) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        Optional<Advice> a = adviceRepository.findById(adviceId);
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        if (a.isEmpty()) throw new AdviceNotFoundException("Объявление не найдено");


        User userBuyer = u.get();
        Advice advice = a.get();
        User userSeller = advice.getSeller();
        Game game =  advice.getGame();
        GameStatus gameStatus = game.getStatus();
        ItemBro.Advice.AdviceStatus adviceStatus = advice.getStatus();

        if (adviceStatus == AdviceStatus.ARCHIVED) throw new AdviceNotFoundException("Объявление было удалено");
        if (gameStatus == GameStatus.ARCHIVED) throw new GameArchivedException("Игра была удалена");

        BigDecimal price = advice.getPrice();
        BigDecimal userBuyerBalance =  userBuyer.getBalance();

        if (userBuyerBalance.compareTo(price) < 0) throw new InsufficientBalanceException("Недостаточно средств");

        Order order = new Order(advice, userBuyer, userSeller, price, OrderStatus.OPENED);

        BigDecimal newUserBuyerBalance =  userBuyerBalance.subtract(price);
        userBuyer.setBalance(newUserBuyerBalance);

        Order orderFromDB = orderRepository.save(order);

        orderMessageService.addSystemMessage(order, userBuyer, EventType.ORDER_CREATED);

        return new OrderDTO(orderFromDB, advice, userBuyer, userSeller, game);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Principal principal, Long id) {
        Optional<Order> o = orderRepository.findById(id);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        Order order = o.get();
        Advice advice = order.getAdvice();
        Game game = advice.getGame();

        User userFromRequest = u.get();
        User userBuyer = order.getBuyer();
        User userSeller = order.getSeller();

        if ( !(Objects.equals(userFromRequest.getId(), userBuyer.getId())
                || Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("У вас нет прав");
            }
        }

        return new OrderDTO(order, advice, userBuyer, userSeller, game);
    }

    @Transactional
    public void cancelOrder(Principal principal, Long orderId) {
        Optional<Order> o = orderRepository.findLockedById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        Order order = o.get();
        User userFromRequest = u.get();
        BigDecimal price = order.getPrice();
        OrderStatus  orderStatus = order.getStatus();

        User userSeller =  order.getSeller();
        User userBuyer =  order.getBuyer();
        if (!(Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("У вас нет прав");
            }
        }

        if (orderStatus != OrderStatus.OPENED) throw new OrderNotOpenedException("Заказ не открыт");

        BigDecimal buyerBalance =  userBuyer.getBalance();
        BigDecimal newUserBuyerBalance =  buyerBalance.add(price);

        userBuyer.setBalance(newUserBuyerBalance);
        order.setStatus(OrderStatus.REFUND);

        orderMessageService.addSystemMessage(order, userFromRequest, EventType.ORDER_CANCELED);
    }

    @Transactional
    public void confirmOrder(Principal principal, Long orderId) {
        Optional<Order> o = orderRepository.findLockedById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        Order order = o.get();
        User userFromRequest = u.get();
        BigDecimal price = order.getPrice();
        OrderStatus  orderStatus = order.getStatus();

        User userSeller =  order.getSeller();
        User userBuyer =  order.getBuyer();
        if (!(Objects.equals(userFromRequest.getId(), userBuyer.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("У вас нет прав");
            }
        }

        if (orderStatus != OrderStatus.OPENED) throw new OrderNotOpenedException("Заказ не открыт");

        BigDecimal sellerBalance =  userSeller.getBalance();
        BigDecimal newUserSellerBalance =  sellerBalance.add(price);

        userSeller.setBalance(newUserSellerBalance);
        order.setStatus(OrderStatus.CLOSED);

        orderMessageService.addSystemMessage(order, userFromRequest, EventType.ORDER_CONFIRMED);
    }

    @Transactional
    public void addFeedback(Principal principal, Long orderId, Integer rating, String feedbackText) {
        Optional<Order> o = orderRepository.findLockedById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        Order order = o.get();
        User userFromRequest = u.get();

        User buyerUser =  order.getBuyer();

        if (!(Objects.equals(userFromRequest.getId(), buyerUser.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("У вас нет прав!");
            }
        }
        if (order.getFeedback() == null) {
            orderMessageService.addSystemMessage(order, userFromRequest, EventType.FEEDBACK_ADDED);
            order.setRating(rating);
            order.setFeedback(feedbackText);
        } else {
            orderMessageService.addSystemMessage(order, userFromRequest, EventType.FEEDBACK_UPDATED);
            order.setRating(rating);
            order.setFeedback(feedbackText);
        }
    }

    @Transactional
    public void addSellerResponse(Principal principal, Long orderId, String sellerResponseText) {
        Optional<Order> o = orderRepository.findLockedById(orderId);
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        Order order = o.get();
        User userFromRequest = u.get();

        User userSeller = order.getSeller();

        if (!(Objects.equals(userFromRequest.getId(), userSeller.getId()))) {
            if(userFromRequest.getRole() != UserRole.ADMIN) {
                throw new NoPermissionsException("У вас нет прав!");
            }
        }

        if (order.getRating() == -1) throw new FeedbackNotCreatedException("Отзыв не был создан");

        if (order.getSellerResponse() == null) {
            order.setSellerResponse(sellerResponseText);
            orderMessageService.addSystemMessage(order, userFromRequest, EventType.SELLER_RESPONSE_ADDED);
        } else {
            order.setSellerResponse(sellerResponseText);
            orderMessageService.addSystemMessage(order, userFromRequest, EventType.SELLER_RESPONSE_UPDATED);
        }

    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getOrders(Principal principal) {
        Optional<User> u = userRepository.findByUsername(principal.getName());
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        List<Order> orders = orderRepository.findAllByBuyerOrSeller(u.get(), u.get());

        if (orders.isEmpty()) throw new OrderNotFoundException("Заказы не найдены");

        return orders.stream()
                .map(e -> {
                    Advice advice = e.getAdvice();
                    Game game =  advice.getGame();
                    User userSeller = advice.getSeller();
                    User userBuyer = e.getBuyer();
                    UserBuyerDTO userBuyerDTO = new UserBuyerDTO(userBuyer);
                    AdviceDTO adviceDTO = new AdviceDTO(advice, game, userSeller);
                    return new OrderDTO(e.getId(), adviceDTO, userBuyerDTO, e.getPrice(),
                            e.getRating(), e.getFeedback(), e.getSellerResponse(), e.getStatus());
                }).toList();
    }
    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders() {
        List<Order> orders =  orderRepository.findAll();
        return orders.stream()
                .map(e -> {
                    Advice advice = e.getAdvice();
                    Game game =  advice.getGame();
                    User userSeller = advice.getSeller();
                    User userBuyer = e.getBuyer();
                    UserBuyerDTO userBuyerDTO = new UserBuyerDTO(userBuyer);
                    AdviceDTO adviceDTO = new AdviceDTO(advice, game, userSeller);
                    return new OrderDTO(e.getId(), adviceDTO, userBuyerDTO, e.getPrice(),
                            e.getRating(), e.getFeedback(), e.getSellerResponse(), e.getStatus());
                }).toList();
    }

    @Transactional
    public void adminOrderReopen(Long orderId, Principal principal) {
        if (orderId < 1) throw new IllegalArgumentException("Невалидный номер заказа");
        Optional<User> u = userRepository.findByUsername(principal.getName());
        Optional<Order> o = orderRepository.findLockedById(orderId);
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        Order order = o.get();
        User user = u.get();

        if (user.getRole() != UserRole.ADMIN) throw new NoPermissionsException("У вас нет прав");

        OrderStatus orderStatus = order.getStatus();
        User userSeller = order.getSeller();

        if (orderStatus != OrderStatus.CLOSED) throw new OrderNotClosedException("Невозможно открыть незакрытый заказ!");

        order.setStatus(OrderStatus.OPENED);

        BigDecimal userSellerBalance = userSeller.getBalance();
        BigDecimal price = order.getPrice();
        BigDecimal newUserSellerBalance = userSellerBalance.subtract(price);
        userSeller.setBalance(newUserSellerBalance);
        orderMessageService.addSystemMessage(order, user, EventType.ORDER_REOPENED);
    }

    @Transactional
    public void adminOrderConfirm(Long orderId, Principal principal) {
        if (orderId < 1) throw new IllegalArgumentException("Невалидный номер заказа");
        Optional<User> u = userRepository.findByUsername(principal.getName());
        Optional<Order> o = orderRepository.findLockedById(orderId);
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        Order order = o.get();
        User user = u.get();

        if (user.getRole() != UserRole.ADMIN) throw new NoPermissionsException("У вас нет прав");

        OrderStatus orderStatus = order.getStatus();
        User userSeller = order.getSeller();

        if (orderStatus != OrderStatus.OPENED) throw new OrderNotOpenedException("Невозможно закрыть неоткрытый заказ!");

        BigDecimal userSellerBalance = userSeller.getBalance();
        BigDecimal price = order.getPrice();
        BigDecimal newUserSellerBalance = userSellerBalance.add(price);
        userSeller.setBalance(newUserSellerBalance);
        order.setStatus(OrderStatus.CLOSED);
        orderMessageService.addSystemMessage(order, user, EventType.ORDER_CONFIRMED);
    }

    @Transactional
    public void adminOrderRefund(Long orderId, Principal principal) {
        if (orderId < 1) throw new IllegalArgumentException("Невалидный номер заказа");
        Optional<User> u = userRepository.findByUsername(principal.getName());
        Optional<Order> o = orderRepository.findLockedById(orderId);
        if (o.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (u.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        Order order = o.get();
        User user = u.get();

        if (user.getRole() != UserRole.ADMIN) throw new NoPermissionsException("У вас нет прав");

        OrderStatus orderStatus = order.getStatus();
        User userBuyer = order.getBuyer();

        if (orderStatus != OrderStatus.OPENED)
            throw new OrderNotOpenedException("Невозможно вернуть деньги на закрытом заказе!");

        BigDecimal userBuyerBalance = userBuyer.getBalance();
        BigDecimal price = order.getPrice();
        BigDecimal newUserBuyerBalance = userBuyerBalance.add(price);
        userBuyer.setBalance(newUserBuyerBalance);
        order.setStatus(OrderStatus.REFUND);
        orderMessageService.addSystemMessage(order, user, EventType.ORDER_REFUNDED);
    }
}
