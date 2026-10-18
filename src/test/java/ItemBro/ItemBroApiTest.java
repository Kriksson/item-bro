package ItemBro;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Advice.DTO.AdviceBody;
import ItemBro.Game.Game;
import ItemBro.Game.GameBody;
import ItemBro.Game.GameRepository;
import ItemBro.Game.GameStatus;
import ItemBro.Order.Order;
import ItemBro.Order.OrderRepository;
import ItemBro.Order.OrderService;
import ItemBro.Order.OrderStatus;
import ItemBro.Order.DTO.OrderFeedbackBody;
import ItemBro.Order.DTO.OrderResponseBody;
import ItemBro.User.DTO.UserBody;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ItemBroApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("item_bro_test")
            .withUsername("test")
            .withPassword("test");

    private static final String PASSWORD = "Strong123!";
    private static final BigDecimal PRICE = new BigDecimal("25.00");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired AdviceRepository advices;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.execute("TRUNCATE TABLE order_messages, orders, advices, games, users RESTART IDENTITY");
    }

    @Test
    @DisplayName("Администрирование: администратор видит чужие активные объявления в разных играх")
    void adminCanReadOtherUsersAdvices() throws Exception {
        savedAdmin();
        User firstSeller = savedUser("alex");
        User secondSeller = savedUser("maria");
        Game firstGame = games.save(new Game("Puzzle Arena"));
        Game secondGame = games.save(new Game("City Builder"));
        Advice first = advices.save(new Advice("Первый предмет", firstGame, firstSeller,
                PRICE, AdviceStatus.ACTIVE));
        Advice second = advices.save(new Advice("Второй предмет", secondGame, secondSeller,
                PRICE, AdviceStatus.ACTIVE));
        Map<Long, String> expectedSellers = Map.of(first.getId(), firstSeller.getUsername(),
                second.getId(), secondSeller.getUsername());

        for (Advice advice : List.of(first, second)) {
            mvc.perform(get("/api/advices").param("gameId", advice.getGame().getId().toString())
                            .with(httpBasic("admin", PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(advice.getId().intValue()))
                    .andExpect(jsonPath("$.content[0].game.id").value(advice.getGame().getId().intValue()))
                    .andExpect(jsonPath("$.content[0].userSeller.username").value(expectedSellers.get(advice.getId())));
        }
    }

    @Test
    @DisplayName("Администрирование: администратор архивирует чужое объявление")
    void adminCanArchiveAnotherUsersAdvice() throws Exception {
        savedAdmin();
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(delete("/api/advices/{id}", advice.getId())
                        .with(httpBasic("admin", PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(advices.count()).isEqualTo(1);
        Advice archived = advices.findById(advice.getId()).orElseThrow();
        assertThat(archived.getStatus()).isEqualTo(AdviceStatus.ARCHIVED);
        assertThat(archived.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(archived.getPrice()).isEqualByComparingTo(PRICE);
    }

    @Test
    @DisplayName("Администрирование: администратор создаёт игру, получает DTO и Location")
    void adminCanCreateGame() throws Exception {
        savedAdmin();

        var result = mvc.perform(gameCreation("Puzzle Arena").with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Puzzle Arena"))
                .andReturn();

        assertThat(games.count()).isEqualTo(1);
        Game game = games.findAll().getFirst();
        assertThat(game.getName()).isEqualTo("Puzzle Arena");
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong())
                .isEqualTo(game.getId());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/games/" + game.getId());
    }

    @Test
    @DisplayName("Администрирование: создание игры без авторизации возвращает 401 с корректным CSRF")
    void rejectsAnonymousGameCreation() throws Exception {
        mvc.perform(gameCreation("Puzzle Arena")).andExpect(status().isUnauthorized());

        assertThat(games.count()).isZero();
    }

    @Test
    @DisplayName("Администрирование: обычный пользователь не может создать игру")
    void rejectsGameCreationByRegularUser() throws Exception {
        savedUser("alex");

        mvc.perform(gameCreation("Puzzle Arena").with(httpBasic("alex", PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(games.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidGameNames")
    @DisplayName("Администрирование: невалидное имя игры возвращает 400 без сохранения")
    void rejectsInvalidGameName(String name) throws Exception {
        savedAdmin();

        mvc.perform(gameCreation(name).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isBadRequest());

        assertThat(games.count()).isZero();
    }

    static Stream<String> invalidGameNames() {
        return Stream.of(null, "", "   ", "a".repeat(51));
    }

    @Test
    @DisplayName("Архивирование игры: администратор меняет статус, игра и связанные данные сохраняются")
    void adminCanArchiveGame() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        archiveGameThroughApi(advice.getGame().getId());

        assertThat(games.count()).isEqualTo(1);
        assertThat(games.findById(advice.getGame().getId()).orElseThrow().getStatus())
                .isEqualTo(GameStatus.ARCHIVED);
        assertThat(advices.existsById(advice.getId())).isTrue();
        assertThat(orders.findById(orderId).orElseThrow().getAdvice().getId()).isEqualTo(advice.getId());
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Архивирование игры: повторный запрос возвращает 409")
    void rejectsArchivingAlreadyArchivedGame() throws Exception {
        savedAdmin();
        Game game = games.save(new Game("Puzzle Arena"));
        archiveGameThroughApi(game.getId());

        mvc.perform(gameArchiving(game.getId()).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isConflict());

        assertThat(games.count()).isEqualTo(1);
        assertThat(games.findById(game.getId()).orElseThrow().getStatus()).isEqualTo(GameStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Архивирование игры: несуществующая игра возвращает 404")
    void rejectsArchivingUnknownGame() throws Exception {
        savedAdmin();

        mvc.perform(gameArchiving(999L).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isNotFound());

        assertThat(games.count()).isZero();
    }

    @ParameterizedTest(name = "gameId: {0}")
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    @DisplayName("Архивирование игры: невалидный id возвращает 400")
    void rejectsArchivingGameWithInvalidId(String gameId) throws Exception {
        savedAdmin();

        mvc.perform(gameArchiving(gameId).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isBadRequest());

        assertThat(games.count()).isZero();
    }

    @Test
    @DisplayName("Архивирование игры: без авторизации возвращает 401, статус не меняется")
    void rejectsAnonymousGameArchiving() throws Exception {
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(gameArchiving(game.getId())).andExpect(status().isUnauthorized());

        assertThat(games.findById(game.getId()).orElseThrow().getStatus()).isEqualTo(GameStatus.OPENED);
    }

    @Test
    @DisplayName("Архивирование игры: обычный пользователь получает 403, статус не меняется")
    void rejectsGameArchivingByRegularUser() throws Exception {
        savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(gameArchiving(game.getId()).with(httpBasic("alex", PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(games.findById(game.getId()).orElseThrow().getStatus()).isEqualTo(GameStatus.OPENED);
    }

    @Test
    @DisplayName("Архивная игра: новое объявление не создаётся, возвращается 409")
    void rejectsAdviceCreationForArchivedGame() throws Exception {
        savedAdmin();
        savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        archiveGameThroughApi(game.getId());

        mvc.perform(creation(new AdviceBody("Предмет", game.getId(), PRICE)).with(user("alex")))
                .andExpect(status().isConflict());

        assertThat(advices.count()).isZero();
    }

    @Test
    @DisplayName("Архивная игра: список объявлений недоступен, возвращается 409 без content")
    void rejectsAdviceReadForArchivedGame() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        archiveGameThroughApi(advice.getGame().getId());

        mvc.perform(get("/api/advices").param("gameId", advice.getGame().getId().toString())
                        .with(user("alex")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.content").doesNotExist());

        assertThat(advices.existsById(advice.getId())).isTrue();
    }

    @Test
    @DisplayName("Архивная игра: новый заказ не создаётся и деньги не списываются")
    void rejectsOrderCreationForArchivedGame() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        archiveGameThroughApi(advice.getGame().getId());

        mvc.perform(orderCreation(advice.getId()).with(user("alex")))
                .andExpect(status().isConflict());

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
        assertThat(users.findByUsername("maria").orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "Участник: {0}")
    @ValueSource(strings = {"alex", "maria"})
    @DisplayName("Архивная игра: покупатель и продавец могут прочитать существующий заказ")
    void participantsCanReadOrderAfterGameArchiving(String username) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        archiveGameThroughApi(advice.getGame().getId());

        mvc.perform(get("/api/orders/{id}", orderId).with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.adviceDTO.game.id").value(advice.getGame().getId().intValue()))
                .andExpect(jsonPath("$.adviceDTO.game.name").value("Puzzle Arena"));

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Архивная игра: существующий заказ подтверждается с начислением продавцу")
    void buyerCanConfirmOrderAfterGameArchiving() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        archiveGameThroughApi(advice.getGame().getId());

        mvc.perform(orderConfirmation(orderId).with(user("alex"))).andExpect(status().isNoContent());

        assertOrderStateAndBalances(orderId, OrderStatus.CLOSED, "75", "25");
    }

    @Test
    @DisplayName("Архивная игра: существующий заказ отменяется с возвратом покупателю")
    void sellerCanCancelOrderAfterGameArchiving() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        archiveGameThroughApi(advice.getGame().getId());

        mvc.perform(orderCancellation(orderId).with(user("maria"))).andExpect(status().isNoContent());

        assertOrderStateAndBalances(orderId, OrderStatus.REFUND, "100");
    }

    @Test
    @DisplayName("Регистрация: создаёт пользователя с нулевым балансом и хешем пароля")
    void registersUser() throws Exception {
        mvc.perform(registration("alex"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("alex"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(users.count()).isEqualTo(1);
        User saved = users.findByUsername("alex").orElseThrow();
        assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abcdefghijklmnopqrstu"})
    @DisplayName("Регистрация: отклоняет пустое, пробельное и слишком длинное имя")
    void rejectsInvalidUsername(String name) throws Exception {
        mvc.perform(registration(name)).andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }

    @Test
    @DisplayName("Регистрация: повторное имя не создаёт второго пользователя")
    void rejectsDuplicateUsername() throws Exception {
        User existing = savedUser("alex");

        mvc.perform(registration("alex"))
                .andExpect(status().isConflict());

        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByUsername("alex").orElseThrow().getId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("Регистрация: вошедший пользователь не может зарегистрировать ещё один аккаунт")
    void rejectsRegistrationByAuthenticatedUser() throws Exception {
        savedUser("alex");

        mvc.perform(registration("maria").with(user("alex")))
                .andExpect(status().isForbidden());

        assertThat(users.count()).isEqualTo(1);
        assertThat(users.existsByUsername("maria")).isFalse();
    }

    @Test
    @DisplayName("Объявление: сохраняет данные и продавца из аутентификации")
    void createsAdvice() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(creation(new AdviceBody("Набор предметов", game.getId(), PRICE)).with(user("alex")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Набор предметов"))
                .andExpect(jsonPath("$.game.id").value(game.getId().intValue()))
                .andExpect(jsonPath("$.game.name").value(game.getName()))
                .andExpect(jsonPath("$.userSeller.username").value(seller.getUsername()))
                .andExpect(jsonPath("$.userSeller.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.price").value(25.0));

        assertThat(advices.count()).isEqualTo(1);
        Advice saved = advices.findAll().getFirst();
        assertThat(saved.getName()).isEqualTo("Набор предметов");
        assertThat(saved.getPrice()).isEqualByComparingTo(PRICE);
        assertThat(saved.getGame().getId()).isEqualTo(game.getId());
        assertThat(saved.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(saved.getStatus()).isEqualTo(AdviceStatus.ACTIVE);
    }

    @ParameterizedTest(name = "Имеющихся объявлений: {0}")
    @ValueSource(ints = {5, 6})
    @DisplayName("Объявление: лимит действует суммарно по всем играм, включая границу 5")
    void rejectsAdviceAtOrAboveLimit(int existingCount) throws Exception {
        User seller = savedUser("alex");
        Game firstGame = games.save(new Game("Puzzle Arena"));
        Game secondGame = games.save(new Game("City Builder"));
        for (int index = 0; index < existingCount; index++) {
            Game game = index % 2 == 0 ? firstGame : secondGame;
            advices.save(new Advice("Предмет " + index, game, seller, PRICE, AdviceStatus.ACTIVE));
        }

        mvc.perform(creation(new AdviceBody("Новый предмет", secondGame.getId(), PRICE)).with(user("alex")))
                .andExpect(status().isConflict());

        assertThat(advices.count()).isEqualTo(existingCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAdviceFields")
    @DisplayName("Объявление: невалидное имя или цена не сохраняются")
    void rejectsInvalidAdviceFields(String description, String name, BigDecimal price) throws Exception {
        savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(creation(new AdviceBody(name, game.getId(), price)).with(user("alex")))
                .andExpect(status().isBadRequest());

        assertThat(advices.count()).isZero();
    }

    static Stream<Arguments> invalidAdviceFields() {
        return Stream.of(
                Arguments.of("Пробельное имя", "   ", PRICE),
                Arguments.of("Имя длиннее 50 символов", "a".repeat(51), PRICE),
                Arguments.of("Нулевая цена", "Предмет", BigDecimal.ZERO),
                Arguments.of("Отрицательная цена", "Предмет", new BigDecimal("-1"))
        );
    }

    @Test
    @DisplayName("Объявление: отклоняет несуществующую игру")
    void rejectsUnknownGame() throws Exception {
        savedUser("alex");

        mvc.perform(creation(new AdviceBody("Предмет", 999L, PRICE)).with(user("alex")))
                .andExpect(status().isNotFound());

        assertThat(advices.count()).isZero();
    }

    @Test
    @DisplayName("Объявление: без авторизации возвращает 401 даже с корректным CSRF")
    void rejectsAnonymousCreation() throws Exception {
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(creation(new AdviceBody("Предмет", game.getId(), PRICE)))
                .andExpect(status().isUnauthorized());

        assertThat(advices.count()).isZero();
    }

    @Test
    @DisplayName("HTTP Basic: зарегистрированный пользователь входит со своим исходным паролем")
    void acceptsRegisteredUsersPassword() throws Exception {
        mvc.perform(registration("alex")).andExpect(status().isCreated());
        Game game = games.save(new Game("Puzzle Arena"));

        mvc.perform(creation(new AdviceBody("Предмет", game.getId(), PRICE))
                        .with(httpBasic("alex", PASSWORD)))
                .andExpect(status().isCreated());

        assertThat(advices.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Удаление: владелец архивирует своё объявление, строка сохраняется")
    void archivesOwnAdvice() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(delete("/api/advices/{id}", advice.getId()).with(user("alex")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(advices.findById(advice.getId()).orElseThrow().getStatus()).isEqualTo(AdviceStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Удаление: чужое объявление остаётся в базе, запрос возвращает 403")
    void rejectsDeletingAnotherUsersAdvice() throws Exception {
        User seller = savedUser("alex");
        savedUser("maria");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(delete("/api/advices/{id}", advice.getId()).with(user("maria")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(advices.existsById(advice.getId())).isTrue();
        assertThat(advices.findById(advice.getId()).orElseThrow().getStatus()).isEqualTo(AdviceStatus.ACTIVE);
    }

    @Test
    @DisplayName("Удаление: несуществующее объявление возвращает 404")
    void rejectsDeletingUnknownAdvice() throws Exception {
        savedUser("alex");

        mvc.perform(delete("/api/advices/{id}", 999L).with(user("alex")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Удаление: без авторизации возвращает 401 даже с корректным CSRF")
    void rejectsAnonymousDeletion() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(delete("/api/advices/{id}", advice.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(advices.existsById(advice.getId())).isTrue();
        assertThat(advices.findById(advice.getId()).orElseThrow().getStatus()).isEqualTo(AdviceStatus.ACTIVE);
    }

    @Test
    @DisplayName("Лимит: пять архивных объявлений не мешают создать активное")
    void archivedAdvicesDoNotConsumeLimit() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        for (int index = 0; index < 5; index++) {
            advices.save(new Advice("Архив " + index, game, seller, PRICE, AdviceStatus.ARCHIVED));
        }

        mvc.perform(creation(new AdviceBody("Новый предмет", game.getId(), PRICE)).with(user("alex")))
                .andExpect(status().isCreated());

        assertThat(advices.count()).isEqualTo(6);
        assertThat(advices.countAdvicesBySellerIdAndStatusEquals(seller.getId(), AdviceStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Список объявлений: возвращает активные объявления игры по убыванию id")
    void readsAdvicesByGame() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        Game otherGame = games.save(new Game("City Builder"));
        Advice first = advices.save(new Advice("Первый предмет", game, seller, PRICE, AdviceStatus.ACTIVE));
        Advice second = advices.save(new Advice("Второй предмет", game, seller,
                new BigDecimal("40"), AdviceStatus.ACTIVE));
        advices.save(new Advice("Архив", game, seller, PRICE, AdviceStatus.ARCHIVED));
        advices.save(new Advice("Другая игра", otherGame, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(get("/api/advices").param("gameId", game.getId().toString()).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second.getId().intValue()))
                .andExpect(jsonPath("$.content[0].name").value("Второй предмет"))
                .andExpect(jsonPath("$.content[0].price").value(40.0))
                .andExpect(jsonPath("$.content[0].game.id").value(game.getId().intValue()))
                .andExpect(jsonPath("$.content[0].game.name").value("Puzzle Arena"))
                .andExpect(jsonPath("$.content[0].userSeller.username").value("alex"))
                .andExpect(jsonPath("$.content[0].userSeller.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").value(first.getId().intValue()));
    }

    @Test
    @DisplayName("Список объявлений: без gameId возвращает 400")
    void rejectsAdviceReadWithoutGameId() throws Exception {
        savedUser("alex");

        mvc.perform(get("/api/advices").with(user("alex")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Список объявлений: несуществующая игра возвращает 404")
    void rejectsAdviceReadForUnknownGame() throws Exception {
        savedUser("alex");

        mvc.perform(get("/api/advices").param("gameId", "999").with(user("alex")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Game not found"));
    }

    @ParameterizedTest(name = "gameId: {0}")
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    @DisplayName("Список объявлений: gameId, который нельзя преобразовать в Long, возвращает 400")
    void rejectsAdviceReadWithInvalidGameId(String gameId) throws Exception {
        savedUser("alex");

        mvc.perform(get("/api/advices").param("gameId", gameId).with(user("alex")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Список объявлений: публичный каталог доступен без авторизации")
    void anonymousCanReadAdvices() throws Exception {
        User seller = savedUser("alex");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, PRICE, AdviceStatus.ACTIVE));

        mvc.perform(get("/api/advices").param("gameId", game.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(advice.getId().intValue()));
    }

    @ParameterizedTest(name = "Начальный баланс: {0}")
    @ValueSource(strings = {"100.00", "25.00"})
    @DisplayName("Заказ: возвращает JSON, сохраняет участников и списывает точную цену")
    void createsOrderAndDebitsBalance(String initialBalance) throws Exception {
        BigDecimal balance = new BigDecimal(initialBalance);
        Advice advice = orderFixture(balance, PRICE);

        mvc.perform(orderCreation(advice.getId()).with(user("alex")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.adviceDTO.id").value(advice.getId().intValue()))
                .andExpect(jsonPath("$.adviceDTO.game.name").value("Puzzle Arena"))
                .andExpect(jsonPath("$.adviceDTO.userSeller.username").value("maria"))
                .andExpect(jsonPath("$.userBuyerDTO.username").value("alex"))
                .andExpect(jsonPath("$.price").value(25.0));

        assertThat(orders.count()).isEqualTo(1);
        Order saved = orders.findAll().getFirst();
        assertThat(saved.getAdvice().getId()).isEqualTo(advice.getId());
        assertThat(saved.getBuyer().getId()).isEqualTo(users.findByUsername("alex").orElseThrow().getId());
        assertThat(saved.getSeller().getId()).isEqualTo(advice.getSeller().getId());
        assertThat(saved.getPrice()).isEqualByComparingTo(PRICE);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.OPENED);
        assertThat(users.findByUsername("alex").orElseThrow().getBalance())
                .isEqualByComparingTo(balance.subtract(PRICE));
    }

    @Test
    @DisplayName("Заказ: недостаточный баланс возвращает 409 без заказа и списания")
    void rejectsOrderWithInsufficientBalance() throws Exception {
        Advice advice = orderFixture(new BigDecimal("10"), PRICE);

        mvc.perform(orderCreation(advice.getId()).with(user("alex")))
                .andExpect(status().isConflict());

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Заказ: несуществующее объявление возвращает 404 без списания")
    void rejectsOrderForUnknownAdvice() throws Exception {
        savedUser("alex", new BigDecimal("100"));

        mvc.perform(orderCreation(999L).with(user("alex")))
                .andExpect(status().isNotFound());

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Заказ: без авторизации возвращает 401 с корректным CSRF")
    void rejectsAnonymousOrderCreation() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);

        mvc.perform(orderCreation(advice.getId())).andExpect(status().isUnauthorized());

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Заказ: архивное объявление возвращает 404 без списания")
    void rejectsOrderForArchivedAdvice() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        archiveAdviceThroughApi(advice.getId());

        mvc.perform(orderCreation(advice.getId()).with(user("alex")))
                .andExpect(status().isNotFound());

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Отмена заказа: продавец отменяет заказ и возвращает деньги покупателю")
    void sellerCanCancelOrder() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderCancellation(orderId).with(user("maria")))
                .andExpect(status().isNoContent());

        assertOrderStateAndBalances(orderId, OrderStatus.REFUND, "100");
    }

    @Test
    @DisplayName("Отмена заказа: покупатель получает 403, заказ и балансы не меняются")
    void rejectsOrderCancellationByBuyer() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderCancellation(orderId).with(user("alex")))
                .andExpect(status().isForbidden());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Отмена заказа: посторонний получает 403, заказ и балансы не меняются")
    void rejectsOrderCancellationByOutsider() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        savedUser("ivan", new BigDecimal("50"));
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderCancellation(orderId).with(user("ivan")))
                .andExpect(status().isForbidden());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
        assertThat(users.findByUsername("ivan").orElseThrow().getBalance()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("Отмена заказа: без авторизации возвращает 401 с корректным CSRF")
    void rejectsAnonymousOrderCancellation() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderCancellation(orderId)).andExpect(status().isUnauthorized());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Подтверждение: покупатель закрывает заказ и начисляет цену продавцу")
    void buyerCanConfirmOrder() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        User seller = users.findByUsername("maria").orElseThrow();
        seller.setBalance(new BigDecimal("40"));
        users.save(seller);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderConfirmation(orderId).with(user("alex")))
                .andExpect(status().isNoContent());

        assertOrderStateAndBalances(orderId, OrderStatus.CLOSED, "75", "65");
    }

    @Test
    @DisplayName("Подтверждение: продавец получает 403, заказ и балансы не меняются")
    void rejectsOrderConfirmationBySeller() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderConfirmation(orderId).with(user("maria")))
                .andExpect(status().isForbidden());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Подтверждение: посторонний получает 403, заказ и балансы не меняются")
    void rejectsOrderConfirmationByOutsider() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        savedUser("ivan", new BigDecimal("50"));
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderConfirmation(orderId).with(user("ivan")))
                .andExpect(status().isForbidden());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
        assertThat(users.findByUsername("ivan").orElseThrow().getBalance()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("Подтверждение: без авторизации возвращает 401 с корректным CSRF")
    void rejectsAnonymousOrderConfirmation() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(orderConfirmation(orderId)).andExpect(status().isUnauthorized());

        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Отзыв: покупатель добавляет и редактирует отзыв, GET возвращает сохранённые данные")
    void buyerCanAddAndEditFeedback() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        Order newOrder = orders.findById(orderId).orElseThrow();
        assertThat(newOrder.getRating()).isEqualTo(-1);
        assertThat(newOrder.getFeedback()).isNull();

        mvc.perform(orderFeedback(orderId, new OrderFeedbackBody(5, "Всё хорошо")).with(user("alex")))
                .andExpect(status().isNoContent());

        assertFeedbackInDatabaseAndResponse(orderId, 5, "Всё хорошо");

        mvc.perform(orderFeedback(orderId, new OrderFeedbackBody(4, "Дополненный отзыв")).with(user("alex")))
                .andExpect(status().isNoContent());

        assertFeedbackInDatabaseAndResponse(orderId, 4, "Дополненный отзыв");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Отзыв: продавец получает 403 и не может изменить отзыв покупателя")
    void rejectsFeedbackBySeller() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        orderService.addFeedback(() -> "alex", orderId, 4, "Отзыв покупателя");

        mvc.perform(orderFeedback(orderId, new OrderFeedbackBody(5, "Отзыв продавца")).with(user("maria")))
                .andExpect(status().isForbidden());

        assertFeedbackInDatabaseAndResponse(orderId, 4, "Отзыв покупателя");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Отзыв: несуществующий заказ возвращает 404")
    void rejectsFeedbackForUnknownOrder() throws Exception {
        savedUser("alex");

        mvc.perform(orderFeedback(999L, new OrderFeedbackBody(5, "Отзыв")).with(user("alex")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Заказ не найден"));

        assertThat(orders.count()).isZero();
    }

    @ParameterizedTest(name = "orderId: {0}")
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    @DisplayName("Отзыв: id, который нельзя преобразовать в Long, возвращает 400")
    void rejectsFeedbackWithInvalidOrderId(String orderId) throws Exception {
        savedUser("alex");

        mvc.perform(orderFeedback(orderId, new OrderFeedbackBody(5, "Отзыв")).with(user("alex")))
                .andExpect(status().isBadRequest());

        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("Отзыв: без авторизации возвращает 401 с корректным CSRF и сохраняет прежний отзыв")
    void rejectsAnonymousFeedback() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        orderService.addFeedback(() -> "alex", orderId, 4, "Отзыв покупателя");

        mvc.perform(orderFeedback(orderId, new OrderFeedbackBody(1, "Чужой отзыв")))
                .andExpect(status().isUnauthorized());

        assertFeedbackInDatabaseAndResponse(orderId, 4, "Отзыв покупателя");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Ответ продавца: сохраняется и возвращается обоим участникам заказа")
    void sellerCanAddResponse() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        orderService.addFeedback(() -> "alex", orderId, 4, "Отзыв покупателя");

        mvc.perform(sellerResponse(orderId, "Спасибо за отзыв").with(user("maria")))
                .andExpect(status().isNoContent());

        assertSellerResponseInDatabaseAndResponse(orderId, "Спасибо за отзыв");
        assertFeedbackInDatabaseAndResponse(orderId, 4, "Отзыв покупателя");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Ответ продавца: до отзыва возвращает 409 без изменения заказа")
    void rejectsSellerResponseBeforeFeedback() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(sellerResponse(orderId, "Ответ до отзыва").with(user("maria")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Отзыв не был создан"));

        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getSellerResponse()).isNull();
        assertThat(order.getRating()).isEqualTo(-1);
        assertThat(order.getFeedback()).isNull();
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Ответ продавца: несуществующий заказ возвращает 404")
    void rejectsSellerResponseForUnknownOrder() throws Exception {
        savedUser("maria");

        mvc.perform(sellerResponse(999L, "Ответ").with(user("maria")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Заказ не найден"));

        assertThat(orders.count()).isZero();
    }

    @ParameterizedTest(name = "orderId: {0}")
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    @DisplayName("Ответ продавца: id, который нельзя преобразовать в Long, возвращает 400")
    void rejectsSellerResponseWithInvalidOrderId(String orderId) throws Exception {
        savedUser("maria");

        mvc.perform(sellerResponse(orderId, "Ответ").with(user("maria")))
                .andExpect(status().isBadRequest());

        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("Ответ продавца: без авторизации возвращает 401 и сохраняет прежний ответ")
    void rejectsAnonymousSellerResponse() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();
        orderService.addFeedback(() -> "alex", orderId, 4, "Отзыв покупателя");
        orderService.addSellerResponse(() -> "maria", orderId, "Ответ продавца");

        mvc.perform(sellerResponse(orderId, "Чужой ответ"))
                .andExpect(status().isUnauthorized());

        assertSellerResponseInDatabaseAndResponse(orderId, "Ответ продавца");
        assertFeedbackInDatabaseAndResponse(orderId, 4, "Отзыв покупателя");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @Test
    @DisplayName("Ответ продавца: покупатель получает 403 как до отзыва, так и после него")
    void rejectsSellerResponseByBuyer() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(sellerResponse(orderId, "Ответ покупателя").with(user("alex")))
                .andExpect(status().isForbidden());

        assertThat(orders.findById(orderId).orElseThrow().getSellerResponse()).isNull();
        orderService.addFeedback(() -> "alex", orderId, 4, "Отзыв покупателя");
        orderService.addSellerResponse(() -> "maria", orderId, "Ответ продавца");

        mvc.perform(sellerResponse(orderId, "Ответ покупателя").with(user("alex")))
                .andExpect(status().isForbidden());

        assertSellerResponseInDatabaseAndResponse(orderId, "Ответ продавца");
        assertFeedbackInDatabaseAndResponse(orderId, 4, "Отзыв покупателя");
        assertOrderStateAndBalances(orderId, OrderStatus.OPENED, "75");
    }

    @ParameterizedTest(name = "Участник: {0}")
    @ValueSource(strings = {"alex", "maria"})
    @DisplayName("Чтение заказа: покупатель и продавец получают полный JSON")
    void participantsCanReadOrder(String username) throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(get("/api/orders/{id}", orderId).with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.adviceDTO.game.name").value("Puzzle Arena"))
                .andExpect(jsonPath("$.adviceDTO.userSeller.username").value("maria"))
                .andExpect(jsonPath("$.userBuyerDTO.username").value("alex"));
    }

    @Test
    @DisplayName("Чтение заказа: посторонний пользователь получает 403")
    void rejectsReadingAnotherUsersOrder() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        savedUser("ivan");
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(get("/api/orders/{id}", orderId).with(user("ivan")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Чтение заказа: несуществующий заказ возвращает 404")
    void rejectsReadingUnknownOrder() throws Exception {
        savedUser("alex");
        mvc.perform(get("/api/orders/999").with(user("alex"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Чтение заказа: без авторизации возвращает 401")
    void rejectsAnonymousOrderRead() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        mvc.perform(get("/api/orders/{id}", orderId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Архивирование объявления сохраняет доступ к истории заказа")
    void archivedAdvicePreservesOrderHistory() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long orderId = orderService.createOrder(() -> "alex", advice.getId()).id();

        archiveAdviceThroughApi(advice.getId());

        mvc.perform(get("/api/orders/{id}", orderId).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adviceDTO.id").value(advice.getId().intValue()));
        assertThat(orders.count()).isEqualTo(1);
        assertThat(advices.findById(advice.getId()).orElseThrow().getStatus()).isEqualTo(AdviceStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Транзакция: ошибка записи заказа откатывает списание")
    void failedOrderInsertRollsBackBalance() {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        // Ограничение существует только в тестовой БД и имитирует отказ при INSERT.
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT test_reject_order CHECK (price < 0)");
        try {
            assertThatThrownBy(() -> orderService.createOrder(() -> "alex", advice.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("ALTER TABLE orders DROP CONSTRAINT test_reject_order");
        }

        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Параллельные покупки: версия пользователя откатывает второй заказ")
    void concurrentOrdersCannotOverspendBalance() throws Exception {
        BigDecimal price = new BigDecimal("75");
        Advice first = orderFixture(new BigDecimal("100"), price);
        Advice second = advices.save(new Advice("Второй предмет", first.getGame(), first.getSeller(),
                price, AdviceStatus.ACTIVE));
        CountDownLatch bothLoaded = new CountDownLatch(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstPurchase = executor.submit(() -> concurrentPurchase(first.getId(), bothLoaded));
            var secondPurchase = executor.submit(() -> concurrentPurchase(second.getId(), bothLoaded));
            int conflicts = 0;
            for (var purchase : List.of(firstPurchase, secondPurchase)) {
                try {
                    purchase.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                    conflicts++;
                }
            }
            assertThat(conflicts).isEqualTo(1);
        }

        assertThat(orders.count()).isEqualTo(1);
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("Каталог игр: гость видит открытые игры с изображениями, архивные скрыты")
    void anonymousCanReadOpenedGames() throws Exception {
        Game visible = new Game("Puzzle Arena");
        visible.setImageUrl("/api/media/550e8400-e29b-41d4-a716-446655440000");
        visible = games.save(visible);
        Game archived = new Game("City Builder");
        archived.setStatus(GameStatus.ARCHIVED);
        games.save(archived);

        mvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(visible.getId().intValue()))
                .andExpect(jsonPath("$[0].imageUrl").value(visible.getImageUrl()));
    }

    @Test
    @DisplayName("Мои заказы: возвращает покупки и продажи, исключая чужие заказы")
    void readsOnlyOwnPurchasesAndSales() throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        User alex = users.findByUsername("alex").orElseThrow();
        User maria = advice.getSeller();
        User outsider = savedUser("ivan");
        Advice ownAdvice = advices.save(new Advice("Моё объявление", advice.getGame(), alex,
                PRICE, AdviceStatus.ACTIVE));
        Order purchase = orders.save(new Order(advice, alex, maria, PRICE, OrderStatus.OPENED));
        Order sale = orders.save(new Order(ownAdvice, outsider, alex, PRICE, OrderStatus.CLOSED));
        orders.save(new Order(advice, outsider, maria, PRICE, OrderStatus.OPENED));

        mvc.perform(get("/api/orders").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        purchase.getId().intValue(), sale.getId().intValue())))
                .andExpect(jsonPath("$[*].orderStatus", containsInAnyOrder("OPENED", "CLOSED")))
                .andExpect(jsonPath("$[0].adviceDTO.game.name").value("Puzzle Arena"))
                .andExpect(jsonPath("$[0].userBuyerDTO.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Мои заказы: без авторизации возвращает 401")
    void rejectsAnonymousOwnOrderList() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Администрирование: список содержит заказы всех пользователей и доступ к чужому заказу")
    void adminCanReadAllOrders() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        User buyer = users.findByUsername("alex").orElseThrow();
        User otherBuyer = savedUser("ivan");
        Order first = orders.save(new Order(advice, buyer, advice.getSeller(), PRICE, OrderStatus.OPENED));
        Order second = orders.save(new Order(advice, otherBuyer, advice.getSeller(), PRICE, OrderStatus.CLOSED));

        mvc.perform(get("/api/admin/orders").with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(), second.getId().intValue())))
                .andExpect(jsonPath("$[0].adviceDTO.userSeller.username").value("maria"));
        mvc.perform(get("/api/orders/{id}", first.getId()).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.getId().intValue()));
    }

    @Test
    @DisplayName("Администрирование: пустой список заказов возвращается массивом")
    void adminCanReadEmptyOrderList() throws Exception {
        savedAdmin();
        mvc.perform(get("/api/admin/orders").with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Администрирование: гость не может запросить все заказы")
    void rejectsAnonymousAdminOrderList() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Администрирование: обычный пользователь не может запросить все заказы")
    void rejectsAdminOrderListForRegularUser() throws Exception {
        savedUser("alex");
        mvc.perform(get("/api/admin/orders").with(httpBasic("alex", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "confirm", "feedback", "response"})
    @DisplayName("Администрирование: администратор может выполнить действие в чужом заказе")
    void adminCanActOnAnotherUsersOrder(String action) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        mvc.perform(orderCreation(advice.getId()).with(user("alex"))).andExpect(status().isCreated());
        Order order = orders.findAll().getFirst();
        MockHttpServletRequestBuilder request;
        if (action.equals("feedback")) {
            request = orderFeedback(order.getId(), new OrderFeedbackBody(4, "Отзыв администратора"));
        } else if (action.equals("response")) {
            order.setRating(5);
            order.setFeedback("Отзыв покупателя");
            orders.save(order);
            request = sellerResponse(order.getId(), "Ответ администратора");
        } else {
            request = patch("/api/orders/{id}/{action}", order.getId(), action).with(csrf());
        }
        mvc.perform(request.with(httpBasic("admin", PASSWORD))).andExpect(status().isNoContent());

        if (action.equals("cancel")) {
            assertOrderStateAndBalances(order.getId(), OrderStatus.REFUND, "100", "0");
        } else if (action.equals("confirm")) {
            assertOrderStateAndBalances(order.getId(), OrderStatus.CLOSED, "75", "25");
        } else {
            assertOrderStateAndBalances(order.getId(), OrderStatus.OPENED, "75", "0");
            Order stored = orders.findById(order.getId()).orElseThrow();
            if (action.equals("feedback")) {
                assertThat(stored.getRating()).isEqualTo(4);
                assertThat(stored.getFeedback()).isEqualTo("Отзыв администратора");
            } else {
                assertThat(stored.getSellerResponse()).isEqualTo("Ответ администратора");
            }
        }
        assertThat(users.findByUsername("admin").orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{0}, затем {1}")
    @CsvSource({"confirm,confirm", "confirm,cancel", "cancel,cancel", "cancel,confirm"})
    @DisplayName("Заказ: повторное завершение или отмена возвращают 409 без повторного движения денег")
    void rejectsActionsOnFinalizedOrders(String firstAction, String secondAction) throws Exception {
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        mvc.perform(orderCreation(advice.getId()).with(user("alex"))).andExpect(status().isCreated());
        Long id = orders.findAll().getFirst().getId();
        String firstActor = "maria";
        if (firstAction.equals("confirm")) {
            firstActor = "alex";
        }
        String secondActor = "maria";
        if (secondAction.equals("confirm")) {
            secondActor = "alex";
        }
        mvc.perform(patch("/api/orders/{id}/{action}", id, firstAction).with(user(firstActor)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/orders/{id}/{action}", id, secondAction).with(user(secondActor)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Заказ не открыт"));

        if (firstAction.equals("confirm")) {
            assertOrderStateAndBalances(id, OrderStatus.CLOSED, "75", "25");
        } else {
            assertOrderStateAndBalances(id, OrderStatus.REFUND, "100", "0");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "confirm"})
    @DisplayName("Заказ: отмена и подтверждение несуществующего заказа возвращают 404")
    void rejectsFinalizingUnknownOrder(String action) throws Exception {
        savedUser("alex", new BigDecimal("100"));
        mvc.perform(patch("/api/orders/999/{action}", action).with(user("alex")).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Заказ не найден"));
        assertThat(users.findByUsername("alex").orElseThrow().getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Сессия: вход сохраняется без Basic, CSRF защищает logout, выход завершает сессию")
    void sessionSurvivesRequestsAndLogoutRequiresCsrf() throws Exception {
        savedUser("alex");
        var login = mvc.perform(get("/api/user/me").with(httpBasic("alex", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/user/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("alex"));

        var csrfResult = mvc.perform(get("/csrf").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        var token = mapper.readTree(csrfResult.getResponse().getContentAsString());
        mvc.perform(patch("/api/user/me").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":25,\"city\":null,\"about\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.age").value(25));

        mvc.perform(post("/api/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/user/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/logout").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "refund"})
    @DisplayName("Администрирование: повторное открытие удерживает деньги даже при нулевом балансе продавца")
    void adminCanReopenAndResolveOrderWithSellerDebt(String resolution) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long id = orderService.createOrder(() -> "alex", advice.getId()).id();
        mvc.perform(orderConfirmation(id).with(user("alex"))).andExpect(status().isNoContent());
        // Продавец уже потратил выручку: повторное удержание может увести баланс в минус.
        jdbc.update("UPDATE users SET balance=0, version=version+1 WHERE username='maria'");
        mvc.perform(adminOrderAction(id, "reopen")).andExpect(status().isNoContent());
        assertOrderStateAndBalances(id, OrderStatus.OPENED, "75", "-25");
        mvc.perform(adminOrderAction(id, "reopen")).andExpect(status().isConflict());
        assertOrderStateAndBalances(id, OrderStatus.OPENED, "75", "-25");
        mvc.perform(adminOrderAction(id, resolution)).andExpect(status().isNoContent());
        if (resolution.equals("confirm")) {
            assertOrderStateAndBalances(id, OrderStatus.CLOSED, "75", "0");
        } else {
            assertOrderStateAndBalances(id, OrderStatus.REFUND, "100", "-25");
        }
        mvc.perform(adminOrderAction(id, resolution)).andExpect(status().isConflict());
        if (resolution.equals("confirm")) {
            assertOrderStateAndBalances(id, OrderStatus.CLOSED, "75", "0");
        } else {
            assertOrderStateAndBalances(id, OrderStatus.REFUND, "100", "-25");
        }
        assertThat(users.findByUsername("admin").orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({"reopen,OPENED", "reopen,REFUND", "reopen,ARCHIVED",
            "confirm,CLOSED", "confirm,REFUND", "confirm,ARCHIVED",
            "refund,CLOSED", "refund,REFUND", "refund,ARCHIVED"})
    @DisplayName("Администрирование: недопустимый переход возвращает 409 без движения денег")
    void adminActionsRejectWrongOrderStatus(String action, OrderStatus initial) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long id = orderService.createOrder(() -> "alex", advice.getId()).id();
        Order order = orders.findById(id).orElseThrow();
        order.setStatus(initial);
        orders.save(order);
        mvc.perform(adminOrderAction(id, action)).andExpect(status().isConflict());
        assertOrderStateAndBalances(id, initial, "75", "0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reopen", "confirm", "refund"})
    @DisplayName("Администрирование: действия требуют авторизацию, роль ADMIN и CSRF")
    void adminOrderActionsRequireAuthenticationRoleAndCsrf(String action) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long id = orderService.createOrder(() -> "alex", advice.getId()).id();
        mvc.perform(patch("/api/admin/orders/{id}/{action}", id, action).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/orders/{id}/{action}", id, action).with(user("alex")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/orders/{id}/{action}", id, action).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertOrderStateAndBalances(id, OrderStatus.OPENED, "75", "0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reopen", "confirm", "refund"})
    @DisplayName("Администрирование: старая сессия с отозванной в БД ролью не разрешает действие")
    void revokedAdminSessionCannotChangeOrder(String action) throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long id = orderService.createOrder(() -> "alex", advice.getId()).id();
        var login = mvc.perform(get("/api/user/me").with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        jdbc.update("UPDATE users SET role='USER', version=version+1 WHERE username='admin'");
        mvc.perform(patch("/api/admin/orders/{id}/{action}", id, action).session(session).with(csrf()))
                .andExpect(status().isForbidden());
        assertOrderStateAndBalances(id, OrderStatus.OPENED, "75", "0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reopen", "confirm", "refund"})
    @DisplayName("Администрирование: невалидный ID — 400, отсутствующий заказ — 404")
    void adminActionsValidateOrderId(String action) throws Exception {
        savedAdmin();
        for (String id : List.of("0", "-1", "abc")) {
            mvc.perform(adminOrderAction(id, action)).andExpect(status().isBadRequest());
        }
        mvc.perform(adminOrderAction(Long.MAX_VALUE, action)).andExpect(status().isNotFound());
        assertThat(orders.count()).isZero();
        assertThat(users.findByUsername("admin").orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Администрирование: одновременные подтверждение и возврат дают 204/409 и одно движение денег")
    void concurrentAdminConfirmAndRefundCannotReleaseMoneyTwice() throws Exception {
        savedAdmin();
        Advice advice = orderFixture(new BigDecimal("100"), PRICE);
        Long id = orderService.createOrder(() -> "alex", advice.getId()).id();
        var executor = Executors.newFixedThreadPool(2);
        try (var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE")) {
                statement.setLong(1, id);
                statement.executeQuery().close();
            }
            var confirm = executor.submit(() -> mvc.perform(adminOrderAction(id, "confirm")).andReturn().getResponse().getStatus());
            var refund = executor.submit(() -> mvc.perform(adminOrderAction(id, "refund")).andReturn().getResponse().getStatus());
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Long.class))
                                .isEqualTo(2));
            } finally { gate.commit(); }
            assertThat(List.of(confirm.get(15, TimeUnit.SECONDS), refund.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
            if (orders.findById(id).orElseThrow().getStatus() == OrderStatus.CLOSED) {
                assertOrderStateAndBalances(id, OrderStatus.CLOSED, "75", "25");
            } else {
                assertOrderStateAndBalances(id, OrderStatus.REFUND, "100", "0");
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MockHttpServletRequestBuilder adminOrderAction(Object id, String action) {
        return patch("/api/admin/orders/{id}/{action}", id, action).with(user("admin").roles("ADMIN")).with(csrf());
    }

    private void concurrentPurchase(Long adviceId, CountDownLatch bothLoaded) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            // Обе транзакции сначала читают одного покупателя с балансом 100 и одной версией.
            users.findByUsername("alex").orElseThrow();
            bothLoaded.countDown();
            try {
                if (!bothLoaded.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Вторая покупка не дошла до чтения баланса");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            orderService.createOrder(() -> "alex", adviceId);
        });
    }

    private void archiveGameThroughApi(Long gameId) throws Exception {
        mvc.perform(gameArchiving(gameId).with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isNoContent());
    }

    private void archiveAdviceThroughApi(Long adviceId) throws Exception {
        mvc.perform(delete("/api/advices/{id}", adviceId).with(user("maria")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void assertSellerResponseInDatabaseAndResponse(Long orderId, String responseText) throws Exception {
        assertThat(orders.findById(orderId).orElseThrow().getSellerResponse()).isEqualTo(responseText);

        for (String username : List.of("alex", "maria")) {
            mvc.perform(get("/api/orders/{id}", orderId).with(user(username)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerResponseText").value(responseText));
        }
    }

    private void assertFeedbackInDatabaseAndResponse(Long orderId, int rating, String feedbackText) throws Exception {
        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getRating()).isEqualTo(rating);
        assertThat(order.getFeedback()).isEqualTo(feedbackText);

        mvc.perform(get("/api/orders/{id}", orderId).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(rating))
                .andExpect(jsonPath("$.feedbackText").value(feedbackText));
    }

    private void assertOrderStateAndBalances(Long orderId, OrderStatus expectedStatus, String buyerBalance) {
        assertOrderStateAndBalances(orderId, expectedStatus, buyerBalance, "0");
    }

    private void assertOrderStateAndBalances(Long orderId, OrderStatus expectedStatus,
                                            String buyerBalance, String sellerBalance) {
        assertThat(orders.count()).isEqualTo(1);
        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(expectedStatus);
        assertThat(order.getPrice()).isEqualByComparingTo(PRICE);
        assertThat(users.findByUsername("alex").orElseThrow().getBalance())
                .isEqualByComparingTo(buyerBalance);
        assertThat(users.findByUsername("maria").orElseThrow().getBalance())
                .isEqualByComparingTo(sellerBalance);
    }

    private Advice orderFixture(BigDecimal buyerBalance, BigDecimal price) {
        savedUser("alex", buyerBalance);
        User seller = savedUser("maria");
        Game game = games.save(new Game("Puzzle Arena"));
        return advices.save(new Advice("Предмет", game, seller, price, AdviceStatus.ACTIVE));
    }

    private User savedAdmin() {
        User admin = savedUser("admin");
        admin.setRole(UserRole.ADMIN);
        return users.save(admin);
    }

    private User savedUser(String name) {
        return savedUser(name, BigDecimal.ZERO);
    }

    private User savedUser(String name, BigDecimal balance) {
        return users.save(new User(name, passwordEncoder.encode(PASSWORD), balance));
    }

    private MockHttpServletRequestBuilder gameArchiving(Object gameId) {
        return patch("/api/games/{id}/delete", gameId).with(csrf());
    }

    private MockHttpServletRequestBuilder gameCreation(String name) throws JsonProcessingException {
        return post("/api/games")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new GameBody(name)));
    }

    private MockHttpServletRequestBuilder registration(String name) throws JsonProcessingException {
        return post("/api/user")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new UserBody(name, PASSWORD)));
    }

    private MockHttpServletRequestBuilder creation(AdviceBody body) throws JsonProcessingException {
        return post("/api/advices")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder orderCreation(Long adviceId) throws JsonProcessingException {
        return post("/api/orders/")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ItemBro.Advice.DTO.AdviceIdBody(adviceId)));
    }

    private MockHttpServletRequestBuilder orderCancellation(Long orderId) {
        return patch("/api/orders/{id}/cancel", orderId).with(csrf());
    }

    private MockHttpServletRequestBuilder orderConfirmation(Long orderId) {
        return patch("/api/orders/{id}/confirm", orderId).with(csrf());
    }

    private MockHttpServletRequestBuilder orderFeedback(Object orderId, OrderFeedbackBody body)
            throws JsonProcessingException {
        return patch("/api/orders/{id}/feedback", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder sellerResponse(Object orderId, String responseText)
            throws JsonProcessingException {
        return patch("/api/orders/{id}/response", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new OrderResponseBody(responseText)));
    }
}
