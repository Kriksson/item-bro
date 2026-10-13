# Item Bro

Окружение для учебного REST API: Java 21, Spring Boot 3.5.16, Maven,
PostgreSQL 17. Backend реализует владелец проекта.

## Подключённые зависимости

| Зависимость | Назначение |
| --- | --- |
| Spring Web | REST-контроллеры, JSON |
| Spring Data JPA | Сущности, стандартные репозитории, транзакции |
| PostgreSQL JDBC | Подключение к PostgreSQL |
| Validation | Проверка DTO через `@Valid` и ограничения полей |
| Spring Security | Аутентификация, доступ к операциям, хеширование паролей |
| Flyway + PostgreSQL module | Версионирование SQL-схемы |
| Spring Boot Test | JUnit 5, Mockito, Spring Test, MockMvc |
| Spring Security Test | Проверка авторизации в будущих тестах |
| Testcontainers PostgreSQL + JUnit Jupiter | Будущие интеграционные тесты с PostgreSQL |

Версии библиотек управляются Spring Boot. Тестовые библиотеки имеют scope `test`.
Тесты пока не созданы.

## Локальное окружение

В IntelliJ IDEA импортировать `pom.xml` как Maven-проект. Выбрать JDK 21 в
Project SDK и Maven Runner JRE, Maven home — Wrapper.
Установленный на этом компьютере JDK 21:
`/home/kriksson/.jdks/temurin-21.0.12.1`.

Для терминала на этом компьютере:

```bash
export JAVA_HOME=/home/kriksson/.jdks/temurin-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -version
```

Поднять только PostgreSQL (доступно уже сейчас):

```bash
docker compose up -d --wait db
```

Параметры подключения из Database tool IntelliJ или приложения на хосте:

| Параметр | Значение по умолчанию |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `item_bro` |
| Users | `item_bro` |
| Password | `item_bro_dev` (только для локальной разработки) |
| JDBC URL | `jdbc:postgresql://localhost:5433/item_bro` |

Порт `5433` выбран, поскольку `5432` на этом компьютере уже занят локальным
PostgreSQL. Внутри контейнера PostgreSQL использует порт `5432`.
База доступна только через localhost. Данные сохраняются в Docker volume
`item-bro_postgres_data`. Остановка с сохранением данных: `docker compose down`.

В Docker приложение подключается по адресу `jdbc:postgresql://db:5432/item_bro`:
`db` — имя сервиса базы, `5432` — внутренний порт PostgreSQL. Эти значения
по умолчанию заданы и в `application.properties`, и в окружении сервиса `app`.

После создания класса с `main` и `@SpringBootApplication` собрать и запустить
Java-приложение вместе с базой:

```bash
docker compose up -d --build app
docker compose logs -f app
```

HTTP-порт приложения: `http://localhost:8080`. Dockerfile собирает JAR через
Maven Wrapper на JDK 21, пропуская тесты, и запускает его на JRE 21.
Пока класса `main` нет, сборка полного образа завершится ошибкой упаковки JAR.

Для изменения параметров Compose экспортировать `DB_PORT` (порт базы на
хосте), `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `APP_PORT` либо указать их в `.env`.
Внутренний порт базы для `app` остаётся `5432`. Spring Boot не читает `.env`
автоматически: Compose передаёт настройки сервису `app` через `environment`.
Изменение имени базы или учётных данных не переинициализирует уже созданный
PostgreSQL volume.

Проверка компиляции без создания и запуска тестов:

```bash
./mvnw -Dmaven.test.skip=true compile
```

Для альтернативного запуска из терминала на хосте (после создания `main`):

```bash
DB_HOST=localhost DB_PORT=5433 ./mvnw spring-boot:run
```

В конфигурации запуска IntelliJ аналогично указать Environment variables
`DB_HOST=localhost;DB_PORT=5433`.

## Следующий этап реализации

Java-классы, таблицы, миграции, дополнительные методы репозиториев и тесты
на этом этапе не создавались. Настроено `ddl-auto=validate`: Hibernate
проверяет схему, а её создание выполняют будущие Flyway-миграции в
`src/main/resources/db/migration`, например `V1__initial_schema.sql`.
При добавлении сущностей понадобится соответствующая миграция.

Spring Security по умолчанию защищает все HTTP-запросы и создаёт временного
пользователя. Разрешение регистрации и правила доступа из задания нужно
реализовать в собственной конфигурации Security.

Модель задания: пользователь, игра, заказ и объявление. Операции:
регистрация, создание объявления, удаление своего объявления, создание заказа.
Лимит — **5 объявлений на пользователя суммарно**: отказ в создании уже при
наличии 5 объявлений. Для денег использовать `BigDecimal` в Java и `NUMERIC`
в PostgreSQL. Имена SQL-таблиц лучше выбрать `users`, `games`, `orders`,
`listings`, чтобы избежать зарезервированных слов `user` и `order`.
