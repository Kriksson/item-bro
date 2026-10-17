package ItemBro;

import ItemBro.Advice.Advice;
import ItemBro.Advice.AdviceRepository;
import ItemBro.Advice.AdviceStatus;
import ItemBro.Game.Game;
import ItemBro.Game.GameRepository;
import ItemBro.Game.GameStatus;
import ItemBro.Order.Order;
import ItemBro.Order.OrderRepository;
import ItemBro.Order.OrderStatus;
import ItemBro.User.DTO.UserDTO;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import ItemBro.User.UserRole;
import ItemBro.User.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProfileMediaApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("item_bro_profile_test")
            .withUsername("test")
            .withPassword("test");

    private static final String PASSWORD = "Strong123!";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9V0AAAAASUVORK5CYII=");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired AdviceRepository advices;
    @Autowired OrderRepository orders;
    @Autowired UserService userService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @TempDir Path uploadDirectory;
    private MockedStatic<Path> storagePaths;

    @BeforeEach
    void isolateDatabaseAndUploads() {
        jdbc.execute("TRUNCATE TABLE orders, advices, games, users RESTART IDENTITY");
        // В production путь пока задан в сервисах. Подменяем только его корень,
        // сохраняя настоящие файловые операции, Tika и транзакции PostgreSQL.
        storagePaths = mockStatic(Path.class, CALLS_REAL_METHODS);
        storagePaths.when(() -> Path.of("/app/uploads")).thenReturn(uploadDirectory);
    }

    @AfterEach
    void restoreStoragePaths() {
        if (storagePaths != null) {
            storagePaths.close();
        }
    }

    @Test
    @DisplayName("Профиль: обновляет свои данные и сохраняет баланс, роль, пароль и аватарку")
    void updatesOwnProfileWithoutChangingProtectedFields() throws Exception {
        User account = savedUser("alex");
        account.setAvatarUrl("/api/media/" + UUID.randomUUID());
        users.save(account);
        User other = savedUser("maria");

        mvc.perform(profileUpdate("""
                        {"age":25,"city":"  Казань  ","about":"  Играю по вечерам.  "}
                        """).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alex"))
                .andExpect(jsonPath("$.userRole").value("USER"))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.avatarUrl").value(account.getAvatarUrl()))
                .andExpect(jsonPath("$.age").value(25))
                .andExpect(jsonPath("$.city").value("Казань"))
                .andExpect(jsonPath("$.about").value("Играю по вечерам."))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User stored = users.findById(account.getId()).orElseThrow();
        assertThat(stored.getAge()).isEqualTo(25);
        assertThat(stored.getCity()).isEqualTo("Казань");
        assertThat(stored.getAbout()).isEqualTo("Играю по вечерам.");
        assertThat(stored.getBalance()).isEqualByComparingTo("100");
        assertThat(stored.getRole()).isEqualTo(UserRole.USER);
        assertThat(stored.getPasswordHash()).isEqualTo(account.getPasswordHash());
        assertThat(stored.getAvatarUrl()).isEqualTo(account.getAvatarUrl());
        assertThat(users.findById(other.getId()).orElseThrow().getCity()).isEqualTo("Москва");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"age\":null,\"city\":null,\"about\":null}",
            "{\"age\":null,\"city\":\"   \",\"about\":\" \\n\\t \"}"
    })
    @DisplayName("Профиль: null и пробельные строки очищают необязательные поля")
    void clearsOptionalProfileFields(String body) throws Exception {
        savedUser("alex");

        mvc.perform(profileUpdate(body).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(nullValue()))
                .andExpect(jsonPath("$.city").value(nullValue()))
                .andExpect(jsonPath("$.about").value(nullValue()));

        User stored = users.findByUsername("alex").orElseThrow();
        assertThat(stored.getAge()).isNull();
        assertThat(stored.getCity()).isNull();
        assertThat(stored.getAbout()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 120})
    @DisplayName("Профиль: принимает границы возраста и длины строк после trim")
    void acceptsProfileValidationBoundaries(int age) throws Exception {
        savedUser("alex");
        String city = "а".repeat(100);
        String about = "б".repeat(1000);
        String body = mapper.createObjectNode().put("age", age)
                .put("city", "  " + city + "  ").put("about", "  " + about + "  ").toString();

        mvc.perform(profileUpdate(body).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(age))
                .andExpect(jsonPath("$.city").value(city))
                .andExpect(jsonPath("$.about").value(about));
        assertThat(users.findByUsername("alex").orElseThrow().getCity()).isEqualTo(city);
        assertThat(users.findByUsername("alex").orElseThrow().getAbout()).isEqualTo(about);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProfiles")
    @DisplayName("Профиль: невалидные данные возвращают 400 с message без изменения БД")
    void rejectsInvalidProfile(String description, String body, String expectedMessage) throws Exception {
        savedUser("alex");

        mvc.perform(profileUpdate(body).with(user("alex")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage));

        User stored = users.findByUsername("alex").orElseThrow();
        assertThat(stored.getAge()).isEqualTo(30);
        assertThat(stored.getCity()).isEqualTo("Москва");
        assertThat(stored.getAbout()).isEqualTo("Исходное описание");
    }

    static Stream<Arguments> invalidProfiles() {
        return Stream.of(
                Arguments.of("возраст 0", "{\"age\":0}", "Возраст строго от 1 года"),
                Arguments.of("возраст 121", "{\"age\":121}", "Возраст строго до 120 лет."),
                Arguments.of("дробный возраст", "{\"age\":25.9}", "Некорректное тело запроса"),
                Arguments.of("нечисловой возраст", "{\"age\":\"abc\"}", "Некорректное тело запроса"),
                Arguments.of("город длиннее 100", "{\"city\":\"" + "а".repeat(101) + "\"}",
                        "Максимум 100 символов"),
                Arguments.of("описание длиннее 1000", "{\"about\":\"" + "б".repeat(1001) + "\"}",
                        "Максимум 1000 символов"),
                Arguments.of("объект вместо города", "{\"city\":{}}", "Некорректное тело запроса"),
                Arguments.of("повреждённый JSON", "{\"age\":", "Некорректное тело запроса"));
    }

    @Test
    @DisplayName("Профиль: без авторизации возвращает 401 при корректном CSRF")
    void rejectsAnonymousProfileUpdate() throws Exception {
        savedUser("alex");
        mvc.perform(profileUpdate("{\"age\":25}"))
                .andExpect(status().isUnauthorized());
        assertThat(users.findByUsername("alex").orElseThrow().getAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("Профиль: без CSRF возвращает 403 и не меняет данные")
    void rejectsProfileUpdateWithoutCsrf() throws Exception {
        savedUser("alex");
        mvc.perform(patch("/api/user/me").with(user("alex"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"age\":25}"))
                .andExpect(status().isForbidden());
        assertThat(users.findByUsername("alex").orElseThrow().getAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("Профиль: удалённый из БД пользователь получает 404")
    void rejectsProfileUpdateForDeletedUser() throws Exception {
        mvc.perform(profileUpdate("{\"age\":25}").with(user("deleted")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(users.count()).isZero();
    }

    @Test
    @DisplayName("Мой профиль: возвращает актуальные данные и роль из БД")
    void readsOwnProfile() throws Exception {
        User account = savedUser("alex");
        account.setRole(UserRole.ADMIN);
        users.save(account);

        mvc.perform(get("/api/user/me").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alex"))
                .andExpect(jsonPath("$.userRole").value("ADMIN"))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.city").value("Москва"))
                .andExpect(jsonPath("$.about").value("Исходное описание"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Мой профиль: без авторизации возвращает 401")
    void rejectsAnonymousOwnProfileRead() throws Exception {
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Публичный профиль: доступен гостю без баланса и пароля")
    void readsPublicProfileWithoutPrivateFields() throws Exception {
        User account = savedUser("alex");
        account.setRole(UserRole.ADMIN);
        account.setAvatarUrl("/api/media/" + UUID.randomUUID());
        users.save(account);

        mvc.perform(get("/api/users/alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.userRole").value("ADMIN"))
                .andExpect(jsonPath("$.avatarUrl").value(account.getAvatarUrl()))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.city").value("Москва"))
                .andExpect(jsonPath("$.about").value("Исходное описание"))
                .andExpect(jsonPath("$.balance").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/advices", "/reviews"})
    @DisplayName("Публичный профиль: несуществующий пользователь возвращает 404")
    void rejectsUnknownPublicProfile(String suffix) throws Exception {
        mvc.perform(get("/api/users/missing" + suffix))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Публичные объявления: возвращает только активные объявления владельца с изображениями")
    void readsOnlyOwnersActiveAdvices() throws Exception {
        User seller = savedUser("alex");
        seller.setRole(UserRole.ADMIN);
        seller.setAvatarUrl("/api/media/" + UUID.randomUUID());
        seller = users.save(seller);
        User other = savedUser("maria");
        Game game = games.save(new Game("Puzzle Arena"));
        game.setImageUrl("/api/media/" + UUID.randomUUID());
        game = games.save(game);
        Advice visible = new Advice("Предмет", game, seller, BigDecimal.TEN, AdviceStatus.ACTIVE);
        visible.setImageUrl("/api/media/" + UUID.randomUUID());
        visible = advices.save(visible);
        advices.save(new Advice("Архив", game, seller, BigDecimal.TEN, AdviceStatus.ARCHIVED));
        advices.save(new Advice("Чужое", game, other, BigDecimal.TEN, AdviceStatus.ACTIVE));

        mvc.perform(get("/api/users/alex/advices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(visible.getId().intValue()))
                .andExpect(jsonPath("$[0].imageUrl").value(visible.getImageUrl()))
                .andExpect(jsonPath("$[0].game.imageUrl").value(game.getImageUrl()))
                .andExpect(jsonPath("$[0].userSeller.userRole").value("ADMIN"))
                .andExpect(jsonPath("$[0].userSeller.avatarUrl").value(seller.getAvatarUrl()));
    }

    @Test
    @DisplayName("Публичные объявления: архивная игра скрывает объявления и в профиле")
    void hidesAdvicesOfArchivedGamesInPublicProfile() throws Exception {
        User seller = savedUser("alex");
        Game game = new Game("Puzzle Arena");
        game.setStatus(GameStatus.ARCHIVED);
        game = games.save(game);
        advices.save(new Advice("Предмет", game, seller, BigDecimal.TEN, AdviceStatus.ACTIVE));

        mvc.perform(get("/api/users/alex/advices"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("Отзывы профиля: доступны гостю и содержат только отзывы о выбранном продавце")
    void readsOnlySelectedSellersReviews() throws Exception {
        User seller = savedUser("alex");
        User buyer = savedUser("maria");
        Game game = games.save(new Game("Puzzle Arena"));
        Advice advice = advices.save(new Advice("Предмет", game, seller, BigDecimal.TEN, AdviceStatus.ACTIVE));
        Order reviewed = new Order(advice, buyer, seller, BigDecimal.TEN, OrderStatus.OPENED);
        reviewed.setRating(5);
        reviewed.setFeedback("Всё хорошо");
        reviewed.setSellerResponse("Спасибо");
        orders.save(reviewed);
        orders.save(new Order(advice, buyer, seller, BigDecimal.TEN, OrderStatus.OPENED));
        Advice anotherAdvice = advices.save(new Advice("Другой предмет", game, buyer,
                BigDecimal.TEN, AdviceStatus.ACTIVE));
        Order anotherSeller = new Order(anotherAdvice, seller, buyer, BigDecimal.TEN, OrderStatus.CLOSED);
        anotherSeller.setRating(1);
        anotherSeller.setFeedback("Другой продавец");
        orders.save(anotherSeller);

        mvc.perform(get("/api/users/alex/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].feedback").value("Всё хорошо"))
                .andExpect(jsonPath("$[0].sellerResponse").value("Спасибо"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/advices", "/reviews"})
    @DisplayName("Публичный профиль: при отсутствии объявлений и отзывов возвращает пустой массив")
    void readsEmptyPublicProfileLists(String suffix) throws Exception {
        savedUser("alex");
        mvc.perform(get("/api/users/alex" + suffix))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedImages")
    @DisplayName("Аватарка: сохраняет JPEG, PNG и WebP и отдаёт файл гостю с правильным Content-Type")
    void uploadsAvatarAndServesItsActualContentType(String contentType, byte[] bytes) throws Exception {
        savedUser("alex");
        var result = mvc.perform(avatarUpload(new MockMultipartFile("file", "avatar.bin",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)).with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alex"))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andReturn();

        String url = mapper.readTree(result.getResponse().getContentAsString()).get("avatarUrl").asText();
        assertThat(UUID.fromString(url.substring("/api/media/".length()))).isNotNull();
        assertThat(users.findByUsername("alex").orElseThrow().getAvatarUrl()).isEqualTo(url);
        assertThat(Files.readAllBytes(fileFor(url))).isEqualTo(bytes);
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType(contentType)).andExpect(content().bytes(bytes));
    }

    static Stream<Arguments> supportedImages() throws IOException {
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        return Stream.of(Arguments.of("image/png", PNG),
                Arguments.of("image/jpeg", jpeg.toByteArray()), Arguments.of("image/webp", webp));
    }

    @Test
    @DisplayName("Аватарка: замена меняет URL и удаляет старый файл после коммита")
    void replacesAvatarAndDeletesOldFile() throws Exception {
        String oldUrl = savedAvatar();
        var result = mvc.perform(avatarUpload(pngFile()).with(user("alex")))
                .andExpect(status().isOk()).andReturn();
        String newUrl = mapper.readTree(result.getResponse().getContentAsString()).get("avatarUrl").asText();

        assertThat(newUrl).isNotEqualTo(oldUrl);
        assertThat(Files.exists(fileFor(oldUrl))).isFalse();
        assertThat(Files.readAllBytes(fileFor(newUrl))).isEqualTo(PNG);
        mvc.perform(get(oldUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Аватарка: файл размером ровно 5 МиБ принимается")
    void acceptsAvatarAtSizeLimit() throws Exception {
        savedUser("alex");
        byte[] bytes = Arrays.copyOf(PNG, 5 * 1024 * 1024);
        mvc.perform(avatarUpload(new MockMultipartFile("file", "avatar.png", "image/png", bytes))
                        .with(user("alex")))
                .andExpect(status().isOk());
        String url = users.findByUsername("alex").orElseThrow().getAvatarUrl();
        assertThat(Files.size(fileFor(url))).isEqualTo(bytes.length);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAvatars")
    @DisplayName("Аватарка: пустой, поддельный и слишком большой файл не заменяют старый")
    void rejectsInvalidAvatar(String description, byte[] bytes) throws Exception {
        String oldUrl = savedAvatar();
        mvc.perform(avatarUpload(new MockMultipartFile("file", "avatar.png", "image/png", bytes))
                        .with(user("alex")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertAvatarUnchanged(oldUrl);
    }

    static Stream<Arguments> invalidAvatars() {
        return Stream.of(Arguments.of("пустой файл", new byte[0]),
                Arguments.of("текст под видом PNG", "Это не изображение".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("больше 5 МиБ", Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1)));
    }

    @Test
    @DisplayName("Аватарка: отсутствие multipart-поля file возвращает 400")
    void rejectsUploadWithoutFilePart() throws Exception {
        String oldUrl = savedAvatar();
        mvc.perform(multipart(HttpMethod.PUT, "/api/user/me/avatar").with(user("alex")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertAvatarUnchanged(oldUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    @DisplayName("Аватарка: без авторизации возвращает 401 с корректным CSRF")
    void rejectsAnonymousAvatarChanges(String method) throws Exception {
        String oldUrl = savedAvatar();
        mvc.perform(avatarChange(method).with(csrf())).andExpect(status().isUnauthorized());
        assertAvatarUnchanged(oldUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    @DisplayName("Аватарка: некорректный CSRF возвращает 403 и сохраняет файл")
    void rejectsAvatarChangesWithInvalidCsrf(String method) throws Exception {
        String oldUrl = savedAvatar();
        mvc.perform(avatarChange(method).with(user("alex")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertAvatarUnchanged(oldUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    @DisplayName("Аватарка: удалённый из БД пользователь получает 404")
    void rejectsAvatarChangesForDeletedUser(String method) throws Exception {
        mvc.perform(avatarChange(method).with(user("deleted")).with(csrf()))
                .andExpect(status().isNotFound());
        try (var files = Files.list(uploadDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    @DisplayName("Аватарка: удаление очищает URL в БД и удаляет файл")
    void deletesAvatarAfterCommit() throws Exception {
        String oldUrl = savedAvatar();
        mvc.perform(delete("/api/user/me/avatar").with(user("alex")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alex"))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
        assertThat(users.findByUsername("alex").orElseThrow().getAvatarUrl()).isNull();
        assertThat(Files.exists(fileFor(oldUrl))).isFalse();
        mvc.perform(get(oldUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Аватарка: повторное удаление успешно и не удаляет другие файлы")
    void deletingMissingAvatarIsIdempotent() throws Exception {
        savedUser("alex");
        Path anotherFile = Files.write(uploadDirectory.resolve(UUID.randomUUID().toString()), PNG);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete("/api/user/me/avatar").with(user("alex")).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(nullValue()));
        }
        assertThat(Files.readAllBytes(anotherFile)).isEqualTo(PNG);
    }

    @Test
    @DisplayName("Аватарка: откат замены сохраняет старый файл и удаляет новый")
    void rolledBackReplacementPreservesOldAvatar() throws Exception {
        String oldUrl = savedAvatar();
        String newUrl = new TransactionTemplate(transactionManager).execute(transaction -> {
            try {
                UserDTO result = userService.uploadAvatar(() -> "alex", pngFile());
                assertThat(Files.exists(fileFor(oldUrl))).isTrue();
                assertThat(Files.exists(fileFor(result.avatarUrl()))).isTrue();
                transaction.setRollbackOnly();
                return result.avatarUrl();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });

        assertAvatarUnchanged(oldUrl);
        assertThat(Files.exists(fileFor(newUrl))).isFalse();
    }

    @Test
    @DisplayName("Аватарка: откат удаления сохраняет URL и физический файл")
    void rolledBackDeletionPreservesAvatar() throws Exception {
        String oldUrl = savedAvatar();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            try {
                assertThat(userService.deleteAvatar(() -> "alex").avatarUrl()).isNull();
                assertThat(Files.exists(fileFor(oldUrl))).isTrue();
                transaction.setRollbackOnly();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        assertAvatarUnchanged(oldUrl);
    }

    @Test
    @DisplayName("Аватарка: IOException при записи удаляет частично созданный файл")
    void failedTransferCleansUpPartialFile() throws Exception {
        String oldUrl = savedAvatar();
        MockMultipartFile brokenFile = new MockMultipartFile("file", "avatar.png", "image/png", PNG) {
            @Override
            public void transferTo(Path destination) throws IOException {
                Files.write(destination, new byte[]{1, 2, 3});
                throw new IOException("Ошибка записи тестового файла");
            }
        };

        assertThatThrownBy(() -> userService.uploadAvatar(() -> "alex", brokenFile))
                .isInstanceOf(IOException.class);
        assertAvatarUnchanged(oldUrl);
    }

    @Test
    @DisplayName("Медиа: отсутствующий файл возвращает 404 с message")
    void rejectsMissingMedia() throws Exception {
        mvc.perform(get("/api/media/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "photo.png", "550e8400-e29b-41d4-a716-446655440000.png"})
    @DisplayName("Медиа: некорректный идентификатор возвращает 400")
    void rejectsInvalidMediaId(String fileId) throws Exception {
        mvc.perform(get("/api/media/{id}", fileId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Медиа: каталог вместо файла возвращает 404")
    void rejectsDirectoryAsMedia() throws Exception {
        String fileId = UUID.randomUUID().toString();
        Files.createDirectory(uploadDirectory.resolve(fileId));
        mvc.perform(get("/api/media/{id}", fileId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Медиа: файл неподдерживаемого типа не отдаётся клиенту")
    void rejectsUnsupportedMediaContent() throws Exception {
        String fileId = UUID.randomUUID().toString();
        Files.writeString(uploadDirectory.resolve(fileId), "<html>not an image</html>");
        mvc.perform(get("/api/media/{id}", fileId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    private User savedUser(String username) {
        User account = new User(username, passwordEncoder.encode(PASSWORD), new BigDecimal("100.00"));
        account.setAge(30);
        account.setCity("Москва");
        account.setAbout("Исходное описание");
        return users.save(account);
    }

    private String savedAvatar() throws IOException {
        User account = savedUser("alex");
        String fileId = UUID.randomUUID().toString();
        Files.write(uploadDirectory.resolve(fileId), PNG);
        String url = "/api/media/" + fileId;
        account.setAvatarUrl(url);
        users.save(account);
        return url;
    }

    private void assertAvatarUnchanged(String oldUrl) throws IOException {
        assertThat(users.findByUsername("alex").orElseThrow().getAvatarUrl()).isEqualTo(oldUrl);
        assertThat(Files.readAllBytes(fileFor(oldUrl))).isEqualTo(PNG);
        try (var files = Files.list(uploadDirectory)) {
            assertThat(files).containsExactly(fileFor(oldUrl));
        }
    }

    private Path fileFor(String url) {
        return uploadDirectory.resolve(url.substring("/api/media/".length()));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", PNG);
    }

    private MockHttpServletRequestBuilder avatarUpload(MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/api/user/me/avatar").file(file).with(csrf());
    }

    private MockHttpServletRequestBuilder avatarChange(String method) {
        if (method.equals("PUT")) {
            return multipart(HttpMethod.PUT, "/api/user/me/avatar").file(pngFile());
        }
        return delete("/api/user/me/avatar");
    }

    private MockHttpServletRequestBuilder profileUpdate(String body) {
        return patch("/api/user/me").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
