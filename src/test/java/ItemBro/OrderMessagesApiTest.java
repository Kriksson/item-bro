package ItemBro;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.Order.Order;
import ItemBro.Order.EventType;
import ItemBro.Order.OrderMessageService;
import ItemBro.Order.OrderRepository;
import ItemBro.Order.OrderStatus;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderMessagesApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("item_bro_messages_test")
            .withUsername("test")
            .withPassword("test");

    private static final BigDecimal PRICE = new BigDecimal("25.00");
    private static final UUID REQUEST_ID = UUID.fromString("51ad99e7-f9e6-4141-a2ab-8d4a021807af");
    private static final Instant SENT_AT = Instant.parse("2026-10-18T10:00:00Z");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired AdviceRepository advices;
    @Autowired OrderRepository orders;
    @Autowired OrderMessageService messageService;
    @Autowired PlatformTransactionManager transactionManager;

    private User buyer;
    private User seller;
    private User outsider;
    private User admin;
    private Order order;
    private Order otherOrder;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE order_messages, orders, advices, games, users RESTART IDENTITY");
        buyer = savedUser("alex", UserRole.USER);
        seller = savedUser("maria", UserRole.USER);
        outsider = savedUser("ivan", UserRole.USER);
        admin = savedUser("admin", UserRole.ADMIN);
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));
        order = orders.save(new Order(advice, buyer, seller, PRICE, OrderStatus.OPENED));
        otherOrder = orders.save(new Order(advice, outsider, seller, PRICE, OrderStatus.OPENED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex", "maria", "admin"})
    @DisplayName("Отправка: покупатель, продавец и администратор получают DTO сохранённого сообщения")
    void participantsAndAdminCanSendMessages(String username) throws Exception {
        User sender = users.findByUsername(username).orElseThrow();
        JsonNode response = responseBody(mvc.perform(send(order, sender, "  Добрый день!  ", REQUEST_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(order.getId().intValue()))
                .andExpect(jsonPath("$.messageType").value("USER"))
                .andExpect(jsonPath("$.eventType").doesNotExist())
                .andExpect(jsonPath("$.sender.username").value(username))
                .andExpect(jsonPath("$.sender.userRole").value(sender.getRole().name()))
                .andExpect(jsonPath("$.sender.avatarUrl").value(sender.getAvatarUrl()))
                .andExpect(jsonPath("$.sender.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.text").value("Добрый день!"))
                .andReturn());

        Instant sentAt = Instant.parse(response.path("sentAt").asText());
        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM order_messages WHERE id = ?",
                response.path("id").asLong());
        assertThat(stored.get("order_id")).isEqualTo(order.getId());
        assertThat(stored.get("sender_id")).isEqualTo(sender.getId());
        assertThat(stored.get("client_message_id")).isEqualTo(REQUEST_ID);
        assertThat(stored.get("message_type")).isEqualTo("USER");
        assertThat(stored.get("event_type")).isNull();
        assertThat(stored.get("text")).isEqualTo("Добрый день!");
        assertThat(Duration.between(((Timestamp) stored.get("sent_at")).toInstant(), sentAt).abs())
                .isLessThan(Duration.ofMillis(1));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Отправка: без авторизации — 401, без CSRF — 403, чужой заказ — 403")
    void rejectsUnauthorizedAndForbiddenSends() throws Exception {
        String body = messageBody("Привет", REQUEST_ID);
        mvc.perform(post("/api/orders/{id}/messages", order.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/{id}/messages", order.getId()).with(user(buyer.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // Роль берётся из БД: одной authority ADMIN у запроса недостаточно.
        mvc.perform(send(order, outsider, "Привет", REQUEST_ID)
                        .with(user(outsider.getUsername()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertThat(messageCount()).isZero();
    }

    @Test
    @DisplayName("Отправка: несуществующий заказ — 404")
    void cannotSendToMissingOrder() throws Exception {
        mvc.perform(post("/api/orders/{id}/messages", Long.MAX_VALUE)
                        .with(user(buyer.getUsername())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(messageBody("Привет", REQUEST_ID)))
                .andExpect(status().isNotFound());
        assertThat(messageCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    @DisplayName("Отправка: невалидные текст, UUID и JSON — 400 без сохранения")
    void rejectsInvalidMessageBody(String body) throws Exception {
        mvc.perform(post("/api/orders/{id}/messages", order.getId())
                        .with(user(buyer.getUsername())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
    }

    static Stream<String> invalidMessages() {
        return Stream.of(
                "{\"clientMessageId\":\"%s\"}".formatted(REQUEST_ID),
                "{\"text\":null,\"clientMessageId\":\"%s\"}".formatted(REQUEST_ID),
                "{\"text\":\"\",\"clientMessageId\":\"%s\"}".formatted(REQUEST_ID),
                "{\"text\":\"  \\n\\t  \",\"clientMessageId\":\"%s\"}".formatted(REQUEST_ID),
                "{\"text\":\"%s\",\"clientMessageId\":\"%s\"}".formatted("a".repeat(1001), REQUEST_ID),
                "{\"text\":\"Привет\"}",
                "{\"text\":\"Привет\",\"clientMessageId\":null}",
                "{\"text\":\"Привет\",\"clientMessageId\":\"not-a-uuid\"}",
                "{broken-json");
    }

    @Test
    @DisplayName("Отправка: текст длиной 1000 символов допустим")
    void acceptsMaximumTextLength() throws Exception {
        String text = "я".repeat(1000);
        mvc.perform(send(order, buyer, text, REQUEST_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value(text));
        assertThat(messageCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "OPENED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Отправка: покупатель, продавец и администратор могут писать после завершения заказа")
    void canSendNewMessagesAfterOrderStopsBeingOpened(OrderStatus orderStatus) throws Exception {
        changeStatus(orderStatus);
        for (User sender : List.of(buyer, seller, admin)) {
            mvc.perform(send(order, sender, "Привет", REQUEST_ID))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sender.username").value(sender.getUsername()));
        }
        assertThat(messageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Повторная отправка: тот же UUID и нормализованный текст возвращают прежнее сообщение")
    void retryReturnsExistingMessageWithoutDuplicate() throws Exception {
        JsonNode first = responseBody(mvc.perform(send(order, buyer, "  Привет  ", REQUEST_ID))
                .andExpect(status().isCreated()).andReturn());
        JsonNode retry = responseBody(mvc.perform(send(order, buyer, "Привет", REQUEST_ID))
                .andExpect(status().isOk()).andReturn());
        assertThat(retry.path("id")).isEqualTo(first.path("id"));
        assertThat(retry.path("text").asText()).isEqualTo("Привет");
        assertThat(messageCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "OPENED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Повторная отправка: уже принятое сообщение возвращается и после закрытия заказа")
    void retryStillWorksAfterOrderStopsBeingOpened(OrderStatus orderStatus) throws Exception {
        JsonNode first = responseBody(mvc.perform(send(order, buyer, "Привет", REQUEST_ID))
                .andExpect(status().isCreated()).andReturn());
        changeStatus(orderStatus);
        mvc.perform(send(order, buyer, "Привет", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.path("id").asInt()));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная отправка: другой текст с прежним UUID — 409, исходный текст сохраняется")
    void conflictingRetryDoesNotOverwriteMessage() throws Exception {
        mvc.perform(send(order, buyer, "Первый текст", REQUEST_ID)).andExpect(status().isCreated());
        mvc.perform(send(order, buyer, "Другой текст", REQUEST_ID)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT text FROM order_messages", String.class)).isEqualTo("Первый текст");
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная отправка: UUID независим для разных отправителей и заказов")
    void requestIdIsScopedToOrderAndSender() throws Exception {
        JsonNode first = responseBody(mvc.perform(send(order, seller, "Первый заказ", REQUEST_ID))
                .andExpect(status().isCreated()).andReturn());
        JsonNode anotherSender = responseBody(mvc.perform(send(order, buyer, "Ответ покупателя", REQUEST_ID))
                .andExpect(status().isCreated()).andReturn());
        JsonNode anotherOrder = responseBody(mvc.perform(send(otherOrder, seller, "Другой заказ", REQUEST_ID))
                .andExpect(status().isCreated()).andReturn());
        assertThat(List.of(first.path("id").asLong(), anotherSender.path("id").asLong(),
                anotherOrder.path("id").asLong())).doesNotHaveDuplicates();
        assertThat(anotherSender.path("sender").path("username").asText()).isEqualTo(buyer.getUsername());
        assertThat(anotherOrder.path("orderId").asLong()).isEqualTo(otherOrder.getId());
        assertThat(messageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Повторная отправка: два одновременных запроса дают 201 и 200 с одним сообщением")
    void concurrentRetriesSaveExactlyOneMessage() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.prepareStatement("SELECT id FROM orders WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, order.getId());
                try (var ignored = statement.executeQuery()) {
                    assertThat(ignored.next()).isTrue();
                }
            }

            var first = executor.submit(() -> mvc.perform(send(order, buyer, "Привет", REQUEST_ID)).andReturn());
            var second = executor.submit(() -> mvc.perform(send(order, buyer, "Привет", REQUEST_ID)).andReturn());
            try {
                // Ждём настоящей блокировки обоих запросов в PostgreSQL, затем отпускаем заказ.
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("""
                                SELECT count(*) FROM pg_stat_activity
                                WHERE datname = current_database() AND pid <> pg_backend_pid()
                                  AND wait_event_type = 'Lock'
                                """, Long.class)).isEqualTo(2));
            } finally {
                gate.commit();
            }
            MvcResult firstResult = first.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 200);
            assertThat(responseBody(firstResult).path("id")).isEqualTo(responseBody(secondResult).path("id"));
            assertThat(messageCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("База данных: уникальность UUID в пределах заказа и отправителя защищена ограничением")
    void databaseRejectsDuplicateRequestId() {
        seedMessage(order, buyer, "Привет", REQUEST_ID);
        assertThatThrownBy(() -> seedMessage(order, buyer, "Дубликат", REQUEST_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(messageCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex", "maria", "admin"})
    @DisplayName("История: участники и администратор видят только сообщения нужного заказа")
    void participantsAndAdminCanReadHistory(String username) throws Exception {
        long id = seedMessage(order, seller, "Привет", REQUEST_ID);
        seedMessage(otherOrder, outsider, "Чужая переписка", UUID.randomUUID());
        JsonNode response = responseBody(mvc.perform(history().with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").value(order.getId().intValue()))
                .andExpect(jsonPath("$.items[0].sender.username").value(seller.getUsername()))
                .andExpect(jsonPath("$.items[0].text").value("Привет"))
                .andExpect(jsonPath("$.items[0].sentAt").value(SENT_AT.toString()))
                .andReturn());
        assertPage(response, List.of(id), false);
    }

    @Test
    @DisplayName("История: без авторизации — 401, чужой заказ — 403, отсутствующий заказ — 404")
    void rejectsUnauthorizedForbiddenAndMissingHistory() throws Exception {
        seedMessage(order, seller, "Привет", REQUEST_ID);
        mvc.perform(history()).andExpect(status().isUnauthorized());
        mvc.perform(history().with(user(outsider.getUsername()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/{id}/messages", Long.MAX_VALUE).with(user(buyer.getUsername())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("История: без курсора возвращает последние 50 по возрастанию ID с hasMore")
    void initialPageReturnsLatestFiftyInAscendingOrder() throws Exception {
        List<Long> ids = seedInterleavedMessages(55);
        JsonNode response = readPage(history());
        assertPage(response, ids.subList(5, 55), true);
    }

    @Test
    @DisplayName("История: beforeId возвращает ближайшие предыдущие сообщения и исключает сам курсор")
    void beforeCursorPaginatesBackwardsButReturnsAscendingItems() throws Exception {
        List<Long> ids = seedInterleavedMessages(8);
        JsonNode first = readPage(history().param("beforeId", ids.get(6).toString()).param("limit", "3"));
        assertPage(first, ids.subList(3, 6), true);

        JsonNode last = readPage(history().param("beforeId", ids.get(3).toString()).param("limit", "3"));
        assertPage(last, ids.subList(0, 3), false);
        assertPage(readPage(history().param("beforeId", ids.getFirst().toString())), List.of(), false);
    }

    @Test
    @DisplayName("История: afterId возвращает ближайшие новые сообщения и исключает сам курсор")
    void afterCursorPaginatesForwardsInAscendingOrder() throws Exception {
        List<Long> ids = seedInterleavedMessages(8);
        JsonNode first = readPage(history().param("afterId", ids.get(1).toString()).param("limit", "3"));
        assertPage(first, ids.subList(2, 5), true);

        JsonNode last = readPage(history().param("afterId", ids.get(4).toString()).param("limit", "3"));
        assertPage(last, ids.subList(5, 8), false);
        assertPage(readPage(history().param("afterId", ids.getLast().toString())), List.of(), false);
    }

    @Test
    @DisplayName("История: afterId=0 начинает с самых ранних сообщений")
    void zeroAfterCursorStartsAtOldestMessage() throws Exception {
        List<Long> ids = seedInterleavedMessages(55);
        assertPage(readPage(history().param("afterId", "0")), ids.subList(0, 50), true);
    }

    @Test
    @DisplayName("История: курсор может быть ID сообщения из другого заказа")
    void cursorIsANumericBoundaryNotAnOrderMessageReference() throws Exception {
        long first = seedMessage(order, buyer, "Первое", UUID.randomUUID());
        long foreignId = seedMessage(otherOrder, outsider, "Другая переписка", UUID.randomUUID());
        long last = seedMessage(order, seller, "Последнее", UUID.randomUUID());
        assertPage(readPage(history().param("beforeId", Long.toString(foreignId))), List.of(first), false);
        assertPage(readPage(history().param("afterId", Long.toString(foreignId))), List.of(last), false);
    }

    @Test
    @DisplayName("История: пустой заказ возвращает пустой список и hasMore=false")
    void emptyHistoryHasNoMoreItems() throws Exception {
        seedMessage(otherOrder, outsider, "Другая переписка", REQUEST_ID);
        assertPage(readPage(history()), List.of(), false);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "OPENED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("История: доступна и у неоткрытого заказа")
    void historyRemainsReadableForNonOpenedOrder(OrderStatus orderStatus) throws Exception {
        long id = seedMessage(order, seller, "История заказа", REQUEST_ID);
        changeStatus(orderStatus);
        assertPage(readPage(history()), List.of(id), false);
    }

    @ParameterizedTest
    @CsvSource({"limit,0", "limit,-1", "limit,101", "limit,abc", "beforeId,0", "beforeId,-1",
            "beforeId,abc", "afterId,-1", "afterId,abc"})
    @DisplayName("История: невалидные лимит и курсор — 400")
    void rejectsInvalidHistoryQuery(String name, String value) throws Exception {
        mvc.perform(history().param(name, value).with(user(buyer.getUsername())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("История: одновременно beforeId и afterId — 400")
    void rejectsTwoCursorsAtOnce() throws Exception {
        mvc.perform(history().param("beforeId", "10").param("afterId", "0")
                        .with(user(buyer.getUsername())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    @DisplayName("История: невалидный ID заказа — 400")
    void rejectsInvalidOrderId(String id) throws Exception {
        mvc.perform(get("/api/orders/{id}/messages", id).with(user(buyer.getUsername())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("История: граничные лимиты 1 и 100 допустимы, ровно limit сообщений не означает hasMore")
    void acceptsBoundaryLimitsAndCalculatesHasMore(int limit) throws Exception {
        List<Long> ids = seedInterleavedMessages(limit);
        assertPage(readPage(history().param("limit", Integer.toString(limit))), ids, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex", "admin"})
    @DisplayName("Событие создания: сохраняется вместе с новым заказом, автором становится покупатель")
    void creationSavesSystemMessageWithActualBuyer(String username) throws Exception {
        var result = mvc.perform(post("/api/orders/").with(user(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("id", order.getAdvice().getId()))))
                .andExpect(status().isCreated()).andReturn();
        long orderId = responseBody(result).path("id").asLong();
        assertSystemMessage(orderId, username, EventType.ORDER_CREATED);
        assertThat(users.findByUsername(username).orElseThrow().getBalance()).isEqualByComparingTo("75");
    }

    static Stream<Arguments> statusActions() {
        return Stream.of(
                Arguments.of("/api/orders/{id}/cancel", "maria", OrderStatus.OPENED, EventType.ORDER_CANCELED, OrderStatus.REFUND),
                Arguments.of("/api/orders/{id}/cancel", "admin", OrderStatus.OPENED, EventType.ORDER_CANCELED, OrderStatus.REFUND),
                Arguments.of("/api/orders/{id}/confirm", "alex", OrderStatus.OPENED, EventType.ORDER_CONFIRMED, OrderStatus.CLOSED),
                Arguments.of("/api/orders/{id}/confirm", "admin", OrderStatus.OPENED, EventType.ORDER_CONFIRMED, OrderStatus.CLOSED),
                Arguments.of("/api/admin/orders/{id}/reopen", "admin", OrderStatus.CLOSED, EventType.ORDER_REOPENED, OrderStatus.OPENED),
                Arguments.of("/api/admin/orders/{id}/confirm", "admin", OrderStatus.OPENED, EventType.ORDER_CONFIRMED, OrderStatus.CLOSED),
                Arguments.of("/api/admin/orders/{id}/refund", "admin", OrderStatus.OPENED, EventType.ORDER_REFUNDED, OrderStatus.REFUND));
    }

    static Stream<Arguments> serializedActions() {
        return Stream.concat(statusActions(), Stream.of(
                Arguments.of("/api/orders/{id}/feedback", "alex", OrderStatus.OPENED, EventType.FEEDBACK_ADDED, OrderStatus.OPENED),
                Arguments.of("/api/orders/{id}/response", "maria", OrderStatus.OPENED, EventType.SELLER_RESPONSE_ADDED, OrderStatus.OPENED)));
    }

    @ParameterizedTest
    @MethodSource("statusActions")
    @DisplayName("События статуса: правильный автор и тип, повтор отклоняется без второго сообщения")
    void statusActionSavesOneEvent(String path, String username, OrderStatus initial,
                                 EventType eventType, OrderStatus expected) throws Exception {
        changeStatus(initial);
        mvc.perform(action(path, order.getId(), username)).andExpect(status().isNoContent());
        assertSystemMessage(order.getId(), username, eventType);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(expected);
        var balances = jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id");
        mvc.perform(action(path, order.getId(), username)).andExpect(status().isConflict());
        assertThat(messageCount()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id")).isEqualTo(balances);
    }

    @ParameterizedTest
    @CsvSource({"alex,OPENED", "admin,OPENED", "alex,CLOSED", "admin,CLOSED", "alex,REFUND", "admin,REFUND"})
    @DisplayName("События отзыва: добавление, изменение и повтор одинаковых данных имеют собственного автора")
    void feedbackAddsAndUpdatesEventsIncludingUnchangedRetry(String username, OrderStatus state) throws Exception {
        changeStatus(state);
        String path = "/api/orders/{id}/feedback";
        mvc.perform(action(path, order.getId(), username)).andExpect(status().isNoContent());
        String changed = mapper.writeValueAsString(Map.of("rating", 4, "feedbackText", "Дополненный отзыв"));
        for (int i = 0; i < 2; i++)
            mvc.perform(action(path, order.getId(), username).content(changed)).andExpect(status().isNoContent());
        assertEvents(username, EventType.FEEDBACK_ADDED, EventType.FEEDBACK_UPDATED, EventType.FEEDBACK_UPDATED);
        Order stored = orders.findById(order.getId()).orElseThrow();
        assertThat(stored.getRating()).isEqualTo(4);
        assertThat(stored.getFeedback()).isEqualTo("Дополненный отзыв");
        assertThat(stored.getStatus()).isEqualTo(state);
    }

    @ParameterizedTest
    @CsvSource({"maria,OPENED", "admin,OPENED", "maria,CLOSED", "admin,CLOSED", "maria,REFUND", "admin,REFUND"})
    @DisplayName("События ответа: продавец и администратор добавляют, изменяют и повторно сохраняют ответ")
    void sellerResponseEventsUseActualActorAndNeutralText(String username, OrderStatus state) throws Exception {
        changeStatus(state);
        jdbc.update("UPDATE orders SET rating=5, feedback='Отзыв' WHERE id=?", order.getId());
        String path = "/api/orders/{id}/response";
        mvc.perform(action(path, order.getId(), username)).andExpect(status().isNoContent());
        for (int i = 0; i < 2; i++)
            mvc.perform(action(path, order.getId(), username)
                            .content(mapper.writeValueAsString(Map.of("responseText", "Дополненный ответ"))))
                    .andExpect(status().isNoContent());
        assertEvents(username, EventType.SELLER_RESPONSE_ADDED, EventType.SELLER_RESPONSE_UPDATED, EventType.SELLER_RESPONSE_UPDATED);
        assertThat(jdbc.queryForList("SELECT text FROM order_messages ORDER BY id", String.class))
                .allSatisfy(text -> assertThat(text).doesNotContain("Продавец"));
        assertThat(orders.findById(order.getId()).orElseThrow().getSellerResponse()).isEqualTo("Дополненный ответ");
    }

    @ParameterizedTest
    @CsvSource({
            "/api/orders/{id}/cancel,maria", "/api/orders/{id}/confirm,alex",
            "/api/orders/{id}/feedback,alex", "/api/orders/{id}/response,maria",
            "/api/admin/orders/{id}/reopen,admin", "/api/admin/orders/{id}/confirm,admin",
            "/api/admin/orders/{id}/refund,admin"})
    @DisplayName("Ошибки действий: 401, 403, 404 и невалидный ID не оставляют системных записей")
    void rejectedActionsDoNotCreateSystemMessages(String path, String username) throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", order.getId());
        var balances = jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id");
        mvc.perform(patch(path, order.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody(path))).andExpect(status().isUnauthorized());
        mvc.perform(patch(path, order.getId()).with(user(username).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(actionBody(path)))
                .andExpect(status().isForbidden());
        mvc.perform(action(path, order.getId(), outsider.getUsername())).andExpect(status().isForbidden());
        mvc.perform(action(path, Long.MAX_VALUE, username)).andExpect(status().isNotFound());
        mvc.perform(patch(path, "abc").with(user(username).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(actionBody(path)))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?", order.getId())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT id, balance, version FROM users ORDER BY id")).isEqualTo(balances);
    }

    @Test
    @DisplayName("Ошибки бизнеса и валидации: нет средств, объявления, отзыва или корректного тела — нет события")
    void invalidActionsDoNotLeaveEventsOrChangeBalances() throws Exception {
        jdbc.update("UPDATE users SET balance=0 WHERE id=?", buyer.getId());
        String body = mapper.writeValueAsString(Map.of("id", order.getAdvice().getId()));
        mvc.perform(post("/api/orders/").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/").with(user(buyer.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        for (long adviceId : List.of(order.getAdvice().getId(), Long.MAX_VALUE))
            mvc.perform(post("/api/orders/").with(user(buyer.getUsername())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("id", adviceId))))
                    .andExpect(status().is(adviceId == Long.MAX_VALUE ? 404 : 409));
        mvc.perform(action("/api/orders/{id}/response", order.getId(), seller.getUsername()))
                .andExpect(status().isConflict());
        mvc.perform(action("/api/orders/{id}/feedback", order.getId(), buyer.getUsername())
                        .content("{\"rating\":6,\"feedbackText\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
        assertThat(orders.count()).isEqualTo(2);
        assertThat(users.findById(buyer.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("История: USER и SYSTEM имеют общие курсоры, правильные DTO и изоляцию по заказу")
    void mixedHistoryUsesTheSamePaginationAndKeepsSystemMetadata() throws Exception {
        long first = seedMessage(order, buyer, "Начало", UUID.randomUUID());
        mvc.perform(action("/api/orders/{id}/feedback", order.getId(), buyer.getUsername())).andExpect(status().isNoContent());
        long feedback = jdbc.queryForObject("SELECT max(id) FROM order_messages", Long.class);
        seedMessage(otherOrder, outsider, "Чужая история", UUID.randomUUID());
        mvc.perform(action("/api/orders/{id}/response", order.getId(), admin.getUsername())).andExpect(status().isNoContent());
        long response = jdbc.queryForObject("SELECT max(id) FROM order_messages", Long.class);
        long last = responseBody(mvc.perform(send(order, seller, "Спасибо", UUID.randomUUID()))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();

        JsonNode latest = readPage(history().param("limit", "2"));
        assertPage(latest, List.of(response, last), true);
        assertThat(latest.path("items").get(0).path("messageType").asText()).isEqualTo("SYSTEM");
        assertThat(latest.path("items").get(0).path("eventType").asText()).isEqualTo("SELLER_RESPONSE_ADDED");
        assertThat(latest.path("items").get(0).path("sender").path("userRole").asText()).isEqualTo("ADMIN");
        assertThat(latest.path("items").get(1).path("messageType").asText()).isEqualTo("USER");
        assertThat(latest.path("items").get(1).path("eventType").isNull()).isTrue();
        assertPage(readPage(history().param("beforeId", Long.toString(response)).param("limit", "2")), List.of(first, feedback), false);
        assertPage(readPage(history().param("afterId", Long.toString(first)).param("limit", "1")), List.of(feedback), true);
        assertPage(readPage(history().param("afterId", Long.toString(feedback))), List.of(response, last), false);
    }

    @Test
    @DisplayName("Обычный POST не позволяет подменить тип сообщения на SYSTEM")
    void messagePostCannotForgeSystemEvent() throws Exception {
        mvc.perform(send(order, buyer, "Заказ подтверждён", REQUEST_ID).content(mapper.writeValueAsString(Map.of(
                        "text", "Заказ подтверждён", "clientMessageId", REQUEST_ID,
                        "messageType", "SYSTEM", "eventType", "ORDER_CONFIRMED"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.messageType").value("USER"))
                .andExpect(jsonPath("$.eventType").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT message_type FROM order_messages", String.class)).isEqualTo("USER");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.OPENED);
    }

    @Test
    @DisplayName("Системная запись требует транзакции действия")
    void systemMessageCannotCommitInItsOwnTransaction() {
        assertThatThrownBy(() -> messageService.addSystemMessage(order, admin, EventType.ORDER_REOPENED))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(messageCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"USER,false,", "USER,true,ORDER_CONFIRMED", "SYSTEM,true,ORDER_CONFIRMED",
            "SYSTEM,false,", "OTHER,true,"})
    @DisplayName("База данных отклоняет несовместимые тип, событие и клиентский UUID сообщения")
    void databaseRejectsInvalidMessageKinds(String type, boolean hasClientId, String eventType) {
        UUID clientId = null;
        if (hasClientId) clientId = UUID.randomUUID();
        UUID requestId = clientId;
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO order_messages(order_id, sender_id, text, sent_at, client_message_id, message_type, event_type)
                VALUES (?, ?, 'Невалидная запись', now(), ?, ?, ?)
                """, order.getId(), buyer.getId(), requestId, type, eventType))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_order_messages_type");
        assertThat(messageCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("serializedActions")
    @DisplayName("Параллельность: действие удерживает заказ до коммита, обычное сообщение получает следующий ID")
    void systemAndUserMessagesCommitInCursorOrder(String path, String username, OrderStatus initial,
                                                 EventType eventType, OrderStatus expected) throws Exception {
        changeStatus(initial);
        if (path.endsWith("/response"))
            jdbc.update("UPDATE orders SET rating=5, feedback='Отзыв' WHERE id=?", order.getId());
        long cursor = seedMessage(order, buyer, "До действия", UUID.randomUUID());
        var saved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var system = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                try {
                    mvc.perform(action(path, order.getId(), username)).andExpect(status().isNoContent());
                    saved.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Коммит не был разрешён");
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
            assertThat(saved.await(10, TimeUnit.SECONDS)).isTrue();
            var regular = executor.submit(() -> mvc.perform(send(order, buyer, "После действия", UUID.randomUUID())).andReturn());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                    """, Long.class)).isEqualTo(1));
            assertPage(readPage(history().param("afterId", Long.toString(cursor))), List.of(), false);
            release.countDown();
            system.get(10, TimeUnit.SECONDS);
            assertThat(regular.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
            JsonNode items = readPage(history().param("afterId", Long.toString(cursor))).path("items");
            assertThat(items.size()).isEqualTo(2);
            assertThat(items.get(0).path("eventType").asText()).isEqualTo(eventType.name());
            assertThat(items.get(1).path("messageType").asText()).isEqualTo("USER");
            assertThat(items.get(0).path("id").asLong()).isLessThan(items.get(1).path("id").asLong());
            assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(expected);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MockHttpServletRequestBuilder action(String path, long id, String username) {
        String role = "USER";
        if (username.equals(admin.getUsername())) role = "ADMIN";
        return patch(path, id).with(user(username).roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(actionBody(path));
    }

    private String actionBody(String path) {
        if (path.endsWith("/feedback")) return "{\"rating\":5,\"feedbackText\":\"Отзыв\"}";
        if (path.endsWith("/response")) return "{\"responseText\":\"Спасибо за отзыв\"}";
        return "";
    }

    private void assertSystemMessage(long orderId, String username, EventType eventType) throws Exception {
        JsonNode history = responseBody(mvc.perform(get("/api/orders/{id}/messages", orderId).with(user(username)))
                .andExpect(status().isOk()).andReturn());
        assertThat(history.path("items").size()).isEqualTo(1);
        JsonNode message = history.path("items").get(0);
        assertThat(message.path("messageType").asText()).isEqualTo("SYSTEM");
        assertThat(message.path("eventType").asText()).isEqualTo(eventType.name());
        assertThat(message.path("orderId").asLong()).isEqualTo(orderId);
        assertThat(message.path("sender").path("username").asText()).isEqualTo(username);
        User actor = users.findByUsername(username).orElseThrow();
        assertThat(message.path("sender").path("userRole").asText()).isEqualTo(actor.getRole().name());
        assertThat(message.path("sender").path("avatarUrl").asText()).isEqualTo(actor.getAvatarUrl());
        assertThat(message.path("sender").has("passwordHash")).isFalse();
        assertThat(message.path("text").asText()).isNotBlank();
        Instant.parse(message.path("sentAt").asText());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM order_messages WHERE id=?", message.path("id").asLong());
        assertThat(row.get("order_id")).isEqualTo(orderId);
        assertThat(row.get("sender_id")).isEqualTo(actor.getId());
        assertThat(row.get("message_type")).isEqualTo("SYSTEM");
        assertThat(row.get("event_type")).isEqualTo(eventType.name());
        assertThat(row.get("client_message_id")).isNull();
    }

    private void assertEvents(String username, EventType... expected) throws Exception {
        JsonNode items = readPage(history()).path("items");
        List<String> types = new ArrayList<>();
        for (JsonNode item : items) {
            assertThat(item.path("messageType").asText()).isEqualTo("SYSTEM");
            assertThat(item.path("sender").path("username").asText()).isEqualTo(username);
            types.add(item.path("eventType").asText());
        }
        assertThat(types).containsExactlyElementsOf(Stream.of(expected).map(Enum::name).toList());
    }

    private User savedUser(String username, UserRole role) {
        // Авторизацию подставляет Spring Security Test; проверка пароля покрыта общими API-тестами.
        User account = new User(username, "unused-in-chat-tests", new BigDecimal("100.00"));
        account.setRole(role);
        account.setAvatarUrl("/api/media/" + UUID.randomUUID());
        return users.save(account);
    }

    private void changeStatus(OrderStatus orderStatus) {
        order.setStatus(orderStatus);
        order = orders.save(order);
    }

    private String messageBody(String text, UUID requestId) throws Exception {
        return mapper.writeValueAsString(Map.of("text", text, "clientMessageId", requestId));
    }

    private MockHttpServletRequestBuilder send(Order target, User sender, String text, UUID requestId)
            throws Exception {
        return post("/api/orders/{id}/messages", target.getId())
                .with(user(sender.getUsername())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(messageBody(text, requestId));
    }

    private MockHttpServletRequestBuilder history() {
        return get("/api/orders/{id}/messages", order.getId());
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode readPage(MockHttpServletRequestBuilder request) throws Exception {
        return responseBody(mvc.perform(request.with(user(buyer.getUsername())))
                .andExpect(status().isOk()).andReturn());
    }

    private void assertPage(JsonNode response, List<Long> expectedIds, boolean hasMore) {
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("hasMore").isBoolean()).isTrue();
        List<Long> ids = new ArrayList<>();
        response.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        assertThat(ids).containsExactlyElementsOf(expectedIds);
        assertThat(response.path("hasMore").asBoolean()).isEqualTo(hasMore);
    }

    private long seedMessage(Order target, User sender, String text, UUID requestId) {
        return jdbc.queryForObject("""
                INSERT INTO order_messages (order_id, sender_id, text, sent_at, client_message_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, target.getId(), sender.getId(), text, Timestamp.from(SENT_AT), requestId);
    }

    private List<Long> seedInterleavedMessages(int count) {
        List<Long> ids = new ArrayList<>();
        // Общая последовательность ID намеренно перемежается сообщениями другого заказа.
        for (int i = 0; i < count; i++) {
            ids.add(seedMessage(order, buyer, "Сообщение " + i, UUID.randomUUID()));
            seedMessage(otherOrder, outsider, "Чужое сообщение " + i, UUID.randomUUID());
        }
        return ids;
    }

    private long messageCount() {
        return jdbc.queryForObject("SELECT count(*) FROM order_messages", Long.class);
    }
}
