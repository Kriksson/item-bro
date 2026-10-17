# ItemBro

Площадка для торговли игровыми предметами. Бэкенд на Java и Spring Boot, фронтенд на React и TypeScript.

## Возможности

- Каталог игр и объявления с покупкой через внутренний баланс.
- Личный раздел с покупками и продажами.
- Подтверждение и отмена заказов.
- Оценки, отзывы покупателей и ответы продавцов.
- Профили пользователей с активными объявлениями и отзывами.
- Настройка профиля, загрузка и удаление аватарки.
- Управление играми, объявлениями и заказами для администратора.

## Скриншоты

| Маркет | Объявления |
| --- | --- |
| [![Каталог игр](docs/screenshots/market.png)](docs/screenshots/market.png) | [![Объявления игры](docs/screenshots/listings.png)](docs/screenshots/listings.png) |
| **Заказ и чат** | **Профиль и отзывы** |
| [![Страница заказа](docs/screenshots/order.png)](docs/screenshots/order.png) | [![Профиль продавца](docs/screenshots/profile.png)](docs/screenshots/profile.png) |

## Стек

**Бэкенд:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, Flyway.

**Фронтенд:** React, TypeScript, Vite.

**Окружение и тесты:** Docker Compose, Nginx, Maven, JUnit 5, Testcontainers, Playwright.

## Запуск

Нужны Docker и Docker Compose. Из корня проекта:

```bash
docker compose up --build -d
docker compose -f frontend/compose.yaml up --build -d
```

- Сайт: http://localhost:5173
- API: http://localhost:8085

Бэкенд и PostgreSQL запускаются первым Compose-файлом, фронтенд — вторым. Фронтенд собирается в статические файлы и работает через Nginx, который передаёт запросы API бэкенду.

Данные PostgreSQL и загруженные файлы сохраняются в Docker volumes.

## Разработка фронтенда

При работающем бэкенде вместо контейнера фронтенда можно запустить Vite:

```bash
cd frontend
npm ci
npm run dev
```

Сайт будет доступен на http://localhost:5173. Запросы `/api` и `/csrf` проксируются на бэкенд по адресу http://localhost:8085.

## Особенности реализации

- Пароли хешируются через BCrypt. После входа используется HTTP-сессия, изменяющие запросы защищены CSRF-токеном.
- Операции с заказами и балансами выполняются в транзакциях.
- Денежные значения хранятся в `BigDecimal`, конкурентные изменения пользователей и заказов контролируются через `@Version`.
- Схема базы данных обновляется миграциями Flyway.
- Игры и объявления архивируются с сохранением существующих заказов.

## В разработке

Загрузка изображений игр и объявлений, серверная часть чата заказов.
