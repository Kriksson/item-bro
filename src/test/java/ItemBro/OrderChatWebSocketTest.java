package ItemBro;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.Order.DTO.AddMessageResult;
import ItemBro.Order.Chat.OrderChatSessionState;
import ItemBro.Order.Chat.OrderChatWebSocketHandler;
import ItemBro.Order.DTO.MessageBody;
import ItemBro.Order.Order;
import ItemBro.Order.OrderMessageService;
import ItemBro.Order.OrderRepository;
import ItemBro.Order.OrderStatus;
import ItemBro.Order.OrderService;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderChatWebSocketTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String PASSWORD = "Strong123!";
    private static final String ORIGIN = "http://localhost:5173";

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired AdviceRepository advices;
    @Autowired OrderRepository orders;
    @Autowired OrderMessageService messages;
    @Autowired OrderService orderService;
    @Autowired OrderChatWebSocketHandler handler;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ServletWebServerApplicationContext context;

    private final List<Account> accounts = new ArrayList<>();
    private final List<ChatSocket> sockets = new ArrayList<>();
    private final List<WebSocketSession> observers = new ArrayList<>();
    private HttpClient transport;
    private Order order;
    private Order otherOrder;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE order_messages, orders, advices, games, users RESTART IDENTITY");
        String hash = passwords.encode(PASSWORD);
        User buyer = users.save(new User("buyer", hash, new BigDecimal("100")));
        User seller = users.save(new User("seller", hash, BigDecimal.ZERO));
        User outsider = users.save(new User("outsider", hash, BigDecimal.ZERO));
        User admin = new User("admin", hash, BigDecimal.ZERO);
        admin.setRole(UserRole.ADMIN);
        users.save(admin);
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller,
                new BigDecimal("25"), AdviceStatus.ACTIVE));
        order = orders.save(new Order(advice, buyer, seller, advice.getPrice(), OrderStatus.OPENED));
        otherOrder = orders.save(new Order(advice, outsider, seller, advice.getPrice(), OrderStatus.OPENED));
        transport = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        sockets.forEach(socket -> socket.connection.abort());
        observers.forEach(session -> {
            try { handler.afterConnectionClosed(session, CloseStatus.NORMAL); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        if (transport != null) transport.close();
        accounts.forEach(Account::close);
        jdbc.execute("ALTER TABLE order_messages DROP CONSTRAINT IF EXISTS review_unique_text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"buyer", "seller", "admin"})
    @DisplayName("WS: участники и администратор подключаются по сессионной cookie без Basic")
    void participantsAndAdminConnectWithSessionCookie(String username) throws Exception {
        ChatSocket socket = connect(login(username), order.getId());
        assertThat(socket.connection.isInputClosed()).isFalse();
        assertThat(socket.closed).isNotDone();
    }

    @Test
    @DisplayName("WS: гость — 401, посторонний — 403, отсутствующий заказ — 404, чужой Origin — 403")
    void rejectsUnauthorizedForbiddenMissingAndForeignOrigin() throws Exception {
        assertHandshakeStatus(null, order.getId().toString(), ORIGIN, 401);
        assertHandshakeStatus(login("outsider"), order.getId().toString(), ORIGIN, 403);
        Account buyer = login("buyer");
        assertHandshakeStatus(buyer, Long.toString(Long.MAX_VALUE), ORIGIN, 404);
        assertHandshakeStatus(buyer, order.getId().toString(), "https://untrusted.invalid", 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    @DisplayName("WS: невалидный ID заказа возвращает 400")
    void rejectsInvalidOrderId(String id) throws Exception {
        assertHandshakeStatus(login("buyer"), id, ORIGIN, 400);
    }

    @Test
    @DisplayName("WS: отозванная в БД роль ADMIN не сохраняет доступ через старую сессию")
    void revokedAdminRoleCannotSubscribeToForeignOrder() throws Exception {
        Account admin = login("admin");
        jdbc.update("UPDATE users SET role='USER', version=version+1 WHERE username='admin'");
        assertHandshakeStatus(admin, order.getId().toString(), ORIGIN, 403);
    }

    @Test
    @DisplayName("WS: POST рассылается участникам и администратору только нужного заказа; повтор не дублируется")
    void deliversSavedMessageToAllOrderSubscribersWithoutRetryDuplicates() throws Exception {
        Account buyer = login("buyer");
        List<ChatSocket> recipients = List.of(connect(buyer, order.getId()),
                connect(login("seller"), order.getId()), connect(login("admin"), order.getId()));
        ChatSocket unrelated = connect(login("outsider"), otherOrder.getId());
        UUID requestId = UUID.randomUUID();
        HttpResponse<String> response = buyer.post(messagePath(), new MessageBody("  Привет  ", requestId));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode saved = mapper.readTree(response.body());
        for (ChatSocket recipient : recipients) {
            JsonNode frame = recipient.nextMessage();
            assertThat(frame.path("type").asText()).isEqualTo("message.created");
            assertThat(frame.path("message")).isEqualTo(saved);
            assertThat(frame.path("message").path("text").asText()).isEqualTo("Привет");
            assertThat(frame.path("message").path("sender").has("passwordHash")).isFalse();
        }
        unrelated.assertNoMessage();
        assertThat(buyer.post(messagePath(), new MessageBody("Привет", requestId)).statusCode()).isEqualTo(200);
        for (ChatSocket recipient : recipients) recipient.assertNoMessage();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CLOSED", "REFUND", "ARCHIVED"})
    @DisplayName("WS: завершение заказа не запрещает подключение и переписку покупателя и продавца")
    void participantsCanChatAfterOrderCompletion(OrderStatus status) throws Exception {
        order.setStatus(status);
        orders.save(order);
        Account buyer = login("buyer");
        Account seller = login("seller");
        ChatSocket buyerSocket = connect(buyer, order.getId());
        ChatSocket sellerSocket = connect(seller, order.getId());
        for (Account sender : List.of(buyer, seller)) {
            var response = sender.post(messagePath(), new MessageBody("После сделки", UUID.randomUUID()));
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(buyerSocket.nextMessage().path("message")).isEqualTo(mapper.readTree(response.body()));
            assertThat(sellerSocket.nextMessage().path("message")).isEqualTo(mapper.readTree(response.body()));
        }
    }

    @Test
    @DisplayName("WS: logout закрывает все вкладки своей сессии, сохраняя другие входы, старая cookie — 401")
    void logoutClosesOnlyItsHttpSessionAcrossOrders() throws Exception {
        Account buyer = login("buyer");
        ChatSocket firstTab = connect(buyer, order.getId());
        Order second = orders.save(new Order(order.getAdvice(), order.getBuyer(), order.getSeller(),
                order.getPrice(), OrderStatus.CLOSED));
        ChatSocket secondTab = connect(buyer, second.getId());
        ChatSocket sellerTab = connect(login("seller"), order.getId());
        Account independentBuyer = login("buyer");
        ChatSocket independentTab = connect(independentBuyer, order.getId());

        assertThat(buyer.post("/api/logout", null).statusCode()).isEqualTo(204);
        assertThat(firstTab.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(secondTab.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(sellerTab.closed).isNotDone();
        assertThat(independentTab.closed).isNotDone();
        assertHandshakeStatus(buyer, order.getId().toString(), ORIGIN, 401);
        var response = independentBuyer.post(messagePath(), new MessageBody("Другой вход активен", UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(sellerTab.nextMessage().path("message")).isEqualTo(mapper.readTree(response.body()));
        assertThat(independentTab.nextMessage().path("message")).isEqualTo(mapper.readTree(response.body()));
        firstTab.assertNoMessage();
    }

    @Test
    @DisplayName("WS: таймаут настоящей сессии Tomcat закрывает её сокет, сохраняя другую сессию")
    void expiredHttpSessionClosesItsWebSocket() throws Exception {
        Account buyer = login("buyer");
        ChatSocket buyerSocket = connect(buyer, order.getId());
        ChatSocket sellerSocket = connect(login("seller"), order.getId());
        org.apache.catalina.Session session = tomcatSession(buyer.sessionId);
        session.setMaxInactiveInterval(1);
        // Проверка isValid запускает штатное истечение по неактивности, без ручного invalidate.
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).until(() -> !session.isValid());
        assertThat(buyerSocket.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(sellerSocket.closed).isNotDone();
        assertHandshakeStatus(buyer, order.getId().toString(), ORIGIN, 401);
    }

    @Test
    @DisplayName("WS: текст из сокета отклоняется без сохранения сообщения — отправка идёт через POST")
    void rejectsMessagesSentDirectlyThroughWebSocket() throws Exception {
        ChatSocket socket = connect(login("buyer"), order.getId());
        socket.connection.sendText("Обход POST", true).get(5, TimeUnit.SECONDS);
        assertThat(socket.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isZero();
    }

    @Test
    @DisplayName("Транзакция: рассылка ждёт коммита, в момент доставки строка видна независимому соединению")
    void deliversOnlyAfterCommit() throws Exception {
        List<JsonNode> frames = observeOrder();
        List<Boolean> committedAtDelivery = new ArrayList<>();
        WebSocketSession observer = observers.getFirst();
        doAnswer(invocation -> {
            JsonNode frame = mapper.readTree(((TextMessage) invocation.getArgument(0)).getPayload());
            committedAtDelivery.add(visibleCount(frame.path("message").path("id").asLong()) == 1);
            frames.add(frame);
            return null;
        }).when(observer).sendMessage(any());
        AddMessageResult result = new TransactionTemplate(transactionManager).execute(transaction -> {
            var created = messages.addMessage(order.getId(), () -> "buyer", new MessageBody("Коммит", UUID.randomUUID()));
            assertThat(frames).isEmpty();
            try { assertThat(visibleCount(created.message().id())).isZero(); }
            catch (SQLException e) { throw new RuntimeException(e); }
            return created;
        });
        assertThat(result).isNotNull();
        assertThat(frames).hasSize(1);
        assertThat(committedAtDelivery).containsExactly(true);
    }

    @Test
    @DisplayName("Транзакция: откат отменяет сохранение и рассылку уже опубликованного события")
    void rollbackDoesNotDeliverPublishedEvent() throws Exception {
        List<JsonNode> frames = observeOrder();
        UUID requestId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            messages.addMessage(order.getId(), () -> "buyer", new MessageBody("Откат", requestId));
            assertThat(frames).isEmpty();
            transaction.setRollbackOnly();
        });
        assertThat(frames).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages WHERE client_message_id=?", Long.class, requestId)).isZero();
    }

    @Test
    @DisplayName("Транзакция: ошибка непосредственно при коммите не выпускает событие в WS")
    void failedCommitDoesNotDeliverPublishedEvent() throws Exception {
        List<JsonNode> frames = observeOrder();
        messages.addMessage(order.getId(), () -> "buyer", new MessageBody("Одинаковый текст", UUID.randomUUID()));
        jdbc.execute("ALTER TABLE order_messages ADD CONSTRAINT review_unique_text UNIQUE(text) DEFERRABLE INITIALLY DEFERRED");
        UUID requestId = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var created = messages.addMessage(order.getId(), () -> "buyer", new MessageBody("Одинаковый текст", requestId));
            assertThat(created.result()).isTrue();
            assertThat(frames).hasSize(1);
        })).satisfies(error -> {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23505");
        });
        assertThat(frames).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages WHERE client_message_id=?", Long.class, requestId)).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "confirm,buyer,OPENED,ORDER_CONFIRMED", "cancel,seller,OPENED,ORDER_CANCELED",
            "admin/reopen,admin,CLOSED,ORDER_REOPENED", "admin/confirm,admin,OPENED,ORDER_CONFIRMED",
            "admin/refund,admin,OPENED,ORDER_REFUNDED", "feedback,buyer,OPENED,FEEDBACK_ADDED",
            "feedback,admin,CLOSED,FEEDBACK_ADDED", "response,seller,REFUND,SELLER_RESPONSE_ADDED",
            "response,admin,OPENED,SELLER_RESPONSE_ADDED"})
    @DisplayName("Системное событие: реальный HTTP-запрос доставляет сохранённый DTO всем участникам только своего заказа")
    void systemActionBroadcastsPersistedMessage(String action, String actor, OrderStatus initial, String eventType) throws Exception {
        prepareSystemAction(action, initial);
        Account buyer = login("buyer");
        Account seller = login("seller");
        Account admin = login("admin");
        List<ChatSocket> recipients = List.of(connect(buyer, order.getId()), connect(seller, order.getId()), connect(admin, order.getId()));
        ChatSocket unrelated = connect(login("outsider"), otherOrder.getId());
        Account sender = switch (actor) {
            case "buyer" -> buyer;
            case "seller" -> seller;
            default -> admin;
        };
        String path = "/api/orders/" + order.getId() + "/" + action;
        if (action.startsWith("admin/"))
            path = "/api/admin/orders/" + order.getId() + "/" + action.substring("admin/".length());
        Object body = null;
        if (action.equals("feedback")) body = Map.of("rating", 5, "feedbackText", "Всё хорошо");
        if (action.equals("response")) body = Map.of("responseText", "Спасибо за отзыв");
        assertThat(sender.patch(path, body).statusCode()).isEqualTo(204);
        var historyResponse = buyer.get("/api/orders/" + order.getId() + "/messages");
        assertThat(historyResponse.statusCode()).isEqualTo(200);
        JsonNode history = mapper.readTree(historyResponse.body());
        assertThat(history.path("items").size()).isEqualTo(1);
        JsonNode message = history.path("items").get(0);
        assertThat(message.path("messageType").asText()).isEqualTo("SYSTEM");
        assertThat(message.path("eventType").asText()).isEqualTo(eventType);
        assertThat(message.path("sender").path("username").asText()).isEqualTo(actor);
        assertThat(message.path("sender").path("userRole").asText()).isEqualTo(actor.equals("admin") ? "ADMIN" : "USER");
        assertThat(message.path("sender").has("passwordHash")).isFalse();
        assertThat(visibleCount(message.path("id").asLong())).isEqualTo(1);
        for (ChatSocket recipient : recipients) {
            JsonNode frame = recipient.nextMessage();
            assertThat(frame.path("type").asText()).isEqualTo("message.created");
            assertSameStoredMessage(frame.path("message"), message);
            recipient.assertNoMessage();
            assertThat(recipient.closed).isNotDone();
        }
        unrelated.assertNoMessage();
        // Смена статуса не разрывает существующий чат.
        var reply = seller.post(messagePath(), new MessageBody("Можно продолжать общение", UUID.randomUUID()));
        assertThat(reply.statusCode()).isEqualTo(201);
        for (ChatSocket recipient : recipients)
            assertThat(recipient.nextMessage().path("message")).isEqualTo(mapper.readTree(reply.body()));
        unrelated.assertNoMessage();
    }

    @Test
    @DisplayName("Системное событие ждёт коммита действия и доставляется после видимости записи другому соединению")
    void systemMessageIsDeliveredOnlyAfterActionCommits() throws Exception {
        List<JsonNode> frames = observeOrder();
        List<Boolean> committedAtDelivery = new ArrayList<>();
        doAnswer(invocation -> {
            JsonNode frame = mapper.readTree(((TextMessage) invocation.getArgument(0)).getPayload());
            committedAtDelivery.add(visibleCount(frame.path("message").path("id").asLong()) == 1);
            frames.add(frame);
            return null;
        }).when(observers.getFirst()).sendMessage(any());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            orderService.confirmOrder(() -> "buyer", order.getId());
            long id = jdbc.queryForObject("SELECT id FROM order_messages", Long.class);
            assertThat(frames).isEmpty();
            try { assertThat(visibleCount(id)).isZero(); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
        assertThat(committedAtDelivery).containsExactly(true);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().path("message").path("eventType").asText()).isEqualTo("ORDER_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.getId())).isEqualTo("CLOSED");
        assertThat(users.findByUsername("seller").orElseThrow().getBalance()).isEqualByComparingTo("25");
    }

    @ParameterizedTest
    @CsvSource({"confirm,OPENED", "cancel,OPENED", "admin/reopen,CLOSED", "admin/confirm,OPENED",
            "admin/refund,OPENED", "feedback,OPENED", "response,OPENED"})
    @DisplayName("Откат действия отменяет изменения заказа, баланса и системную запись без отправки WS")
    void systemActionRollbackRevertsOrderBalanceAndMessage(String action, OrderStatus initial) throws Exception {
        prepareSystemAction(action, initial);
        List<JsonNode> frames = observeOrder();
        var before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", order.getId());
        var balances = jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            performServiceAction(action);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isEqualTo(1);
            assertThat(frames).isEmpty();
            tx.setRollbackOnly();
        });
        assertThat(frames).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?", order.getId())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id")).isEqualTo(balances);
    }

    @Test
    @DisplayName("Ошибка коммита действия откатывает деньги, статус и системную запись, не выпуская событие")
    void systemMessageIsNotBroadcastIfActionCommitFails() throws Exception {
        jdbc.update("""
                INSERT INTO order_messages(order_id, sender_id, text, sent_at, client_message_id)
                VALUES (?, ?, 'Выполнение заказа подтверждено', now(), ?)
                """, order.getId(), order.getBuyer().getId(), UUID.randomUUID());
        jdbc.execute("ALTER TABLE order_messages ADD CONSTRAINT review_unique_text UNIQUE(text) DEFERRABLE INITIALLY DEFERRED");
        List<JsonNode> frames = observeOrder();
        var balances = jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            orderService.confirmOrder(() -> "buyer", order.getId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isEqualTo(2);
            assertThat(frames).isEmpty();
        })).satisfies(error -> {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23505");
        });
        assertThat(frames).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.getId())).isEqualTo("OPENED");
        assertThat(jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id")).isEqualTo(balances);
    }

    private void prepareSystemAction(String action, OrderStatus initial) {
        order.setStatus(initial);
        order = orders.save(order);
        if (action.equals("response"))
            jdbc.update("UPDATE orders SET rating=5, feedback='Отзыв' WHERE id=?", order.getId());
    }

    private void assertSameStoredMessage(JsonNode delivered, JsonNode stored) {
        // Java сохраняет наносекунды, PostgreSQL округляет время до микросекунд.
        assertThat(Duration.between(Instant.parse(delivered.path("sentAt").asText()),
                Instant.parse(stored.path("sentAt").asText())).abs()).isLessThan(Duration.ofNanos(1000));
        ObjectNode deliveredFields = delivered.deepCopy();
        ObjectNode storedFields = stored.deepCopy();
        deliveredFields.remove("sentAt");
        storedFields.remove("sentAt");
        assertThat(deliveredFields).isEqualTo(storedFields);
    }

    private void performServiceAction(String action) {
        switch (action) {
            case "confirm" -> orderService.confirmOrder(() -> "buyer", order.getId());
            case "cancel" -> orderService.cancelOrder(() -> "seller", order.getId());
            case "admin/reopen" -> orderService.adminOrderReopen(order.getId(), () -> "admin");
            case "admin/confirm" -> orderService.adminOrderConfirm(order.getId(), () -> "admin");
            case "admin/refund" -> orderService.adminOrderRefund(order.getId(), () -> "admin");
            case "feedback" -> orderService.addFeedback(() -> "buyer", order.getId(), 5, "Отзыв");
            case "response" -> orderService.addSellerResponse(() -> "seller", order.getId(), "Ответ");
            default -> throw new IllegalArgumentException(action);
        }
    }

    private List<JsonNode> observeOrder() throws Exception {
        List<JsonNode> frames = new ArrayList<>();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("orderId", order.getId(),
                OrderChatSessionState.ATTRIBUTE, new OrderChatSessionState(handler)));
        doAnswer(invocation -> {
            frames.add(mapper.readTree(((TextMessage) invocation.getArgument(0)).getPayload()));
            return null;
        }).when(session).sendMessage(any());
        observers.add(session);
        handler.afterConnectionEstablished(session);
        return frames;
    }

    private long visibleCount(long id) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM order_messages WHERE id=?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private org.apache.catalina.Session tomcatSession(String id) throws Exception {
        var server = (TomcatWebServer) context.getWebServer();
        for (var child : server.getTomcat().getHost().findChildren()) {
            if (child instanceof org.apache.catalina.Context servletContext) {
                var session = servletContext.getManager().findSession(id);
                if (session != null) return session;
            }
        }
        throw new AssertionError("HTTP-сессия не найдена");
    }

    private String messagePath() { return "/api/orders/" + order.getId() + "/messages"; }

    private Account login(String username) throws Exception {
        Account account = new Account(username);
        accounts.add(account);
        return account;
    }

    private ChatSocket connect(Account account, long id) throws Exception {
        return connect(account, Long.toString(id), ORIGIN);
    }

    private ChatSocket connect(Account account, String id, String origin) throws Exception {
        ChatSocket socket = new ChatSocket();
        var builder = transport.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("Origin", origin);
        if (account != null) builder.header("Cookie", account.cookie);
        socket.connection = builder.buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/orders/" + id + "/chat/ws"), socket)
                .get(8, TimeUnit.SECONDS);
        sockets.add(socket);
        return socket;
    }

    private void assertHandshakeStatus(Account account, String id, String origin, int status) {
        assertThatThrownBy(() -> connect(account, id, origin)).isInstanceOf(ExecutionException.class)
                .satisfies(error -> {
                    assertThat(error.getCause()).isInstanceOf(WebSocketHandshakeException.class);
                    assertThat(((WebSocketHandshakeException) error.getCause()).getResponse().statusCode()).isEqualTo(status);
                });
    }

    private final class Account implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        final String sessionId;
        final String cookie;

        Account(String username) throws Exception {
            String basic = Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            var response = http.send(HttpRequest.newBuilder(uri("/api/user/me"))
                    .header("Authorization", "Basic " + basic).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            sessionId = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
            cookie = "JSESSIONID=" + sessionId;
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return request("POST", path, body);
        }

        HttpResponse<String> patch(String path, Object body) throws Exception {
            return request("PATCH", path, body);
        }

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> request(String method, String path, Object body) throws Exception {
            var csrfResponse = http.send(HttpRequest.newBuilder(uri("/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(csrfResponse.statusCode()).isEqualTo(200);
            JsonNode csrf = mapper.readTree(csrfResponse.body());
            String json = body == null ? "" : mapper.writeValueAsString(body);
            return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                    .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
        @Override public void close() { http.close(); }
    }

    private final class ChatSocket implements WebSocket.Listener {
        WebSocket connection;
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) { frames.add(partial.toString()); partial.setLength(0); }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code);
            return WebSocket.Listener.super.onClose(socket, code, reason);
        }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
        JsonNode nextMessage() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("Ожидается сообщение по WS").isNotNull();
            return mapper.readTree(frame);
        }
        void assertNoMessage() throws InterruptedException { assertThat(frames.poll(200, TimeUnit.MILLISECONDS)).isNull(); }
    }
}
