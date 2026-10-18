import { chromium, request, expect } from "@playwright/test";
import { createServer } from "vite";
import react from "@vitejs/plugin-react";
import { execFileSync } from "node:child_process";
import { readFile, writeFile, mkdir, mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { avatar, cover } from "./gallery-art.mjs";

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const root = resolve(frontend, "..");
const out = resolve(root, "docs/screenshots");
const temp = await mkdtemp(resolve(tmpdir(), "item-bro-gallery-"));
const password = "Gallery123!";
const tag = `item-bro-gallery-${process.pid}`;
const apiPort = Number(process.env.GALLERY_API_PORT || 18086);
const port = Number(process.env.GALLERY_PORT || 5187);
const base = `http://127.0.0.1:${port}`;
const shots = [];
const checks = [];
const contexts = [];
const clients = new Map();
const errors = [];
let browser, vite;
const docker = (...args) =>
  execFileSync("docker", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
const log = (value) => console.log(`[gallery] ${value}`);
const pause = (ms) => new Promise((done) => setTimeout(done, ms));

async function api(user, method, path, body, file) {
  const client = clients.get(user);
  const csrf = await (await client.get("/csrf")).json();
  const options = { method, headers: { [csrf.headerName]: csrf.token } };
  if (body !== undefined) options.data = body;
  if (file)
    options.multipart = {
      file: {
        name: "image.png",
        mimeType: "image/png",
        buffer: await readFile(file),
      },
    };
  const response = await client.fetch(path, options);
  if (!response.ok())
    throw new Error(
      `${user} ${method} ${path}: ${response.status()} ${await response.text()}`,
    );
  return response.status() === 204 ? null : response.json();
}

async function art(name, svg, size = { width: 1000, height: 660 }) {
  const page = await browser.newPage({ viewport: size, deviceScaleFactor: 1 });
  await page.setContent(
    `<style>body{margin:0}svg{display:block;width:100vw;height:100vh}</style>${svg}`,
  );
  const path = resolve(temp, `${name}.png`);
  await page.screenshot({ path });
  await page.close();
  return path;
}

async function createUser(name, index) {
  const anonymous = await request.newContext({
    baseURL: base,
    extraHTTPHeaders: { "X-Requested-With": "XMLHttpRequest" },
  });
  clients.set(name, anonymous);
  await api(name, "POST", "/api/user", { name, password });
  if (name === "moderator")
    docker(
      "exec",
      `${tag}-db`,
      "psql",
      "-U",
      "gallery",
      "-d",
      "gallery",
      "-c",
      "UPDATE users SET role='ADMIN' WHERE username='moderator'",
    );
  docker(
    "exec",
    `${tag}-db`,
    "psql",
    "-U",
    "gallery",
    "-d",
    "gallery",
    "-c",
    `UPDATE users SET balance=10000 WHERE username='${name}'`,
  );
  const response = await anonymous.get("/api/user/me", {
    headers: {
      Authorization: `Basic ${Buffer.from(`${name}:${password}`).toString("base64")}`,
    },
  });
  expect(response.status()).toBe(200);
  await api(name, "PATCH", "/api/user/me", {
    age: 23 + index,
    city: ["Казань", "Санкт-Петербург", "Москва"][index % 3],
    about:
      name === "moderator"
        ? "Помогаю участникам разобраться в спорных заказах."
        : name === "maria"
          ? "Игровые предметы и настройка цифровых сервисов. На связи вечером."
          : "Покупаю игровые предметы и пользуюсь цифровыми сервисами.",
  });
  await api(
    name,
    "PUT",
    "/api/user/me/avatar",
    undefined,
    await art(`avatar-${name}`, avatar(index), { width: 240, height: 240 }),
  );
}

async function pageFor(username) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: "reduce",
    locale: "ru-RU",
  });
  contexts.push(context);
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(base);
  await expect(page.locator(".game-card").first()).toBeVisible();
  if (username) {
    await page
      .getByRole("button", { name: "Войти", exact: true })
      .first()
      .click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Имя пользователя", { exact: true }).fill(username);
    await dialog.getByLabel("Пароль", { exact: true }).fill(password);
    await dialog.getByRole("button", { name: "Войти", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator(".profile .username")).toHaveText(username);
  }
  return page;
}

async function nav(page, label) {
  if (!(await page.locator(".sidebar").isVisible()))
    await page
      .getByRole("button", { name: "Открыть меню", exact: true })
      .click();
  await page
    .locator(".sidebar")
    .getByRole("button", { name: new RegExp(`^${label}`) })
    .click();
}

async function category(page, name) {
  await nav(page, "Маркет");
  await page
    .getByRole("button", { name: `Открыть категорию ${name}`, exact: true })
    .click();
  await expect(page.locator(".skeleton-card")).toHaveCount(0);
}

async function ordersPage(page) {
  await nav(page, "Мои заказы");
  await expect(page.locator(".orders-loading")).toHaveCount(0);
}

async function openOrder(page, id, admin = false, connected = true) {
  if (admin) {
    await nav(page, "Управление");
    const trades = page.getByRole("region", {
      name: "Заказы и чаты площадки",
      exact: true,
    });
    await trades.getByLabel("Поиск заказов по названию").fill(String(id));
    const card = trades
      .locator(".admin-order-card")
      .filter({ hasText: new RegExp(`Заказ #${id} ·`) });
    await card
      .getByRole("button", { name: "Открыть заказ", exact: true })
      .click();
  } else {
    await ordersPage(page);
    await page
      .locator(".order-row")
      .filter({ hasText: new RegExp(`Заказ #${id} ·`) })
      .click();
  }
  await expect(
    page.getByRole("heading", { name: `Заказ #${id}.`, exact: true }),
  ).toBeVisible();
  await expect(page.locator(".order-chat-header")).toContainText(
    connected ? "Подключён" : "Автообновление",
  );
}

async function capture(page, name, caption, group) {
  await page.waitForLoadState("networkidle");
  await expect(page.locator("fieldset[disabled]")).toHaveCount(0);
  await expect(page.locator(".skeleton-card")).toHaveCount(0);
  const toast = page.getByRole("button", {
    name: "Закрыть уведомление",
    exact: true,
  });
  if ((await toast.count()) && !(await page.getByRole("dialog").count()))
    await toast.click();
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(
      [...document.images].map(async (img) => {
        img.loading = "eager";
        try {
          await img.decode();
        } catch {}
      }),
    );
  });
  await page.screenshot({
    path: resolve(out, `${name}.png`),
    fullPage: true,
    animations: "disabled",
  });
  shots.push({ name: `${name}.png`, caption, group });
  log(`${shots.length}. ${caption}`);
}

async function adminAction(page, label) {
  await page
    .getByRole("region", { name: "Админ-функции заказа", exact: true })
    .getByRole("button", { name: label, exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: label, exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

async function send(user, order, text) {
  return api(user, "POST", `/api/orders/${order.id}/messages`, {
    text,
    clientMessageId: crypto.randomUUID(),
  });
}

async function purchase(user, advice) {
  return api(user, "POST", "/api/orders/", { id: advice.id });
}

async function seed() {
  for (const [index, user] of [
    "moderator",
    "alex",
    "maria",
    "nikita",
    "elena",
    "roman",
    "pavel",
  ].entries())
    await createUser(user, index);
  const games = {};
  for (const [key, name] of [
    ["cs", "Counter-Strike 2"],
    ["dota", "Dota 2"],
    ["craft", "Minecraft"],
    ["gpt", "ChatGPT"],
    ["spotify", "Spotify"],
    ["discord", "Discord"],
  ]) {
    games[key] = await api("moderator", "POST", "/api/games", { name });
    await api(
      "moderator",
      "PUT",
      `/api/games/${games[key].id}/image`,
      undefined,
      await art(key, cover(key)),
    );
  }
  const listings = {};
  for (const [key, user, game, name, price] of [
    ["rifle", "maria", "cs", "AK-47 · Красная линия", 1200],
    ["gpt", "maria", "gpt", "Настройка рабочего пространства ChatGPT", 800],
    ["spotify", "maria", "spotify", "Перенос и организация плейлистов", 450],
    ["craft", "maria", "craft", "Постройка уютного дома в Minecraft", 900],
    ["skin", "maria", "cs", "AWP · Древесная гадюка", 750],
    ["dota", "nikita", "dota", "Набор оформления для Juggernaut", 650],
    ["gpt2", "nikita", "gpt", "Промпты для учёбы и работы", 550],
    ["discord", "nikita", "discord", "Оформление Discord-сервера", 1000],
    ["cs2", "nikita", "cs", "M4A1-S · Ночной кошмар", 1100],
    ["fraud", "roman", "gpt", "Настройка помощника для рабочих задач", 1500],
    ["badbuyer", "elena", "spotify", "Настройка музыкальной библиотеки", 600],
    ["refusal", "elena", "discord", "Настройка ролей и каналов Discord", 700],
    ["admin", "moderator", "gpt", "Подборка промптов для новичков", 300],
  ]) {
    listings[key] = await api(user, "POST", "/api/advices", {
      name,
      game_id: games[game].id,
      price,
    });
    await api(
      user,
      "PUT",
      `/api/advices/${listings[key].id}/image`,
      undefined,
      await art(`listing-${key}`, cover(game, name.replaceAll("&", "&amp;"))),
    );
  }
  for (const [buyer, key, rating, text] of [
    [
      "elena",
      "spotify",
      5,
      "Плейлисты перенесены аккуратно, ничего не потерялось.",
    ],
    ["pavel", "gpt", 5, "Помогла настроить всё под рабочие задачи. Спасибо!"],
    [
      "alex",
      "craft",
      4,
      "Дом получился отличным. Потребовался ещё один вечер на детали.",
    ],
  ]) {
    const order = await purchase(buyer, listings[key]);
    await api(buyer, "PATCH", `/api/orders/${order.id}/confirm`);
    await api(buyer, "PATCH", `/api/orders/${order.id}/feedback`, {
      rating,
      feedbackText: text,
    });
    await api("maria", "PATCH", `/api/orders/${order.id}/response`, {
      responseText: "Спасибо за отзыв! Была рада помочь.",
    });
  }
  return { games, listings };
}

async function updateReadme() {
  const groups = [
    {
      title: "Каталог, профиль и управление",
      images: [
        ["market", "Каталог игр и сервисов"],
        ["listings", "Объявления"],
        ["profile", "Профиль продавца"],
        ["admin-chats", "Управление и поиск заказов"],
      ],
    },
    {
      title: "Заказ и чат",
      images: [
        ["order", "Заказ и переписка участников"],
        ["order-completed", "Завершённая сделка, отзыв и ответ"],
      ],
    },
    {
      title: "Спор с продавцом",
      description:
        "После жалобы покупателя администратор открывает закрытый заказ и возвращает деньги. Если продавец уже потратил оплату, его баланс может стать отрицательным.",
      images: [
        ["seller-dispute-reopened", "Заказ открыт для разбора"],
        ["seller-dispute-refund", "Возврат покупателю"],
      ],
    },
    {
      title: "Спор с покупателем",
      description:
        "Покупатель требует возврат после получения результата. Администратор проверяет переписку и повторно подтверждает выполнение в пользу продавца.",
      images: [
        ["buyer-dispute-claim", "Необоснованное требование возврата"],
        ["buyer-dispute-confirmed", "Решение в пользу продавца"],
      ],
    },
    {
      title: "Мобильная версия",
      images: [
        ["mobile-market", "Каталог"],
        ["mobile-order", "Заказ и чат"],
      ],
    },
  ];
  let gallery =
    "## Скриншоты\n\nСнято на тестовых аккаунтах с работающим бэкендом и PostgreSQL.\n";
  for (const group of groups) {
    gallery = `${gallery.trimEnd()}\n\n### ${group.title}\n\n`;
    if (group.description) gallery += `${group.description}\n\n`;
    const items = group.images.map(([name, caption]) => {
      const shot = shots.find((item) => item.name === `${name}.png`);
      if (!shot) throw new Error(`Нет скриншота для галереи: ${name}`);
      return { ...shot, caption };
    });
    for (let i = 0; i < items.length; i += 2) {
      const pair = items.slice(i, i + 2);
      const image = (s) =>
        `[![${s.caption}](docs/screenshots/${s.name})](docs/screenshots/${s.name})`;
      gallery += `| ${pair[0].caption} | ${pair[1]?.caption || ""} |\n| --- | --- |\n| ${image(pair[0])} | ${pair[1] ? image(pair[1]) : ""} |\n\n`;
    }
  }
  const path = resolve(root, "README.md");
  const original = await readFile(path, "utf8");
  await writeFile(
    path,
    original.replace(
      /## Скриншоты\n[\s\S]*?(?=\n## Стек)/,
      `${gallery.trimEnd()}\n`,
    ),
  );
  await writeFile(
    resolve(out, "smoke-results.json"),
    JSON.stringify(
      { screenshots: shots.length, checks, pageErrors: errors, images: shots },
      null,
      2,
    ) + "\n",
  );
}

try {
  await mkdir(out, { recursive: true });
  if (!process.env.GALLERY_SKIP_BUILD) {
    log("Сборка бэкенда для отдельного окружения");
    execFileSync("docker", ["build", "-t", "item-bro-gallery:local", "."], {
      cwd: root,
      stdio: "inherit",
    });
  }
  docker("network", "create", tag);
  docker(
    "run",
    "-d",
    "--name",
    `${tag}-db`,
    "--network",
    tag,
    "-e",
    "POSTGRES_USER=gallery",
    "-e",
    "POSTGRES_PASSWORD=gallery",
    "-e",
    "POSTGRES_DB=gallery",
    "postgres:17-alpine",
  );
  for (let i = 0; ; i++) {
    try {
      docker("exec", `${tag}-db`, "pg_isready", "-U", "gallery");
      break;
    } catch {
      if (i > 100) throw new Error("PostgreSQL не запустился");
      await pause(200);
    }
  }
  docker(
    "run",
    "-d",
    "--name",
    `${tag}-app`,
    "--network",
    tag,
    "-p",
    `127.0.0.1:${apiPort}:8080`,
    "-e",
    `SPRING_DATASOURCE_URL=jdbc:postgresql://${tag}-db:5432/gallery`,
    "-e",
    "SPRING_DATASOURCE_USERNAME=gallery",
    "-e",
    "SPRING_DATASOURCE_PASSWORD=gallery",
    "item-bro-gallery:local",
  );
  for (let i = 0; ; i++) {
    try {
      if ((await fetch(`http://127.0.0.1:${apiPort}/api/games`)).ok) break;
    } catch {}
    if (i > 200) throw new Error("Приложение не запустилось");
    await pause(200);
  }
  const proxy = Object.fromEntries(
    ["/api", "/csrf"].map((path) => [
      path,
      {
        target: `http://127.0.0.1:${apiPort}`,
        changeOrigin: true,
        ws: true,
        // Изолированный порт съёмки использует тот же разрешённый Origin, что основной фронтенд.
        configure(server) {
          server.on("proxyReqWs", (req) =>
            req.setHeader("origin", "http://localhost:5173"),
          );
        },
      },
    ]),
  );
  vite = await createServer({
    configFile: false,
    root: frontend,
    plugins: [react()],
    server: { host: "127.0.0.1", port, strictPort: true, proxy },
  });
  await vite.listen();
  browser = await chromium.launch();
  const { games, listings } = await seed();
  log("Данные созданы, начинаю сценарии");

  const guest = await pageFor();
  const buyer = await pageFor("alex");
  const seller = await pageFor("maria");
  const admin = await pageFor("moderator");
  const catalogGroup = "Каталог игр и сервисов";
  await capture(
    guest,
    "market",
    "Маркет: игры и цифровые сервисы",
    catalogGroup,
  );
  await guest
    .getByLabel("Поиск по каталогу", { exact: true })
    .fill("несуществующая категория");
  await expect(
    guest.getByText("Категория не найдена", { exact: true }),
  ).toBeVisible();
  await capture(guest, "catalog-empty", "Поиск без результатов", catalogGroup);
  await guest.getByLabel("Поиск по каталогу", { exact: true }).fill("");
  await category(guest, games.cs.name);
  await capture(
    guest,
    "listings",
    "Объявления игры: изображения, цены и оценки",
    catalogGroup,
  );
  await category(guest, "ChatGPT");
  await capture(
    guest,
    "service-listings",
    "Объявления ChatGPT и красный ник администратора",
    catalogGroup,
  );
  await guest
    .locator(".listing-card")
    .filter({ hasText: listings.gpt.name })
    .getByRole("button", { name: "Купить", exact: true })
    .click();
  await expect(
    guest
      .getByRole("dialog")
      .getByRole("heading", { name: "Вход", exact: true }),
  ).toBeVisible();
  await capture(
    guest,
    "guest-purchase",
    "Покупка требует авторизации",
    "Вход и регистрация",
  );
  await guest.keyboard.press("Escape");
  await nav(guest, "Мои заказы");
  await capture(
    guest,
    "guest-orders",
    "Личный раздел недоступен без входа",
    "Вход и регистрация",
  );
  await guest
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  await capture(guest, "login", "Форма входа", "Вход и регистрация");
  let dialog = guest.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("alex");
  await dialog.getByLabel("Пароль", { exact: true }).fill("Wrong123!");
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog.getByRole("alert")).toBeVisible();
  await capture(
    guest,
    "login-error",
    "Неверные данные входа",
    "Вход и регистрация",
  );
  await dialog
    .getByRole("button", { name: "Зарегистрироваться", exact: true })
    .click();
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("sofia");
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await capture(
    guest,
    "register",
    "Регистрация нового пользователя",
    "Вход и регистрация",
  );
  await dialog
    .getByRole("button", { name: "Создать аккаунт", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await guest.reload();
  await expect(guest.locator(".profile .username")).toHaveText("sofia");
  checks.push("Регистрация и восстановление сессии после F5");
  await capture(
    guest,
    "session-restored",
    "Сессия сохранена после перезагрузки страницы",
    "Вход и регистрация",
  );
  await ordersPage(guest);
  await expect(guest.getByText("Заказов нет", { exact: true })).toBeVisible();
  await capture(
    guest,
    "orders-empty",
    "У нового пользователя пока нет заказов",
    "Вход и регистрация",
  );
  await category(guest, games.cs.name);
  await guest
    .locator(".listing-card")
    .filter({ hasText: listings.rifle.name })
    .getByRole("button", { name: "Купить", exact: true })
    .click();
  await guest
    .getByRole("dialog")
    .getByRole("button", { name: "Создать заказ", exact: true })
    .click();
  await expect(guest.getByRole("dialog").getByRole("alert")).toContainText(
    "Недостаточно",
  );
  await capture(
    guest,
    "insufficient-balance",
    "Покупка отклонена: недостаточно средств",
    "Ошибки и ограничения",
  );
  await guest.keyboard.press("Escape");

  const profileGroup = "Профили и изображения";
  await guest.locator(".profile").click();
  dialog = guest.getByRole("dialog");
  await dialog.getByLabel("Возраст", { exact: true }).fill("24");
  await dialog.getByLabel("Город", { exact: true }).fill("Казань");
  await dialog
    .getByLabel("О себе", { exact: true })
    .fill("Люблю игровые миры и удобные цифровые сервисы.");
  await dialog
    .getByRole("button", { name: "Сохранить профиль", exact: true })
    .click();
  await expect(
    dialog.getByText("Профиль сохранён.", { exact: true }),
  ).toBeVisible();
  await dialog
    .locator('input[type="file"]')
    .setInputFiles(
      await art("avatar-sofia", avatar(3), { width: 240, height: 240 }),
    );
  await dialog
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(dialog.locator(".image-editor-preview")).toHaveCount(1);
  await capture(
    guest,
    "profile-settings",
    "Возраст, город, описание и установка аватарки",
    profileGroup,
  );
  await dialog.locator('input[type="file"]').setInputFiles({
    name: "document.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("not an image"),
  });
  await expect(dialog.getByRole("alert")).toContainText("Выбери JPEG");
  await capture(
    guest,
    "invalid-image",
    "Неподдерживаемый формат изображения",
    "Ошибки и ограничения",
  );
  await dialog
    .getByRole("button", { name: "Удалить изображение", exact: true })
    .click();
  await expect(dialog.locator(".image-editor-preview")).toHaveCount(0);
  await capture(guest, "avatar-removed", "Удаление аватарки", profileGroup);
  await guest.keyboard.press("Escape");
  await guest.goto(`${base}/#/users/maria`);
  await expect(
    guest.getByRole("region", { name: "Профиль maria", exact: true }),
  ).toBeVisible();
  await capture(
    guest,
    "profile",
    "Профиль продавца с активными объявлениями",
    profileGroup,
  );
  await guest.getByRole("button", { name: /^Отзывы/ }).click();
  await expect(guest.locator(".profile-review").first()).toBeVisible();
  await capture(
    guest,
    "profile-reviews",
    "Отзывы покупателей и ответы продавца",
    profileGroup,
  );
  await guest.goto(`${base}/#/users/moderator`);
  await expect(guest.locator(".profile-role")).toHaveText("Администратор");
  await capture(
    guest,
    "admin-profile",
    "Публичный профиль администратора",
    profileGroup,
  );

  const listingGroup = "Публикация и изображения объявлений";
  const publisher = await pageFor("nikita");
  await publisher
    .getByRole("button", { name: "Разместить объявление", exact: true })
    .first()
    .click();
  dialog = publisher.getByRole("dialog");
  await dialog
    .locator('select[name="game"]')
    .selectOption(String(games.discord.id));
  await dialog
    .getByLabel("Название объявления", { exact: true })
    .fill("Подборка эмодзи для Discord");
  await dialog.getByLabel("Цена, ₽", { exact: true }).fill("350");
  await capture(
    publisher,
    "listing-create",
    "Публикация объявления в категории сервиса",
    listingGroup,
  );
  await dialog.getByLabel("Цена, ₽", { exact: true }).fill("0");
  await dialog
    .getByRole("button", { name: "Опубликовать объявление", exact: true })
    .click();
  expect(
    await dialog
      .locator('input[name="price"]')
      .evaluate((el) => el.validity.rangeUnderflow),
  ).toBe(true);
  await capture(
    publisher,
    "invalid-price",
    "Цена ниже допустимой: форма не отправляется",
    "Ошибки и ограничения",
  );
  await dialog.getByLabel("Цена, ₽", { exact: true }).fill("350");
  await dialog.getByLabel("Название объявления", { exact: true }).fill("   ");
  await dialog
    .getByRole("button", { name: "Опубликовать объявление", exact: true })
    .click();
  await expect(dialog.getByRole("alert")).toBeVisible();
  await capture(
    publisher,
    "invalid-listing-name",
    "Название из пробелов отклоняется",
    "Ошибки и ограничения",
  );
  await dialog
    .getByLabel("Название объявления", { exact: true })
    .fill("Подборка эмодзи для Discord");
  await dialog
    .getByRole("button", { name: "Опубликовать объявление", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  const createdName = "Подборка эмодзи для Discord";
  await publisher
    .getByRole("button", {
      name: `Открыть объявление ${createdName}`,
      exact: true,
    })
    .click();
  dialog = publisher.getByRole("dialog");
  await dialog
    .locator('input[type="file"]')
    .setInputFiles(resolve(temp, "discord.png"));
  await dialog
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(
    dialog.getByRole("button", { name: "Удалить изображение", exact: true }),
  ).toBeVisible();
  await capture(
    publisher,
    "listing-image",
    "Установка изображения объявления",
    listingGroup,
  );
  await dialog
    .getByRole("button", { name: "Удалить изображение", exact: true })
    .click();
  await expect(
    dialog.getByRole("button", { name: "Удалить изображение", exact: true }),
  ).toHaveCount(0);
  await capture(
    publisher,
    "listing-image-removed",
    "Удаление изображения с сохранением объявления",
    listingGroup,
  );
  await publisher.keyboard.press("Escape");
  await nav(publisher, "Мои объявления");
  await publisher
    .getByRole("button", { name: "Открыть категорию Discord", exact: true })
    .click();
  await capture(
    publisher,
    "my-listings",
    "Личные объявления продавца",
    listingGroup,
  );
  await publisher
    .getByRole("button", { name: "Разместить объявление", exact: true })
    .first()
    .click();
  dialog = publisher.getByRole("dialog");
  await dialog
    .getByLabel("Название объявления", { exact: true })
    .fill("Шестое активное объявление");
  await dialog.getByLabel("Цена, ₽", { exact: true }).fill("100");
  await dialog
    .getByRole("button", { name: "Опубликовать объявление", exact: true })
    .click();
  await expect(dialog.getByRole("alert")).toContainText(/пяти|5|лимит/i);
  await capture(
    publisher,
    "listing-limit",
    "Ограничение: не больше пяти активных объявлений",
    "Ошибки и ограничения",
  );
  await publisher.keyboard.press("Escape");
  await publisher
    .locator(".listing-card")
    .filter({ hasText: createdName })
    .getByRole("button", { name: "В архив", exact: true })
    .click();
  await capture(
    publisher,
    "listing-archive",
    "Архивирование собственного объявления",
    listingGroup,
  );
  await publisher
    .getByRole("dialog")
    .getByRole("button", { name: "Архивировать", exact: true })
    .click();
  await expect(
    publisher.locator(".listing-card").filter({ hasText: createdName }),
  ).toHaveCount(0);
  checks.push(
    "Публикация, установка и удаление изображения, лимит пяти объявлений, архивирование",
  );

  const dealGroup = "Покупка, продажа и успешная сделка";
  await category(buyer, games.cs.name);
  await buyer
    .locator(".listing-card")
    .filter({ hasText: listings.rifle.name })
    .getByRole("button", { name: "Купить", exact: true })
    .click();
  await capture(
    buyer,
    "purchase",
    "Покупка объявления с внутреннего баланса",
    dealGroup,
  );
  const createdResponse = buyer.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname === "/api/orders/",
  );
  await buyer
    .getByRole("dialog")
    .getByRole("button", { name: "Создать заказ", exact: true })
    .click();
  const deal = await (await createdResponse).json();
  await expect(buyer.locator(".order-chat-header")).toContainText("Подключён");
  await openOrder(seller, deal.id);
  await send("alex", deal, "Здравствуйте! Готов принять предмет, я на связи.");
  await send(
    "maria",
    deal,
    "Здравствуйте! Предмет передан, проверьте инвентарь.",
  );
  await expect(buyer.getByRole("log")).toContainText("проверьте инвентарь");
  await capture(
    buyer,
    "order",
    "Заказ покупателя: живой чат и подтверждение получения",
    dealGroup,
  );
  await capture(
    seller,
    "order-sale",
    "Тот же заказ у продавца с пометкой «Продажа»",
    dealGroup,
  );
  await ordersPage(buyer);
  await capture(buyer, "my-purchases", "Личный список покупок", dealGroup);
  await ordersPage(seller);
  await capture(seller, "my-sales", "Личный список продаж", dealGroup);
  await openOrder(buyer, deal.id);
  await buyer
    .getByRole("button", { name: "Подтвердить получение", exact: true })
    .click();
  await expect(buyer.locator(".order-status")).toHaveText("Закрыт");
  await openOrder(seller, deal.id);
  await expect(seller.locator(".order-status")).toHaveText("Закрыт");
  await buyer
    .locator('textarea[name="text"]')
    .fill("Предмет получен, всё совпало с описанием. Спасибо!");
  await buyer
    .getByRole("button", { name: "Сохранить отзыв", exact: true })
    .click();
  await expect(seller.locator(".review-block")).toContainText(
    "Предмет получен",
  );
  await seller
    .locator('textarea[name="text"]')
    .fill("Спасибо за покупку! Приятной игры.");
  await seller
    .getByRole("button", { name: "Сохранить ответ", exact: true })
    .click();
  await expect(buyer.locator(".review-block")).toContainText("Приятной игры");
  await capture(
    buyer,
    "order-completed",
    "Закрытая сделка, отзыв и ответ продавца",
    dealGroup,
  );
  await buyer
    .locator('textarea[name="text"]')
    .fill("Дополнение: всё работает и на следующий день. Рекомендую!");
  await buyer
    .getByRole("button", { name: "Сохранить отзыв", exact: true })
    .click();
  await expect(buyer.getByRole("log")).toContainText("Обновлен отзыв");
  await seller
    .locator('textarea[name="text"]')
    .fill("Благодарю за дополнение. Обращайтесь!");
  await seller
    .getByRole("button", { name: "Сохранить ответ", exact: true })
    .click();
  await expect(buyer.getByRole("log")).toContainText("Ответ на отзыв обновлен");
  await capture(
    buyer,
    "feedback-updated",
    "Редактирование отзыва и ответа отражается в истории",
    dealGroup,
  );
  await send(
    "alex",
    deal,
    "Можно написать вам после закрытия, если возникнут вопросы?",
  );
  await send("maria", deal, "Конечно, чат остаётся доступным.");
  await expect(buyer.getByRole("log")).toContainText("чат остаётся доступным");
  await capture(
    buyer,
    "chat-after-completion",
    "Переписка продолжается после завершения сделки",
    dealGroup,
  );

  const cancelled = await purchase("alex", listings.spotify);
  await send(
    "maria",
    cancelled,
    "Сегодня не успею выполнить заказ. Верну оплату.",
  );
  await openOrder(seller, cancelled.id);
  await capture(
    seller,
    "seller-cancel",
    "Продавец может отменить открытый заказ",
    "Отмена заказа продавцом",
  );
  await seller
    .getByRole("button", { name: "Отменить заказ", exact: true })
    .click();
  await expect(seller.locator(".order-status")).toHaveText("Отменён");
  await openOrder(buyer, cancelled.id);
  await send("alex", cancelled, "Возврат получен. Спасибо, что предупредили.");
  await capture(
    buyer,
    "seller-cancelled",
    "Деньги возвращены, чат доступен и после отмены",
    "Отмена заказа продавцом",
  );

  const fraudGroup = "Спор: продавец не выполнил заказ";
  const fraud = await purchase("alex", listings.fraud);
  await send(
    "roman",
    fraud,
    "Настройка готова. Подтвердите получение, пожалуйста.",
  );
  await send("alex", fraud, "Подтверждаю. Сейчас проверю на рабочих задачах.");
  await api("alex", "PATCH", `/api/orders/${fraud.id}/confirm`);
  await send(
    "alex",
    fraud,
    "Проверил: сценарии не запускаются, результата нет. Нужен возврат.",
  );
  await send("roman", fraud, "Исправлять не буду. Заказ вы уже подтвердили.");
  await openOrder(admin, fraud.id, true);
  await capture(
    admin,
    "seller-dispute-closed",
    "Закрытый заказ: жалоба покупателя и переписка сторон",
    fraudGroup,
  );
  await adminAction(admin, "Открыть заказ");
  await expect(admin.locator(".order-status")).toHaveText("Открыт");
  await capture(
    admin,
    "seller-dispute-reopened",
    "Администратор открывает заказ для разбора",
    fraudGroup,
  );
  // Продавец тратит начисление на другие покупки до повторного открытия.
  // Здесь воспроизводим этот вариант отдельным заказом ниже.
  await send(
    "moderator",
    fraud,
    "Результат не предоставлен. Возвращаю оплату покупателю.",
  );
  await adminAction(admin, "Вернуть деньги");
  await expect(admin.locator(".order-status")).toHaveText("Отменён");
  await capture(
    admin,
    "seller-dispute-refund",
    "Решение администратора: возврат покупателю",
    fraudGroup,
  );
  await openOrder(buyer, fraud.id);
  await buyer
    .locator('textarea[name="text"]')
    .fill("Услуга не выполнена. Оплату вернул администратор.");
  await buyer.locator('select[name="rating"]').selectOption("1");
  await buyer
    .getByRole("button", { name: "Сохранить отзыв", exact: true })
    .click();
  await capture(
    buyer,
    "seller-dispute-buyer",
    "Итог у покупателя: возврат, отзыв и история решения",
    fraudGroup,
  );

  const spent = await purchase("alex", listings.fraud);
  await api("alex", "PATCH", `/api/orders/${spent.id}/confirm`);
  const romanBalance = await api("roman", "GET", "/api/user/me");
  const spending = await api("moderator", "POST", "/api/advices", {
    name: "Комплект оформления рабочего пространства",
    game_id: games.gpt.id,
    price: romanBalance.balance,
  });
  await purchase("roman", spending);
  await openOrder(admin, spent.id, true);
  await adminAction(admin, "Открыть заказ");
  await send(
    "moderator",
    spent,
    "Средства возвращаются покупателю, даже если продавец уже потратил начисление.",
  );
  await adminAction(admin, "Вернуть деньги");
  const romanPage = await pageFor("roman");
  await ordersPage(romanPage);
  await expect(romanPage.locator(".balance"))
    .toContainText("−")
    .catch(async () =>
      expect(romanPage.locator(".balance")).toContainText("-"),
    );
  await capture(
    romanPage,
    "seller-negative-balance",
    "Продавец потратил оплату: возврат прошёл, баланс отрицательный",
    fraudGroup,
  );

  const buyerDispute = "Спор: покупатель требует необоснованный возврат";
  const badBuyer = await purchase("pavel", listings.badbuyer);
  await send(
    "elena",
    badBuyer,
    "Библиотека настроена. Все плейлисты перенесены.",
  );
  await send("pavel", badBuyer, "Проверил, всё на месте и работает. Спасибо!");
  await api("pavel", "PATCH", `/api/orders/${badBuyer.id}/confirm`);
  await send(
    "pavel",
    badBuyer,
    "Хочу вернуть оплату. Будем считать, что ничего не получил.",
  );
  await send(
    "elena",
    badBuyer,
    "Вы выше подтвердили получение. Прошу администратора посмотреть переписку.",
  );
  await openOrder(admin, badBuyer.id, true);
  await capture(
    admin,
    "buyer-dispute-claim",
    "Жалоба покупателя противоречит подтверждению в переписке",
    buyerDispute,
  );
  await adminAction(admin, "Открыть заказ");
  await send(
    "moderator",
    badBuyer,
    "Передача результата подтверждена участниками. Оснований для возврата нет.",
  );
  await capture(
    admin,
    "buyer-dispute-review",
    "Администратор проверяет историю открытого заказа",
    buyerDispute,
  );
  await adminAction(admin, "Подтвердить заказ");
  await expect(admin.locator(".order-status")).toHaveText("Закрыт");
  await capture(
    admin,
    "buyer-dispute-confirmed",
    "Заказ подтверждён повторно, начисление продавцу восстановлено",
    buyerDispute,
  );

  const refusal = await purchase("pavel", listings.refusal);
  await send(
    "elena",
    refusal,
    "Роли и каналы готовы. Проверьте доступы, пожалуйста.",
  );
  await send(
    "pavel",
    refusal,
    "Все работает. Но подтверждать заказ пока не буду.",
  );
  await send(
    "elena",
    refusal,
    "Работа выполнена. Прошу администратора завершить заказ.",
  );
  await openOrder(admin, refusal.id, true);
  await capture(
    admin,
    "buyer-refuses-confirmation",
    "Покупатель получил результат, но не подтверждает заказ",
    "Спор: покупатель отказывается подтверждать выполнение",
  );
  await send(
    "moderator",
    refusal,
    "Выполнение и получение подтверждены перепиской. Подтверждаю заказ.",
  );
  await adminAction(admin, "Подтвердить заказ");
  await expect(admin.locator(".order-status")).toHaveText("Закрыт");
  await capture(
    admin,
    "admin-confirms-delivery",
    "Администратор подтверждает выполненный заказ",
    "Спор: покупатель отказывается подтверждать выполнение",
  );
  checks.push(
    "Споры: CLOSED→OPENED→REFUND, CLOSED→OPENED→CLOSED и OPENED→CLOSED; отрицательный баланс продавца",
  );

  const adminGroup = "Администрирование каталога и заказов";
  await nav(admin, "Управление");
  await capture(
    admin,
    "admin-orders",
    "Все заказы площадки и участники сделок",
    adminGroup,
  );
  const trades = admin.getByRole("region", {
    name: "Заказы и чаты площадки",
    exact: true,
  });
  await trades.getByRole("button", { name: "Все чаты", exact: true }).click();
  await trades.getByLabel("Поиск заказов по названию").fill("помощника");
  await capture(
    admin,
    "admin-chats",
    "Поиск по заказам и доступ администратора к чатам",
    adminGroup,
  );
  await category(admin, games.cs.name);
  await admin
    .getByRole("button", {
      name: `Открыть объявление ${listings.cs2.name}`,
      exact: true,
    })
    .click();
  await capture(
    admin,
    "admin-listing",
    "Админ-функции внутри чужого объявления",
    adminGroup,
  );
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: "Удалить объявление", exact: true })
    .click();
  await capture(
    admin,
    "admin-listing-archive",
    "Подтверждение архивирования чужого объявления",
    adminGroup,
  );
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: "Архивировать", exact: true })
    .click();
  await expect(
    admin.locator(".listing-card").filter({ hasText: listings.cs2.name }),
  ).toHaveCount(0);
  await nav(admin, "Управление");
  await admin
    .getByRole("button", { name: "Создать категорию", exact: true })
    .click();
  await admin
    .getByRole("dialog")
    .getByLabel("Название категории", { exact: true })
    .fill("Roblox");
  await capture(
    admin,
    "category-create",
    "Добавление новой категории администратором",
    adminGroup,
  );
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: "Создать категорию", exact: true })
    .click();
  await expect(admin.getByRole("dialog")).toHaveCount(0);
  const roblox = (await api("moderator", "GET", "/api/games")).find(
    (game) => game.name === "Roblox",
  );
  await admin
    .getByRole("button", { name: "Изображение категории Roblox", exact: true })
    .click();
  dialog = admin.getByRole("dialog");
  await dialog
    .locator('input[type="file"]')
    .setInputFiles(resolve(temp, "craft.png"));
  await dialog
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(
    dialog.getByRole("button", { name: "Удалить изображение", exact: true }),
  ).toBeVisible();
  await capture(
    admin,
    "category-image",
    "Установка изображения категории",
    adminGroup,
  );
  await dialog
    .getByRole("button", { name: "Удалить изображение", exact: true })
    .click();
  await expect(
    dialog.getByRole("button", { name: "Удалить изображение", exact: true }),
  ).toHaveCount(0);
  await capture(
    admin,
    "category-image-removed",
    "Удаление изображения категории",
    adminGroup,
  );
  await admin.keyboard.press("Escape");
  const archivedAdvice = await api("nikita", "POST", "/api/advices", {
    name: "Оформление игрового мира Roblox",
    game_id: roblox.id,
    price: 400,
  });
  const archivedOrder = await purchase("alex", archivedAdvice);
  await admin
    .getByRole("button", { name: "Архивировать категорию Roblox", exact: true })
    .click();
  await capture(
    admin,
    "category-archive",
    "Архивирование категории",
    adminGroup,
  );
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: "Архивировать категорию", exact: true })
    .click();
  await expect(
    admin.getByRole("button", {
      name: "Архивировать категорию Roblox",
      exact: true,
    }),
  ).toHaveCount(0);
  await openOrder(buyer, archivedOrder.id);
  await capture(
    buyer,
    "archived-category-order",
    "Заказ доступен после архивирования категории",
    adminGroup,
  );

  const chatGroup = "История чата и восстановление соединения";
  const longOrder = await purchase("alex", listings.discord);
  const questions = [
    "Какие роли нужны?",
    "Нужен отдельный канал для новостей?",
    "Кто сможет писать в общий чат?",
    "Добавим приветственное сообщение?",
    "Какие цвета выбрать?",
    "Нужен голосовой канал?",
    "Как назвать раздел с правилами?",
    "Оставить доступ гостям?",
    "Настроить уведомления?",
    "Какие эмодзи использовать?",
    "Создать канал для поддержки?",
    "Добавить раздел с полезными ссылками?",
    "Как назвать роль участников?",
    "Проверить права модератора?",
    "Ограничить отправку ссылок?",
    "Добавить отдельный канал для вопросов?",
    "Какое описание сервера поставить?",
    "Проверить вид с телефона?",
    "Оставить отдельную роль для новичков?",
    "Сделать канал с расписанием?",
    "Сохранить текущую иконку?",
    "Открыть архив для участников?",
    "Настроить часовой пояс?",
    "Создать список команд?",
    "Проверить приглашение?",
    "Ещё что-нибудь добавить?",
  ];
  for (const [i, text] of questions.entries()) {
    await send("nikita", longOrder, text);
    await send(
      "alex",
      longOrder,
      [
        "Да, добавьте, пожалуйста.",
        "Оставим стандартный вариант.",
        "Спасибо, этот вариант подходит.",
      ][i % 3],
    );
  }
  await openOrder(buyer, longOrder.id);
  await buyer.locator(".chat-messages").evaluate((el) => {
    el.scrollTop = 0;
  });
  await expect(
    buyer.getByRole("button", { name: "Ранние сообщения", exact: true }),
  ).toBeVisible();
  await capture(
    buyer,
    "chat-history",
    "История загружается страницами",
    chatGroup,
  );
  await buyer
    .getByRole("button", { name: "Ранние сообщения", exact: true })
    .click();
  await buyer.locator(".chat-messages").evaluate((el) => {
    el.scrollTop = 0;
  });
  await expect(buyer.getByRole("log")).toContainText("Заказ создан");
  await capture(
    buyer,
    "chat-history-older",
    "Догружены ранние сообщения и событие создания заказа",
    chatGroup,
  );
  const fallback = await pageFor("alex");
  let blockSocket = true;
  await fallback.routeWebSocket(/\/chat\/ws$/, (socket) => {
    if (blockSocket) socket.close();
    else socket.connectToServer();
  });
  await openOrder(fallback, deal.id, false, false);
  await send(
    "maria",
    deal,
    "Сообщение доставлено и при временном разрыве соединения.",
  );
  await expect(fallback.getByRole("log")).toContainText(
    "при временном разрыве",
    { timeout: 10000 },
  );
  await capture(
    fallback,
    "chat-rest-fallback",
    "При разрыве WS сообщения приходят через REST",
    chatGroup,
  );
  blockSocket = false;
  await expect(fallback.locator(".order-chat-header")).toContainText(
    "Подключён",
    { timeout: 20000 },
  );
  await capture(
    fallback,
    "chat-reconnected",
    "WebSocket восстановлен, история сохранена",
    chatGroup,
  );
  const ids = await fallback
    .locator(".chat-message, .chat-system-message")
    .count();
  const history = await api("alex", "GET", `/api/orders/${deal.id}/messages`);
  expect(ids).toBe(history.items.length);
  checks.push(
    "Пагинация истории, REST при обрыве WS, восстановление подключения без дублей",
  );

  const securityResponse = await guest.request.get(
    `${base}/api/orders/${deal.id}`,
  );
  expect(securityResponse.status()).toBe(403);
  expect(
    (
      await guest.request.get(`${base}/api/orders/${deal.id}/messages`)
    ).status(),
  ).toBe(403);
  const securityCsrf = await (await guest.request.get(`${base}/csrf`)).json();
  expect(
    (
      await guest.request.patch(`${base}/api/admin/orders/${deal.id}/refund`, {
        headers: { [securityCsrf.headerName]: securityCsrf.token },
      })
    ).status(),
  ).toBe(403);
  checks.push(
    "Чужой заказ и история недоступны пользователю; административный запрос отклонён",
  );
  await nav(guest, "Мои объявления");
  await guest
    .getByRole("button", { name: "Открыть категорию Spotify", exact: true })
    .click();
  await expect(
    guest.getByText("Объявлений нет", { exact: true }),
  ).toBeVisible();
  await capture(
    guest,
    "listings-empty",
    "Нет личных объявлений в выбранной категории",
    "Ошибки и ограничения",
  );
  const offline = await guest.context().newPage();
  await offline.route("**/api/games", (route) =>
    route.abort("connectionrefused"),
  );
  await offline.goto(base);
  await expect(
    offline.getByText("Не удалось загрузить каталог", { exact: true }),
  ).toBeVisible();
  await capture(
    offline,
    "api-unavailable",
    "Ошибка соединения с API и возможность повторить запрос",
    "Ошибки и ограничения",
  );

  const mobileGroup = "Мобильная версия";
  for (const [kind, name, caption] of [
    ["market", "mobile-market", "Мобильный маркет"],
    ["listings", "mobile-listings", "Мобильные объявления сервиса"],
    ["order", "mobile-order", "Мобильный заказ и чат"],
    ["profile", "mobile-profile", "Мобильный профиль и отзывы"],
  ]) {
    const page = await pageFor("alex");
    if (kind === "listings") await category(page, "ChatGPT");
    if (kind === "order") await openOrder(page, deal.id);
    if (kind === "profile") {
      await page.goto(`${base}/#/users/maria`);
      await page.getByRole("button", { name: /^Отзывы/ }).click();
    }
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await capture(page, name, caption, mobileGroup);
  }
  checks.push(
    "Мобильная вёрстка: маркет, объявления, заказ и профиль без горизонтальной прокрутки",
  );

  expect(errors).toEqual([]);
  await updateReadme();
  log(
    `Готово: ${shots.length} скриншотов; ${checks.length} проверок. README обновлён.`,
  );
} catch (error) {
  console.error(error);
  if (browser) {
    for (const [i, page] of browser
      .contexts()
      .flatMap((ctx) => ctx.pages())
      .entries())
      try {
        await page.screenshot({
          path: resolve(temp, `failure-${i}.png`),
          fullPage: true,
        });
      } catch {}
  }
  log(`Диагностика: ${temp}`);
  try {
    console.error(docker("logs", "--tail", "35", `${tag}-app`));
  } catch {}
  process.exitCode = 1;
} finally {
  for (const context of contexts) await context.close().catch(() => {});
  await browser?.close();
  for (const client of clients.values()) await client.dispose();
  await vite?.close();
  for (const suffix of ["app", "db"])
    try {
      docker("rm", "-f", `${tag}-${suffix}`);
    } catch {}
  try {
    docker("network", "rm", tag);
  } catch {}
}
