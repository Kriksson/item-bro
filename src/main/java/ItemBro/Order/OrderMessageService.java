package ItemBro.Order;

import ItemBro.Exception.*;
import ItemBro.Order.Chat.OrderMessageCreatedEvent;
import ItemBro.Order.DTO.AddMessageResult;
import ItemBro.Order.DTO.MessageBody;
import ItemBro.Order.DTO.MessageDTO;
import ItemBro.Order.DTO.MessagesDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Service
public class OrderMessageService {

    private final ApplicationEventPublisher publisher;
    private final OrderMessageRepository orderMessageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderMessageService(OrderRepository orderRepository, UserRepository userRepository,
                               OrderMessageRepository orderMessageRepository, ApplicationEventPublisher publisher) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderMessageRepository = orderMessageRepository;
        this.publisher = publisher;
    }

    @Transactional
    public AddMessageResult addMessage(Long id, Principal principal, MessageBody messageBody) {
        Optional<Order> orderOptional = orderRepository.findLockedById(id);
        Optional<User> userOptional = userRepository.findByUsername(principal.getName());
        if (orderOptional.isEmpty()) throw new OrderNotFoundException("Заказ не найден");
        if (userOptional.isEmpty()) throw new UserNotFoundException("Пользователь не найден");

        User user = userOptional.get();
        Order order = orderOptional.get();
        OrderStatus orderStatus = order.getStatus();
        User userBuyer = order.getBuyer();
        User userSeller = order.getSeller();

        if (user.getRole() != UserRole.ADMIN) {
            if (!user.getId().equals(userBuyer.getId()) && !user.getId().equals(userSeller.getId())) {
                throw new NoPermissionsException("Нет прав для отправки сообщения");
            }
        }

        Optional<OrderMessage> orderMessageOptional =
                orderMessageRepository.findByOrder_IdAndSender_IdAndClientMessageId(
                        order.getId(), user.getId(), messageBody.clientMessageId());

        String text = messageBody.text().trim();

        if (orderMessageOptional.isPresent()) {
            OrderMessage orderMessage = orderMessageOptional.get();

            if (!orderMessage.getText().equals(text)) {
                throw new IllegalMessageRequestException("Текста двух одинаковых запросов отличаются");
            } else {
                return new AddMessageResult(new MessageDTO(orderMessage, order), false);
            }
        }

        OrderMessage newMessage = new OrderMessage(order, user, text, messageBody.clientMessageId());
        OrderMessage messageFromDB = orderMessageRepository.save(newMessage);
        MessageDTO messageDTO = new MessageDTO(messageFromDB, messageFromDB.getOrder());

        publisher.publishEvent(new OrderMessageCreatedEvent(messageDTO));

        return new AddMessageResult(messageDTO, true);
    }

    @Transactional(readOnly = true)
    public MessagesDTO getMessages(Principal principal, Long orderId, Integer limit, Long beforeId, Long afterId) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Невалидный лимит");
        if (orderId < 1) throw new IllegalArgumentException("Невалидный заказ");
        if (beforeId != null && afterId != null) {
            throw new IllegalArgumentException(
                    "Одновременно два курсора передать нельзя"
            );
        }

        Optional<User> userOptional = userRepository.findByUsername(principal.getName());
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (userOptional.isEmpty()) throw new UserNotFoundException("Пользователь не найден");
        if (orderOptional.isEmpty()) throw new OrderNotFoundException("Заказ не найден");

        User user = userOptional.get();
        Order order = orderOptional.get();

        User userSeller = order.getSeller();
        User userBuyer = order.getBuyer();

        if (user.getRole() != UserRole.ADMIN) {
            if (!user.getId().equals(userBuyer.getId()) && !user.getId().equals(userSeller.getId()))
                throw new NoPermissionsException("Нет доступа");
        }

        Slice<OrderMessage> orderMessageSlice;
        Pageable pageable = PageRequest.of(0, limit);

        if (afterId != null) {
            if (afterId < 0) throw new IllegalArgumentException("afterId не может быть меньше нуля");

            orderMessageSlice =
                    orderMessageRepository.findByOrder_IdAndIdGreaterThanOrderByIdAsc(orderId, afterId, pageable);
        } else if (beforeId != null) {
            if (beforeId < 1) throw new IllegalArgumentException(("beforeId не может быть меньше единицы"));
            orderMessageSlice =
                    orderMessageRepository.findByOrder_IdAndIdLessThanOrderByIdDesc(orderId, beforeId, pageable);
        } else {
            orderMessageSlice =
                    orderMessageRepository.findByOrder_IdOrderByIdDesc(orderId, pageable);
        }

        Slice<MessageDTO> messageDTOSlice = orderMessageSlice.map(e -> new MessageDTO(e, order));

        List<MessageDTO> items = messageDTOSlice.getContent();
        if (afterId == null) {
            items = items.reversed();
        }

        return new MessagesDTO(items, messageDTOSlice.hasNext());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void addSystemMessage(Order order, User user, EventType eventType) {
        String text = switch (eventType) {
            case ORDER_CREATED -> "Заказ создан";
            case ORDER_CONFIRMED -> "Выполнение заказа подтверждено";
            case ORDER_CANCELED -> "Заказ отменён";
            case ORDER_REOPENED -> "Заказ повторно открыт";
            case ORDER_REFUNDED -> "Деньги возвращены покупателю";
            case FEEDBACK_ADDED -> "Оставлен отзыв по заказу";
            case FEEDBACK_UPDATED -> "Обновлен отзыв по заказу";
            case SELLER_RESPONSE_ADDED -> "Ответ на отзыв добавлен";
            case SELLER_RESPONSE_UPDATED -> "Ответ на отзыв обновлен";
        };
        OrderMessage orderMessage = new OrderMessage(order, user, text, MessageType.SYSTEM, eventType);
        OrderMessage messageFromDB = orderMessageRepository.save(orderMessage);

        MessageDTO messageDTO = new MessageDTO(messageFromDB, messageFromDB.getOrder());
        publisher.publishEvent(new OrderMessageCreatedEvent(messageDTO));
    }
}
