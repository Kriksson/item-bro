import { test, expect, type Page, type WebSocketRoute } from "@playwright/test";
import type { ChatMessage } from "../src/types";

// HTTP-ответы подменяются только в тестах. В приложении нет локального сервера данных.
const game = { id: 7, name: "Arena" };
const sword = {
  id: 31,
  name: "Silver Sword",
  game,
  userSeller: { username: "seller" },
  price: 25,
};
const shield = {
  id: 32,
  name: "Traveler Shield",
  game,
  userSeller: { username: "seller" },
  price: 40,
};
const password = "Test12345!";

// External fonts do not participate in these API/UI checks.
test.beforeEach(async ({ page }) => {
  await page.route(/^https:\/\/fonts\.(?:googleapis|gstatic)\.com\//, (route) =>
    route.abort(),
  );
});

async function server(
  page: Page,
  roles: Record<string, string> = { admin: "ADMIN" },
  onSocket?: (ws: WebSocketRoute) => void,
) {
  const mutations: {
    method: string;
    path: string;
    body: unknown;
    authorization?: string;
    csrf?: string;
  }[] = [];
  let listings = [sword, shield].map((advice) => ({
    ...advice,
    userSeller: { ...advice.userSeller, userRole: roles.seller ?? "USER" },
  }));
  let games = [game];
  let orderCreated = false;
  const fields: Record<
    string,
    {
      age?: number | null;
      city?: string | null;
      about?: string | null;
      avatarUrl?: string | null;
    }
  > = {};
  const profile = (name: string) => ({
    name,
    userRole: roles[name] ?? "USER",
    balance: 100,
    ...fields[name],
  });
  const messages: ChatMessage[] = [];
  const requests = new Map<string, ChatMessage>();
  const sockets = new Map<WebSocketRoute, number>();
  await page.routeWebSocket(/\/api\/orders\/\d+\/chat\/ws/, (ws) => {
    sockets.set(ws, Number(new URL(ws.url()).pathname.split("/")[3]));
    ws.onClose(() => {
      sockets.delete(ws);
    });
    onSocket?.(ws);
  });
  let order = {
    id: 73,
    adviceDTO: listings[0],
    userBuyerDTO: { username: "buyer", userRole: roles.buyer ?? "USER" },
    price: 25,
    rating: -1,
    feedbackText: null as string | null,
    sellerResponseText: null as string | null,
    orderStatus: "OPENED" as "OPENED" | "CLOSED" | "REFUND",
  };
  const adminOrder = () => ({
    ...order,
    id: 74,
    adviceDTO: {
      ...order.adviceDTO,
      name: "Ancient Ring",
      userSeller: { username: "mira", userRole: "USER" },
    },
    userBuyerDTO: { username: "alex", userRole: "USER" },
  });
  const sessions = new Map<string, string>();
  let nextSession = 1;
  await page.route(/\/(api\/|csrf(?:\?|$))/, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const headers = request.headers();
    const authorization = headers.authorization;
    const sessionId = /(?:^|; )test-session=([^;]+)/.exec(
      headers.cookie ?? "",
    )?.[1];
    const username = authorization
      ? atob(authorization.slice("Basic ".length)).split(":")[0]
      : sessions.get(sessionId ?? "");
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, json: body });
    const empty = () => route.fulfill({ status: 204 });
    if (path.startsWith("/api/media/"))
      return route.fulfill({
        contentType: "image/svg+xml",
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#d72f42"/></svg>',
      });
    const publicUserPath =
      /^\/api\/users\/([^/]+)(?:\/(advices|reviews))?$/.exec(path);
    if (publicUserPath && method === "GET") {
      const name = decodeURIComponent(publicUserPath[1]);
      if (!["buyer", "seller", "admin", "alex", "mira"].includes(name))
        return json(404, { message: "User not found" });
      if (publicUserPath[2] === "advices")
        return json(
          200,
          listings.filter((advice) => advice.userSeller.username === name),
        );
      if (publicUserPath[2] === "reviews")
        return json(
          200,
          order.feedbackText && name === order.adviceDTO.userSeller.username
            ? [
                {
                  orderId: order.id,
                  adviceName: order.adviceDTO.name,
                  userBuyer: order.userBuyerDTO,
                  rating: order.rating,
                  feedbackText: order.feedbackText,
                  sellerResponseText: order.sellerResponseText,
                },
              ]
            : [],
        );
      return json(200, {
        username: name,
        userRole: roles[name] ?? "USER",
        ...fields[name],
      });
    }
    if (path === "/api/games" && method === "GET") return json(200, games);
    if (path === "/api/advices" && method === "GET")
      return json(200, {
        content: listings,
        page: 0,
        size: 50,
        totalElements: listings.length,
        totalPages: 1,
      });
    if (path === "/csrf")
      return json(200, { headerName: "X-CSRF-TOKEN", token: "test-token" });
    if (path === "/api/user/me" && method === "GET") {
      if (!username) return json(401, { message: "Unauthorized" });
      const person = profile(username);
      if (authorization) {
        const token = `session-${nextSession++}`;
        sessions.set(token, username);
        return route.fulfill({
          status: 200,
          json: person,
          headers: {
            "Set-Cookie": `test-session=${token}; Path=/; HttpOnly; SameSite=Lax`,
          },
        });
      }
      return json(200, person);
    }
    const chatPath = /^\/api\/orders\/(\d+)\/messages$/.exec(path);
    if (chatPath && method === "GET") {
      if (!username) return json(401, { message: "Unauthorized" });
      const params = new URL(request.url()).searchParams;
      let items = messages.filter(
        (message) => message.orderId === Number(chatPath[1]),
      );
      if (params.has("beforeId"))
        items = items.filter(
          (message) => message.id < Number(params.get("beforeId")),
        );
      if (params.has("afterId"))
        items = items.filter(
          (message) => message.id > Number(params.get("afterId")),
        );
      return json(200, { items, hasMore: false });
    }
    if (method !== "GET") {
      const body = request.postData()
        ? headers["content-type"]?.startsWith("multipart/form-data")
          ? { multipart: true }
          : request.postDataJSON()
        : null;
      mutations.push({
        method,
        path,
        body,
        authorization,
        csrf: headers["x-csrf-token"],
      });
      if (headers["x-csrf-token"] !== "test-token")
        return json(403, { message: "CSRF required" });
      if (path === "/api/user")
        return json(201, { name: body.name, userRole: "USER", balance: 0 });
      if (path === "/api/logout") {
        sessions.delete(sessionId ?? "");
        return route.fulfill({
          status: 204,
          headers: {
            "Set-Cookie":
              "test-session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax",
          },
        });
      }
      if (!username) return json(401, { message: "Unauthorized" });
      if (path === "/api/user/me" && method === "PATCH") {
        fields[username] = { ...fields[username], ...body };
        return json(200, profile(username));
      }
      if (path === "/api/user/me/avatar") {
        fields[username] = {
          ...fields[username],
          avatarUrl: method === "DELETE" ? null : "/api/media/avatar",
        };
        return json(200, profile(username));
      }
      const gameImage = /^\/api\/games\/(\d+)\/image$/.exec(path);
      if (gameImage) {
        const updated = {
          ...games.find((item) => item.id === Number(gameImage[1]))!,
          imageUrl: method === "DELETE" ? null : "/api/media/game",
        };
        games = games.map((item) => (item.id === updated.id ? updated : item));
        return json(200, updated);
      }
      const adviceImage = /^\/api\/advices\/(\d+)\/image$/.exec(path);
      if (adviceImage) {
        const updated = {
          ...listings.find((item) => item.id === Number(adviceImage[1]))!,
          imageUrl: method === "DELETE" ? null : "/api/media/advice",
        };
        listings = listings.map((item) =>
          item.id === updated.id ? updated : item,
        );
        return json(200, updated);
      }
      if (chatPath && method === "POST") {
        const key = `${username}/${chatPath[1]}/${body.clientMessageId}`;
        if (requests.has(key)) return json(200, requests.get(key));
        const message = {
          id: messages.length + 1,
          orderId: Number(chatPath[1]),
          sender: {
            username,
            userRole:
              roles[username] === "ADMIN"
                ? ("ADMIN" as const)
                : ("USER" as const),
          },
          text: body.text,
          sentAt: new Date().toISOString(),
        };
        messages.push(message);
        requests.set(key, message);
        for (const [ws, id] of sockets)
          if (id === message.orderId)
            ws.send(JSON.stringify({ type: "message.created", message }));
        return json(201, message);
      }
      if (path === "/api/advices" && method === "POST") {
        const advice = {
          ...sword,
          id: 33,
          name: body.name,
          price: body.price,
          userSeller: { username: "buyer", userRole: roles.buyer ?? "USER" },
        };
        listings.push(advice);
        return json(201, advice);
      }
      if (/^\/api\/advices\/\d+$/.test(path) && method === "DELETE") {
        const id = Number(path.split("/").at(-1));
        const listing = listings.find((advice) => advice.id === id);
        if (!listing) return json(404, { message: "Advice not found" });
        if (
          listing.userSeller.username !== username &&
          roles[username] !== "ADMIN"
        )
          return json(403, { message: "No permissions" });
        listings = listings.filter((advice) => advice.id !== id);
        return empty();
      }
      if (path === "/api/orders/" && method === "POST") {
        orderCreated = true;
        return json(201, order);
      }
      if (path === "/api/orders/73/feedback") {
        order = {
          ...order,
          rating: body.rating,
          feedbackText: body.feedbackText,
        };
        return empty();
      }
      if (path === "/api/orders/73/response") {
        order = { ...order, sellerResponseText: body.responseText };
        return empty();
      }
      if (path === "/api/orders/73/confirm") {
        if (order.orderStatus !== "OPENED")
          return json(409, { message: "order not open" });
        order = { ...order, orderStatus: "CLOSED" };
        return empty();
      }
      if (path === "/api/orders/73/cancel") {
        if (order.orderStatus !== "OPENED")
          return json(409, { message: "order not open" });
        order = { ...order, orderStatus: "REFUND" };
        return empty();
      }
      const adminAction =
        /^\/api\/admin\/orders\/74\/(reopen|confirm|refund)$/.exec(path);
      if (adminAction && method === "PATCH") {
        if (roles[username] !== "ADMIN")
          return json(403, { message: "No permissions" });
        const action = adminAction[1];
        const expected = action === "reopen" ? "CLOSED" : "OPENED";
        if (order.orderStatus !== expected)
          return json(409, { message: "order not open" });
        const next =
          action === "reopen"
            ? "OPENED"
            : action === "confirm"
              ? "CLOSED"
              : "REFUND";
        order = { ...order, orderStatus: next };
        return empty();
      }
      if (path === "/api/games") {
        if (!username || roles[username] !== "ADMIN")
          return json(403, { message: "No have permissions" });
        const created = { id: 8, name: body.name };
        games.push(created);
        return json(201, created);
      }
      if (path === "/api/games/8/delete") {
        games = games.filter((item) => item.id !== 8);
        return empty();
      }
    }
    if (!username) return json(401, { message: "Unauthorized" });
    if (path === "/api/admin/orders") {
      if (roles[username] !== "ADMIN")
        return json(403, { message: "No permissions" });
      return json(200, [adminOrder(), ...(orderCreated ? [order] : [])]);
    }
    if (path === "/api/orders")
      return json(
        200,
        orderCreated &&
          [
            order.userBuyerDTO.username,
            order.adviceDTO.userSeller.username,
          ].includes(username)
          ? [order]
          : [],
      );
    if (path === "/api/orders/73") return json(200, order);
    if (path === "/api/orders/74") return json(200, adminOrder());
    return json(404, { message: "Unknown route" });
  });
  return mutations;
}

async function login(page: Page, name = "buyer") {
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill(name);
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

async function selectGame(page: Page) {
  await page
    .getByRole("button", { name: "Открыть игру Arena", exact: true })
    .click();
  await expect(page.locator(".listing-card")).toHaveCount(2);
}

async function purchase(page: Page) {
  await page
    .locator(".listing-card")
    .filter({ hasText: "Silver Sword" })
    .getByRole("button", { name: "Купить", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Создать заказ", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Заказ #73." })).toBeVisible();
}

test("старт без вымышленных данных; недоступный API не подменяется каталогом", async ({
  page,
}) => {
  await page.route("**/api/**", (route) => route.abort("connectionrefused"));
  await page.goto("/");
  await expect(page.locator(".listing-card")).toHaveCount(0);
  await expect(page.locator(".game-card")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("buyer");
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "Не удалось связаться с сервером",
  );
  await expect(page.locator(".profile")).toHaveCount(0);
});

test("публичный маркет показывает игры из API и предлагает вход перед покупкой", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  await page.route("**/api/games", (route) =>
    route.fulfill({
      json: [
        game,
        { id: 9, name: "Craft World" },
        { id: 12, name: "Racing Club" },
      ],
    }),
  );
  await page.goto("/");
  await expect(page.locator(".game-card")).toHaveCount(3);
  await expect(
    page.getByRole("button", { name: "Открыть игру Craft World", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("games-desktop.png"),
    fullPage: true,
  });
  await selectGame(page);
  await page
    .locator(".listing-card")
    .first()
    .getByRole("button", { name: "Купить", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("dialog").getByLabel("Пароль", { exact: true }),
  ).toBeVisible();
  expect(mutations).toHaveLength(0);
});

test("списки игр и заказов не подменяются локальными данными при ошибке API", async ({
  page,
}) => {
  await server(page);
  await page.addInitScript(() => {
    localStorage.setItem(
      "itembro-games",
      JSON.stringify([{ id: 99, name: "Fake Game" }]),
    );
  });
  await page.route("**/api/games", (route) =>
    route.fulfill({ status: 503, json: { message: "Каталог недоступен" } }),
  );
  await page.goto("/");
  await expect(
    page.getByText("Сервер не смог выполнить запрос. Попробуй позже.", {
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.locator(".game-card")).toHaveCount(0);
  await page.route("**/api/orders", (route) =>
    route.fulfill({ json: { content: [] } }),
  );
  await login(page);
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(
    page.getByText("Сервер вернул список заказов в неожиданном формате.", {
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.locator(".order-row")).toHaveCount(0);
});

test("объявления показывают среднюю оценку рядом с продавцом без номера объявления", async ({
  page,
}, testInfo) => {
  await server(page);
  let reviewRequests = 0;
  await page.route("**/api/users/seller/reviews", (route) => {
    reviewRequests++;
    return route.fulfill({
      json: [
        { rating: 5, feedback: "Отлично", sellerResponse: null },
        { rating: 4, feedback: "Хорошо", sellerResponse: null },
      ],
    });
  });
  await page.goto("/");
  await selectGame(page);
  const ratings = page.locator(".listing-card .seller-rating");
  await expect(ratings).toHaveText(["4,5", "4,5"]);
  await expect(ratings.first()).toHaveAccessibleName(
    "Средняя оценка продавца seller: 4,5 из 5",
  );
  await expect(page.getByText(/Объявление #\d+/)).toHaveCount(0);
  expect(reviewRequests).toBe(1);
  await page.screenshot({
    path: testInfo.outputPath("seller-rating-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 320, height: 800 });
  await expect(page.locator(".sidebar")).toBeHidden();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("seller-rating-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Silver Sword", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.locator(".seller-rating")).toHaveText("4,5");
  await dialog.getByRole("button", { name: "seller", exact: true }).click();
  const profile = page.getByRole("region", {
    name: "Профиль seller",
    exact: true,
  });
  await expect(profile.locator(".listing-card .seller-rating")).toHaveText([
    "4,5",
    "4,5",
  ]);
  const requestsAfterProfile = reviewRequests;
  await profile.getByRole("button", { name: /Отзывы/ }).click();
  await profile.getByRole("button", { name: /Активные объявления/ }).click();
  await expect(profile.locator(".listing-card .seller-rating")).toHaveText([
    "4,5",
    "4,5",
  ]);
  expect(reviewRequests).toBe(requestsAfterProfile);
});

for (const status of [200, 503]) {
  test(`отсутствующие или недоступные отзывы не подменяются оценкой (${status})`, async ({
    page,
  }) => {
    await server(page);
    await page.route("**/api/users/seller/reviews", (route) =>
      route.fulfill({
        status,
        json: status === 200 ? [] : { message: "Unavailable" },
      }),
    );
    await page.goto("/");
    const response = page.waitForResponse("**/api/users/seller/reviews");
    await selectGame(page);
    await response;
    await expect(page.locator(".listing-card .seller-rating")).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Silver Sword", exact: true }),
    ).toBeVisible();
    await expect(page.locator(".listing-card .buy-button")).toHaveCount(2);
    await expect(page.getByRole("alert")).toHaveCount(0);
  });
}

test("регистрация отправляет CSRF без Basic, затем выполняется вход", async ({
  page,
}) => {
  const mutations = await server(page);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("button", { name: "Зарегистрироваться", exact: true })
    .click();
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("new_user");
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await dialog
    .getByRole("button", { name: "Создать аккаунт", exact: true })
    .click();
  await expect(page.locator(".profile")).toContainText("new_user");
  expect(mutations).toEqual([
    {
      method: "POST",
      path: "/api/user",
      body: { name: "new_user", password },
      authorization: undefined,
      csrf: "test-token",
    },
  ]);
  await page.reload();
  await expect(page.locator(".profile")).toContainText("new_user");
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ),
  ).not.toContain(password);
});

test("после входа объявления используют cookie и CSRF без повторной отправки Basic", async ({
  page,
}) => {
  const mutations = await server(page);
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await page
    .getByRole("button", { name: "Разместить объявление", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByLabel("Название предмета", { exact: true })
    .fill("Crystal Helm");
  await dialog.getByLabel("Цена, ₽", { exact: true }).fill("125.50");
  await dialog
    .getByRole("button", { name: "Опубликовать объявление", exact: true })
    .click();
  await expect(page.locator(".listing-card")).toHaveCount(1);
  await expect(page.locator(".listing-card")).toContainText("Crystal Helm");
  await page.getByRole("button", { name: "В архив", exact: true }).click();
  await dialog
    .getByRole("button", { name: "Архивировать", exact: true })
    .click();
  await expect(page.locator(".listing-card")).toHaveCount(0);
  expect(
    mutations.map(({ method, path, body }) => ({ method, path, body })),
  ).toEqual([
    {
      method: "POST",
      path: "/api/advices",
      body: { name: "Crystal Helm", game_id: 7, price: 125.5 },
    },
    { method: "DELETE", path: "/api/advices/33", body: null },
  ]);
  for (const request of mutations) {
    expect(request.csrf).toBe("test-token");
    expect(request.authorization).toBeUndefined();
  }
});

test("покупка и продажа: серверный список, отзыв и чат с REST и WebSocket", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  const chatRequests: string[] = [];
  page.on("request", (request) => {
    if (/\/(chat|messages)/.test(new URL(request.url()).pathname))
      chatRequests.push(request.url());
  });
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const workspace = page.getByRole("region", {
    name: "Заказ #73",
    exact: true,
  });
  const summary = workspace.getByRole("complementary", {
    name: "Действия и отзыв",
  });
  const chat = workspace.getByRole("region", { name: "Чат заказа" });
  await expect(workspace.locator(".order-kind")).toHaveText("Покупка");
  await expect(workspace.locator(".order-status")).toHaveText("Открыт");
  const left = await summary.boundingBox();
  const right = await chat.boundingBox();
  expect(left!.x + left!.width).toBeLessThan(right!.x);
  await expect(
    summary.getByRole("button", { name: "Подтвердить получение", exact: true }),
  ).toBeVisible();
  await summary.getByLabel("Отзыв", { exact: true }).fill("Хорошая сделка");
  await summary
    .getByRole("button", { name: "Сохранить отзыв", exact: true })
    .click();
  await expect(summary.locator(".review-block")).toContainText(
    "Хорошая сделка",
  );
  const mutationsBeforeChat = mutations.length;
  await chat
    .getByLabel("Сообщение", { exact: true })
    .fill("Когда удобно передать предмет?");
  await chat.getByRole("button", { name: "Отправить сообщение" }).click();
  await expect(chat.getByRole("log")).toContainText(
    "Когда удобно передать предмет?",
  );
  expect(mutations.length).toBe(mutationsBeforeChat + 1);
  expect(mutations.at(-1)).toMatchObject({
    method: "POST",
    path: "/api/orders/73/messages",
    csrf: "test-token",
  });
  expect(
    chatRequests.some((url) => url.includes("/api/orders/73/messages")),
  ).toBe(true);
  await expect(chat.locator(".chat-message")).toHaveCount(1);
  await page.screenshot({
    path: testInfo.outputPath("order-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("order-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await summary
    .getByRole("button", { name: "Подтвердить получение", exact: true })
    .click();
  await expect(workspace.locator(".order-status")).toHaveText("Закрыт");
  await expect(
    summary.getByRole("button", { name: "Подтвердить получение", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row")).toContainText("Покупка");
  await page.locator(".profile").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Выйти", exact: true })
    .click();
  await login(page, "seller");
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row")).toContainText("Продажа");
  await page.getByRole("button", { name: /^Продажи/ }).click();
  await page.locator(".order-row").click();
  await expect(workspace.locator(".order-kind")).toHaveText("Продажа");
  await expect(summary.getByLabel("Отзыв", { exact: true })).toHaveCount(0);
  await summary
    .getByLabel("Ответ продавца", { exact: true })
    .fill("Спасибо за покупку");
  await summary
    .getByRole("button", { name: "Сохранить ответ", exact: true })
    .click();
  await expect(summary.locator("blockquote")).toContainText(
    "Спасибо за покупку",
  );
  expect(
    mutations.find((request) => request.path === "/api/orders/")?.body,
  ).toEqual({ id: 31 });
});

test("каталог из API: поиск, избранное и мобильное меню", async ({
  page,
}, testInfo) => {
  await server(page);
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await page.screenshot({
    path: testInfo.outputPath("desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.getByLabel("Поиск предмета на странице").fill("Silver");
  await expect(page.locator(".listing-card")).toHaveCount(1);
  await page
    .getByRole("button", { name: "Очистить поиск", exact: true })
    .click();
  await page
    .getByRole("button", { name: /^В избранное:/ })
    .first()
    .click();
  await page.getByRole("button", { name: /^Избранное/ }).click();
  await expect(page.locator(".listing-card")).toHaveCount(1);
  await page.getByRole("button", { name: "Маркет", exact: true }).click();
  await expect(page.locator(".game-card")).toHaveCount(1);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".sidebar")).toHaveCSS("visibility", "hidden");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Открыть меню", exact: true }).click();
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(
    page.getByRole("heading", { name: "Мои заказы." }),
  ).toBeVisible();
});

test("USER не видит управление даже с именем admin и подменой localStorage", async ({
  page,
}) => {
  const mutations = await server(page, { admin: "USER" });
  await page.goto("/");
  const management = page.getByRole("button", {
    name: "Управление",
    exact: true,
  });
  await expect(management).toHaveCount(0);
  await login(page, "admin");
  await expect(management).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Создать игру", exact: true }),
  ).toHaveCount(0);
  await page.evaluate(() =>
    localStorage.setItem("itembro.account", JSON.stringify({ role: "ADMIN" })),
  );
  await expect(management).toHaveCount(0);
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row")).toHaveCount(0);
  expect(mutations).toEqual([]);
});

test("ADMIN получает управление после профиля; после выхода и входа USER доступ исчезает", async ({
  page,
}) => {
  const mutations = await server(page);
  await page.goto("/");
  const management = page.getByRole("button", {
    name: "Управление",
    exact: true,
  });
  await expect(management).toHaveCount(0);
  await login(page, "admin");
  await expect(management).toBeVisible();
  await management.click();
  await page.getByRole("button", { name: "Создать игру", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Название игры", { exact: true }).fill("New Arena");
  await dialog
    .getByRole("button", { name: "Создать игру", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await page
    .getByRole("button", { name: "Архивировать игру New Arena", exact: true })
    .click();
  await dialog
    .getByRole("button", { name: "Архивировать игру", exact: true })
    .click();
  await expect(
    page.locator(".managed-game").filter({ hasText: "New Arena" }),
  ).toHaveCount(0);
  expect(mutations.at(-1)?.path).toBe("/api/games/8/delete");
  await page.locator(".profile").click();
  await dialog.getByRole("button", { name: "Выйти", exact: true }).click();
  await expect(management).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Управление." })).toHaveCount(
    0,
  );
  await login(page, "buyer");
  await expect(management).toHaveCount(0);
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row")).toHaveCount(0);
});

test("администратор удаляет чужое объявление внутри объявления с подтверждением", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  await page.goto("/");
  await login(page, "admin");
  await expect(page.locator(".profile .username-admin")).toHaveText("admin");
  await page.getByRole("button", { name: "Управление", exact: true }).click();
  await expect(
    page.getByLabel("Номер объявления", { exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Маркет", exact: true }).click();
  await selectGame(page);
  await page.getByRole("button", { name: "Silver Sword", exact: true }).click();
  const dialog = page.getByRole("dialog");
  const adminFunctions = dialog.getByRole("region", {
    name: "Админ-функции",
    exact: true,
  });
  await expect(adminFunctions).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("advice-admin-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: testInfo.outputPath("advice-admin-mobile.png"),
    fullPage: true,
  });
  await adminFunctions
    .getByRole("button", { name: "Удалить объявление", exact: true })
    .click();
  expect(mutations).toHaveLength(0);
  await expect(dialog).toContainText("Silver Sword");
  await dialog
    .getByRole("button", { name: "Архивировать", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator(".listing-card")).toHaveCount(1);
  expect(mutations.at(-1)).toMatchObject({
    method: "DELETE",
    path: "/api/advices/31",
    csrf: "test-token",
  });
});

test("роль из DTO подсвечивает администратора гостю и покупателю без выдачи админ-функций", async ({
  page,
}) => {
  await server(page, { seller: "ADMIN" });
  await page.goto("/");
  await selectGame(page);
  await expect(page.locator(".seller .username-admin")).toHaveCount(2);
  await expect(page.locator(".seller .username-admin").first()).toHaveCSS(
    "color",
    "rgb(197, 35, 56)",
  );
  await page.getByRole("button", { name: "Silver Sword", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.locator(".username-admin")).toHaveText("seller");
  await expect(
    dialog.getByRole("region", { name: "Админ-функции" }),
  ).toHaveCount(0);
  await dialog.getByRole("button", { name: "Закрыть окно" }).click();
  await login(page);
  await purchase(page);
  await expect(page.locator(".order-parties .username-admin")).toHaveText(
    "seller",
  );
  await expect(page.locator(".order-chat-header .username-admin")).toHaveText(
    "seller",
  );
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row .username-admin")).toHaveText("seller");
  await page.locator(".order-row").click();
  await page.getByRole("button", { name: "К заказам", exact: true }).click();
  await page.getByRole("button", { name: "Маркет", exact: true }).click();
  await selectGame(page);
  await page.getByRole("button", { name: "Silver Sword", exact: true }).click();
  await expect(
    dialog.getByRole("region", { name: "Админ-функции" }),
  ).toHaveCount(0);
});

test("публичный профиль: активные объявления, отзывы и отметка администратора", async ({
  page,
}, testInfo) => {
  await server(page, { seller: "ADMIN" });
  await page.route("**/api/users/seller/reviews", (route) =>
    route.fulfill({
      json: [
        {
          orderId: 73,
          adviceName: "Silver Sword",
          userBuyer: { username: "buyer", userRole: "USER" },
          rating: 5,
          feedbackText: "Предмет получил, всё отлично.",
          sellerResponseText: "Спасибо за покупку!",
        },
      ],
    }),
  );
  await page.goto("/#/users/seller");
  const profile = page.getByRole("region", {
    name: "Профиль seller",
    exact: true,
  });
  await expect(
    profile.getByRole("heading", { name: "seller", exact: true }),
  ).toBeVisible();
  await expect(
    profile.getByText("Администратор", { exact: true }),
  ).toBeVisible();
  await expect(profile.locator(".listing-card")).toHaveCount(2);
  await expect(page.getByRole("button", { name: /Мои заказы/ })).toBeVisible();
  await expect(profile.getByRole("button", { name: /Заказы/ })).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("profile-desktop.png"),
    fullPage: true,
  });
  await profile.getByRole("button", { name: /Отзывы/ }).click();
  await expect(profile.locator(".profile-review")).toHaveCount(1);
  await expect(profile).toContainText("Предмет получил, всё отлично.");
  await expect(profile).toContainText("Спасибо за покупку!");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".sidebar")).toBeHidden();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("profile-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await profile.getByRole("button", { name: "buyer", exact: true }).click();
  await expect(page).toHaveURL(/#\/users\/buyer$/);
  await expect(
    page.getByRole("region", { name: "Профиль buyer", exact: true }),
  ).toContainText("Нет активных объявлений");
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "buyer", exact: true }),
  ).toBeVisible();
  await login(page);
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator(".profile")).toContainText("buyer");
});

test("отзывы в формате текущего Java DTO отображаются без белого экрана", async ({
  page,
}) => {
  await server(page);
  await page.route("**/api/users/seller/reviews", (route) =>
    route.fulfill({
      json: [
        {
          rating: 4,
          feedback: "Сделка прошла хорошо",
          sellerResponse: "Спасибо!",
        },
        { rating: 5, feedback: "Всё отлично", sellerResponse: null },
      ],
    }),
  );
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto("/#/users/seller");
  const profile = page.getByRole("region", {
    name: "Профиль seller",
    exact: true,
  });
  await profile.getByRole("button", { name: /Отзывы/ }).click();
  await expect(profile.locator(".profile-review")).toHaveCount(2);
  await expect(profile).toContainText("Сделка прошла хорошо");
  await expect(profile).toContainText("Спасибо!");
  await expect(profile.locator(".profile-stat strong")).toHaveText("4.5");
  await expect(
    profile
      .locator(".profile-review")
      .first()
      .getByText("Покупатель", { exact: true }),
  ).toBeVisible();
  await expect(
    profile
      .locator(".profile-review")
      .first()
      .getByRole("button", { name: "buyer", exact: true }),
  ).toHaveCount(0);
  expect(pageErrors).toEqual([]);
});

test("некорректный отзыв показывает ошибку без падения профиля и каталога", async ({
  page,
}) => {
  await server(page);
  await page.route("**/api/users/seller/reviews", (route) =>
    route.fulfill({
      json: [{ rating: 5, feedback: { invalid: true }, sellerResponse: null }],
    }),
  );
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto("/#/users/seller");
  const profile = page.getByRole("region", {
    name: "Профиль seller",
    exact: true,
  });
  await profile.getByRole("button", { name: /Отзывы/ }).click();
  await expect(profile).toContainText(
    "Сервер вернул отзыв в неожиданном формате.",
  );
  await expect(profile.locator(".profile-review")).toHaveCount(0);
  await profile.getByRole("button", { name: /Активные объявления/ }).click();
  await expect(profile.locator(".listing-card")).toHaveCount(2);
  expect(pageErrors).toEqual([]);
});

test("ник открывает профиль, недоступный профиль не получает вымышленных данных", async ({
  page,
}) => {
  await server(page);
  await page.goto("/");
  await selectGame(page);
  await page
    .locator(".seller")
    .first()
    .getByRole("button", { name: "seller", exact: true })
    .click();
  await expect(page).toHaveURL(/#\/users\/seller$/);
  await expect(
    page.getByRole("heading", { name: "seller", exact: true }),
  ).toBeVisible();
  await page.goto("/#/users/missing");
  await expect(
    page.getByRole("heading", {
      name: "Не удалось открыть профиль",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.locator(".public-profile-heading")).toHaveCount(0);
  await expect(page.locator(".listing-card")).toHaveCount(0);
});

test("администратор ищет чужой заказ и открывает его чат из управления", async ({
  page,
}, testInfo) => {
  await server(page);
  let historyAvailable = false;
  await page.route(/\/api\/orders\/74\/messages(?:\?|$)/, (route) => {
    if (historyAvailable) return route.fallback();
    return route.fulfill({ status: 403, json: { message: "No permissions" } });
  });
  await page.goto("/");
  await login(page, "admin");
  await page.getByRole("button", { name: "Управление", exact: true }).click();
  const trades = page.getByRole("region", {
    name: "Заказы и чаты площадки",
    exact: true,
  });
  await expect(trades.locator(".admin-order-card")).toHaveCount(1);
  await expect(trades).toContainText("Ancient Ring");
  await expect(trades).toContainText("alex");
  await trades
    .getByRole("textbox", { name: "Поиск заказов по названию", exact: true })
    .fill("не найдено");
  await expect(trades).toContainText("Заказы не найдены");
  await trades
    .getByRole("textbox", { name: "Поиск заказов по названию", exact: true })
    .fill("ring");
  await expect(trades.locator(".admin-order-card")).toHaveCount(1);
  await page.screenshot({
    path: testInfo.outputPath("admin-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".sidebar")).toBeHidden();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("admin-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await trades.getByRole("button", { name: "Все чаты", exact: true }).click();
  await page.route("**/api/orders/74", (route) =>
    route.fulfill({
      json: {
        id: 74,
        adviceDTO: {
          ...sword,
          name: "Ancient Ring",
          userSeller: { username: "mira", userRole: "USER" },
        },
        userBuyerDTO: { username: "alex", userRole: "USER" },
        price: 25,
        orderStatus: "OPENED",
        rating: -1,
        feedbackText: null,
        sellerResponseText: null,
      },
    }),
  );
  await trades
    .getByRole("button", { name: "Открыть чат", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Заказ #74." })).toBeVisible();
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await expect(chat.locator(".order-chat-header")).toContainText(
    "Просмотр администратором · Нет доступа",
  );
  await expect(chat.getByRole("alert")).toContainText("No permissions");
  await expect(chat.getByLabel("Сообщение", { exact: true })).toBeDisabled();
  historyAvailable = true;
  await chat.getByRole("button", { name: "Обновить", exact: true }).click();
  await expect(chat.locator(".order-chat-header")).toContainText(
    "Просмотр администратором · Подключён",
  );
  await expect(chat.getByRole("alert")).toHaveCount(0);
  await page.getByRole("button", { name: "В управление", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Управление." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Открыть меню", exact: true }).click();
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  await expect(page.locator(".order-row")).toHaveCount(0);
});

test("неизвестная роль в профиле не даёт войти и получить управление", async ({
  page,
}) => {
  await server(page, { admin: "OWNER" });
  await page.goto("/");
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("admin");
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("некорректный профиль");
  await expect(page.locator(".profile")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Управление", exact: true }),
  ).toHaveCount(0);
});

test("ошибка профиля не считается успешным входом", async ({ page }) => {
  await page.route("**/api/user/me", (route) =>
    route.fulfill({ status: 404, json: { message: "Unknown route" } }),
  );
  await page.goto("/");
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("buyer");
  await dialog.getByLabel("Пароль", { exact: true }).fill(password);
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("Unknown route");
  await expect(page.locator(".profile")).toHaveCount(0);
});

test("неверный пароль: запрос помечен как XMLHttpRequest, ошибка показана в форме", async ({
  page,
}) => {
  let requestedWith: string | undefined;
  await page.route("**/api/user/me", (route) => {
    requestedWith = route.request().headers()["x-requested-with"];
    return route.fulfill({ status: 401 });
  });
  await page.goto("/");
  await page
    .getByRole("button", { name: "Войти", exact: true })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Имя пользователя", { exact: true }).fill("buyer");
  await dialog.getByLabel("Пароль", { exact: true }).fill("WrongPassword123!");
  await dialog.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "Неверное имя или пароль",
  );
  expect(requestedWith).toBe("XMLHttpRequest");
  await expect(page.locator(".profile")).toHaveCount(0);
});

test("F5 восстанавливает ADMIN по HttpOnly cookie, выход сохраняется после следующего F5", async ({
  page,
}) => {
  const mutations = await server(page);
  const authHeaders: (string | undefined)[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/user/me")
      authHeaders.push(request.headers().authorization);
  });
  await page.goto("/");
  await login(page, "admin");
  const management = page.getByRole("button", {
    name: "Управление",
    exact: true,
  });
  await expect(management).toBeVisible();
  const countBeforeReload = authHeaders.length;
  await page.reload();
  await expect(page.locator(".profile")).toContainText("admin");
  await expect(management).toBeVisible();
  expect(
    authHeaders
      .slice(countBeforeReload)
      .every((header) => header === undefined),
  ).toBeTruthy();
  expect(authHeaders.filter(Boolean)).toEqual([
    `Basic ${btoa(`admin:${password}`)}`,
  ]);
  expect(
    (await page.context().cookies()).find(
      (cookie) => cookie.name === "test-session",
    )?.httpOnly,
  ).toBe(true);
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ),
  ).not.toContain(password);
  await page.locator(".profile").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Выйти", exact: true })
    .click();
  await expect(management).toHaveCount(0);
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Войти", exact: true }).first(),
  ).toBeEnabled();
  await expect(page.locator(".profile")).toHaveCount(0);
  await expect(management).toHaveCount(0);
  expect(mutations.at(-1)).toMatchObject({
    path: "/api/logout",
    method: "POST",
    csrf: "test-token",
    authorization: undefined,
  });
});

test("истёкшая сессия после F5 не восстанавливает профиль из localStorage", async ({
  page,
}) => {
  await server(page);
  await page.goto("/");
  await login(page, "admin");
  await page.context().clearCookies();
  await page.evaluate(() =>
    localStorage.setItem(
      "itembro.account",
      JSON.stringify({ name: "admin", role: "ADMIN" }),
    ),
  );
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Войти", exact: true }).first(),
  ).toBeEnabled();
  await expect(page.locator(".profile")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Управление", exact: true }),
  ).toHaveCount(0);
});

const image = {
  name: "photo.png",
  mimeType: "image/png",
  buffer: Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=",
    "base64",
  ),
};

test("профиль и аватар сохраняются на сервере, переживают F5 и видны публично", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  const uploads: {
    type: string;
    body: string;
    csrf?: string;
    authorization?: string;
  }[] = [];
  page.on("request", (request) => {
    if (request.method() === "PUT")
      uploads.push({
        type: request.headers()["content-type"],
        body: request.postDataBuffer()?.toString("latin1") ?? "",
        csrf: request.headers()["x-csrf-token"],
        authorization: request.headers().authorization,
      });
  });
  await page.goto("/");
  await login(page);
  await page.locator(".profile").click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Возраст", { exact: true }).fill("25");
  await dialog.getByLabel("Город", { exact: true }).fill("  Казань  ");
  await dialog
    .getByLabel("О себе", { exact: true })
    .fill("  Играю по вечерам.  ");
  await dialog
    .getByRole("button", { name: "Сохранить профиль", exact: true })
    .click();
  await expect(dialog.getByRole("status")).toHaveText("Профиль сохранён.");
  expect(mutations.at(-1)).toMatchObject({
    method: "PATCH",
    path: "/api/user/me",
    csrf: "test-token",
    body: { age: 25, city: "Казань", about: "Играю по вечерам." },
  });
  const avatar = dialog.getByRole("region", { name: "Аватар", exact: true });
  await avatar.locator("input[type=file]").setInputFiles(image);
  await expect(avatar.getByRole("img")).toHaveAttribute("src", /^blob:/);
  await avatar
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(avatar.getByRole("img")).toHaveAttribute(
    "src",
    "/api/media/avatar",
  );
  expect(uploads).toHaveLength(1);
  expect(uploads[0].type).toMatch(/^multipart\/form-data; boundary=/);
  expect(uploads[0].body).toContain('name="file"; filename="photo.png"');
  expect(uploads[0].csrf).toBe("test-token");
  expect(uploads[0].authorization).toBeUndefined();
  await page.screenshot({
    path: testInfo.outputPath("profile-settings-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("profile-settings-mobile.png"),
    fullPage: true,
  });
  await page.reload();
  await expect(page.locator(".profile img")).toHaveAttribute(
    "src",
    "/api/media/avatar",
  );
  await page.locator(".profile").click();
  await expect(dialog.getByLabel("Возраст", { exact: true })).toHaveValue("25");
  await expect(dialog.getByLabel("Город", { exact: true })).toHaveValue(
    "Казань",
  );
  await expect(dialog.getByLabel("О себе", { exact: true })).toHaveValue(
    "Играю по вечерам.",
  );
  await dialog
    .getByRole("button", { name: "Открыть мой профиль", exact: true })
    .click();
  const profile = page.getByRole("region", {
    name: "Профиль buyer",
    exact: true,
  });
  await expect(profile.locator(".profile-details")).toHaveText(
    "25 лет · Казань",
  );
  await expect(profile.locator(".profile-about")).toHaveText(
    "Играю по вечерам.",
  );
  await expect(
    profile.getByRole("img", { name: "Аватар buyer" }),
  ).toBeVisible();
  // Те же сведения доступны гостю; баланс в публичный профиль не выводится.
  await page.locator(".profile").click();
  await dialog.getByRole("button", { name: "Выйти", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.goto("/#/users/buyer");
  await expect(profile.locator(".profile-details")).toHaveText(
    "25 лет · Казань",
  );
  await login(page);
  await page.locator(".profile").click();
  await avatar
    .getByRole("button", { name: "Удалить изображение", exact: true })
    .click();
  await expect(avatar.getByRole("img")).toHaveCount(0);
  await dialog.getByLabel("Возраст", { exact: true }).fill("");
  await dialog.getByLabel("Город", { exact: true }).fill("");
  await dialog.getByLabel("О себе", { exact: true }).fill("");
  await dialog
    .getByRole("button", { name: "Сохранить профиль", exact: true })
    .click();
  await expect(dialog.getByRole("status")).toBeVisible();
  expect(mutations.at(-1)?.body).toEqual({
    age: null,
    city: null,
    about: null,
  });
});

test("изображения игры и объявления: права в UI, multipart, проверка файла и ошибка сервера", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  await page.goto("/");
  await login(page, "admin");
  await page.getByRole("button", { name: "Управление", exact: true }).click();
  await page
    .getByRole("button", { name: "Изображение игры Arena", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  const gameEditor = dialog.getByRole("region", {
    name: "Изображение игры",
    exact: true,
  });
  await gameEditor.locator("input[type=file]").setInputFiles(image);
  await gameEditor
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(gameEditor.getByRole("img")).toHaveAttribute(
    "src",
    "/api/media/game",
  );
  await dialog
    .getByRole("button", { name: "Закрыть окно", exact: true })
    .click();
  await page.getByRole("button", { name: "Маркет", exact: true }).click();
  await expect(page.locator(".game-card img")).toHaveAttribute(
    "src",
    "/api/media/game",
  );
  await page.locator(".profile").click();
  await dialog.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, "seller");
  await selectGame(page);
  await page
    .getByRole("button", {
      name: "Открыть объявление Silver Sword",
      exact: true,
    })
    .click();
  const editor = dialog.getByRole("region", {
    name: "Изображение объявления",
    exact: true,
  });
  const input = editor.locator("input[type=file]");
  const countBeforeInvalidFile = mutations.length;
  await input.setInputFiles({
    name: "bad.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from("<svg/>"),
  });
  await expect(editor.getByRole("alert")).toContainText(
    "Выбери JPEG, PNG или WebP",
  );
  await input.setInputFiles({
    name: "large.png",
    mimeType: "image/png",
    buffer: Buffer.alloc(5 * 1024 * 1024 + 1),
  });
  await expect(editor.getByRole("alert")).toContainText("до 5 МБ");
  expect(mutations.length).toBe(countBeforeInvalidFile);
  let failUpload = true;
  await page.route("**/api/advices/31/image", (route) =>
    failUpload && route.request().method() === "PUT"
      ? route.fulfill({ status: 503, json: { message: "Unavailable" } })
      : route.fallback(),
  );
  await input.setInputFiles(image);
  await editor
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(editor.getByRole("alert")).toContainText(
    "Сервер не смог выполнить запрос",
  );
  await expect(editor.getByRole("img")).toHaveAttribute("src", /^blob:/);
  await expect(
    editor.getByRole("button", { name: "Удалить изображение", exact: true }),
  ).toHaveCount(0);
  failUpload = false;
  await editor
    .getByRole("button", { name: "Сохранить изображение", exact: true })
    .click();
  await expect(
    dialog.getByRole("img", { name: "Silver Sword", exact: true }),
  ).toHaveAttribute("src", "/api/media/advice");
  await expect(editor.getByRole("img")).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("advice-image-desktop.png"),
    fullPage: true,
  });
  await dialog
    .getByRole("button", { name: "Закрыть окно", exact: true })
    .click();
  const card = page
    .locator(".listing-card")
    .filter({ hasText: "Silver Sword" });
  await expect(
    card.getByRole("img", { name: "Silver Sword", exact: true }),
  ).toHaveAttribute("src", "/api/media/advice");
  await page.reload();
  await selectGame(page);
  await expect(
    card.getByRole("img", { name: "Silver Sword", exact: true }),
  ).toBeVisible();
  await page.locator(".profile").click();
  await dialog.getByRole("button", { name: "Выйти", exact: true }).click();
  await page
    .getByRole("button", {
      name: "Открыть объявление Silver Sword",
      exact: true,
    })
    .click();
  await expect(editor).toHaveCount(0);
  for (const mutation of mutations.filter((item) => item.method === "PUT")) {
    expect(mutation.body).toEqual({ multipart: true });
    expect(mutation.csrf).toBe("test-token");
    expect(mutation.authorization).toBeUndefined();
  }
});

test("чат: ранняя история, входящие события, повтор отправки и восстановление пропущенных сообщений", async ({
  page,
}) => {
  const sockets: WebSocketRoute[] = [];
  await server(page, undefined, (socket) => sockets.push(socket));
  const message = (
    id: number,
    text: string,
    sender = "seller",
  ): ChatMessage => ({
    id,
    orderId: 73,
    sender: { username: sender, userRole: "USER" },
    text,
    sentAt: "2026-10-17T12:30:00Z",
  });
  const history = [
    message(50, "Раннее сообщение"),
    message(51, "Последнее сообщение"),
  ];
  let latestOnly = true;
  const cursors: string[] = [];
  const attempts: { text: string; clientMessageId: string }[] = [];
  await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, async (route) => {
    const request = route.request();
    const params = new URL(request.url()).searchParams;
    if (request.method() === "GET") {
      const before = params.get("beforeId"),
        after = params.get("afterId");
      cursors.push(params.toString());
      const items = before
        ? history.filter((item) => item.id < Number(before))
        : after
          ? history.filter((item) => item.id > Number(after))
          : latestOnly
            ? history.slice(-1)
            : history;
      return route.fulfill({
        json: { items, hasMore: !before && !after && latestOnly },
      });
    }
    expect(request.headers()["x-csrf-token"]).toBe("test-token");
    attempts.push(request.postDataJSON());
    if (attempts.length === 1) return route.fulfill({ status: 503 });
    const saved = message(
      attempts.length === 2 ? 53 : 55,
      attempts.at(-1)!.text,
      "buyer",
    );
    history.push(saved);
    return route.fulfill({ status: 201, json: saved });
  });
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await expect(chat).toContainText("Последнее сообщение");
  await expect.poll(() => sockets.length).toBe(1);
  await expect(chat).toContainText("Подключён");
  await chat
    .getByRole("button", { name: "Ранние сообщения", exact: true })
    .click();
  await expect(chat.getByRole("log")).toContainText("Раннее сообщение");
  expect(cursors.some((cursor) => cursor.includes("beforeId=51"))).toBeTruthy();
  const incoming = message(52, "Готов передать предмет");
  history.push(incoming);
  sockets[0].send(
    JSON.stringify({ type: "message.created", message: incoming }),
  );
  sockets[0].send(
    JSON.stringify({ type: "message.created", message: incoming }),
  );
  await expect(chat.locator(".chat-message")).toHaveCount(3);
  const draft = chat.getByLabel("Сообщение", { exact: true });
  await draft.fill("Заберу вечером");
  await draft.press("Enter");
  await expect(chat.getByRole("alert")).toContainText(
    "Сервер не смог выполнить запрос",
  );
  await expect(draft).toHaveValue("Заберу вечером");
  await expect(chat.getByRole("log")).not.toContainText("Заберу вечером");
  await chat
    .getByRole("button", { name: "Отправить сообщение", exact: true })
    .click();
  await expect(chat.getByRole("log")).toContainText("Заберу вечером");
  expect(attempts[0].clientMessageId).toBeTruthy();
  expect(attempts[1].clientMessageId).toBe(attempts[0].clientMessageId);
  await expect(draft).toHaveValue("");
  sockets[0].close();
  await expect(chat).toContainText("Автообновление");
  history.push(message(54, "Сообщение во время разрыва"));
  // Успешный POST с большим ID во время разрыва не должен скрыть пропущенное.
  await draft.fill("Отправлено без сокета");
  await draft.press("Enter");
  await expect(chat.getByRole("log")).toContainText("Отправлено без сокета");
  await expect.poll(() => sockets.length).toBe(2);
  await expect(chat.getByRole("log")).toContainText(
    "Сообщение во время разрыва",
  );
  await expect(chat.locator(".chat-message")).toHaveCount(6);
  latestOnly = false;
  await chat.getByRole("button", { name: "Обновить", exact: true }).click();
  await expect(chat.locator(".chat-message")).toHaveCount(6);
  await expect(chat.getByRole("log")).toContainText("Заберу вечером");
});

test("анимированное окно закрывается по Escape и возвращает фокус; reduced motion сохраняет управление", async ({
  page,
}) => {
  await server(page);
  await page.goto("/");
  const trigger = page
    .getByRole("button", { name: "Войти", exact: true })
    .first();
  await expect(trigger).toBeEnabled();
  for (const reducedMotion of ["no-preference", "reduce"] as const) {
    await page.emulateMedia({ reducedMotion });
    await trigger.click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Имя пользователя", { exact: true }).fill("alex");
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    await trigger.click();
    await expect(dialog).toBeVisible();
    await dialog
      .getByRole("button", { name: "Закрыть окно", exact: true })
      .click();
    await expect(dialog).toHaveCount(0);
  }
});

test("недоступная история чата показывает ошибку и не создаёт локальную переписку", async ({
  page,
}) => {
  const mutations = await server(page);
  await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, (route) =>
    route.fulfill({ status: 403, json: { message: "No permissions" } }),
  );
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await expect(chat.getByRole("alert")).toContainText("No permissions");
  await expect(chat.locator(".order-chat-header")).toContainText("Нет доступа");
  await expect(chat.locator(".order-chat-header")).not.toContainText(
    "Подключение…",
  );
  await expect(chat.getByLabel("Сообщение", { exact: true })).toBeDisabled();
  await expect(chat.locator(".chat-message")).toHaveCount(0);
  expect(mutations.filter((item) => item.path.endsWith("/messages"))).toEqual(
    [],
  );
});

test("отсутствующий API чата не оставляет статус подключения", async ({
  page,
}) => {
  let sockets = 0;
  await server(page, undefined, () => sockets++);
  await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, (route) =>
    route.fulfill({ status: 404, json: { message: "Not found" } }),
  );
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await expect(chat.locator(".order-chat-header")).toContainText("Недоступен");
  await expect(chat.getByRole("alert")).toContainText("Чат пока недоступен.");
  await expect(chat.getByLabel("Сообщение", { exact: true })).toBeDisabled();
  await expect(chat.getByRole("status")).toHaveCount(0);
  expect(sockets).toBe(0);
});

async function logout(page: Page) {
  await page.locator(".profile").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Выйти", exact: true })
    .click();
}

async function openAdminOrder(page: Page) {
  await page.goto("/");
  await login(page, "admin");
  await page.getByRole("button", { name: "Управление", exact: true }).click();
  await page
    .getByRole("region", { name: "Заказы и чаты площадки", exact: true })
    .getByRole("button", { name: "Открыть заказ", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Заказ #74", exact: true }),
  ).toBeVisible();
}

async function adminAction(page: Page, name: string) {
  await page
    .getByRole("region", { name: "Админ-функции заказа", exact: true })
    .getByRole("button", { name, exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name, exact: true })
    .click();
}

test("чат без WebSocket: REST догружает все страницы, сохраняет пропуски ID и не дублирует ответ POST", async ({
  page,
}, testInfo) => {
  await page.clock.install({ time: new Date("2026-10-18T12:00:00Z") });
  await page.clock.pauseAt(new Date("2026-10-18T12:00:01Z"));
  await server(page, undefined, (ws) => ws.close());
  const message = (
    id: number,
    text: string,
    sender = "seller",
  ): ChatMessage => ({
    id,
    orderId: 73,
    text,
    sender: { username: sender, userRole: "USER" },
    sentAt: "2026-10-18T12:00:00Z",
  });
  const history = [message(10, "Начало разговора")];
  const cursors: number[] = [];
  let reads = 0;
  await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, (route) => {
    const request = route.request();
    if (request.method() === "POST") {
      const saved = message(400, request.postDataJSON().text, "buyer");
      history.push(saved);
      return route.fulfill({ status: 201, json: saved });
    }
    reads++;
    const after = new URL(request.url()).searchParams.get("afterId");
    if (after === null)
      return route.fulfill({ json: { items: history, hasMore: false } });
    cursors.push(Number(after));
    const items = history.filter((item) => item.id > Number(after));
    return route.fulfill({
      json: { items: items.slice(0, 2), hasMore: items.length > 2 },
    });
  });
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await expect(chat).toContainText("Начало разговора");
  await expect(chat.locator(".order-chat-header")).toContainText(
    "Автообновление",
  );
  await expect(chat).not.toContainText("Переподключение");
  history.push(
    message(100, "Сообщение продавца"),
    message(250, "Ещё одно сообщение"),
  );
  await chat.getByLabel("Сообщение", { exact: true }).fill("Ответ покупателя");
  await chat
    .getByRole("button", { name: "Отправить сообщение", exact: true })
    .click();
  await expect(chat.getByRole("log")).toContainText("Ответ покупателя");
  await expect(chat.getByLabel("Сообщение", { exact: true })).toHaveValue("");
  await page.clock.fastForward(3000);
  await expect(chat.locator(".chat-message p")).toHaveText([
    "Начало разговора",
    "Сообщение продавца",
    "Ещё одно сообщение",
    "Ответ покупателя",
  ]);
  expect(cursors).toContain(10);
  expect(cursors).toContain(250);
  await expect(chat.getByRole("alert")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Закрыть уведомление", exact: true })
    .click();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("chat-rest-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 320, height: 844 });
  await page.clock.runFor(400);
  await expect(page.locator(".sidebar")).toBeHidden();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("chat-rest-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Открыть меню", exact: true }).click();
  await page.getByRole("button", { name: /Мои заказы/ }).click();
  const readsAfterLeaving = reads;
  await page.clock.fastForward(30_000);
  expect(reads).toBe(readsAfterLeaving);
});

test("чат: временная ошибка REST сохраняет переписку и автоматически восстанавливается", async ({
  page,
}) => {
  await page.clock.install({ time: new Date("2026-10-18T12:00:00Z") });
  await page.clock.pauseAt(new Date("2026-10-18T12:00:01Z"));
  await server(page, undefined, (ws) => ws.close());
  let unavailable = false;
  await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, (route) => {
    if (unavailable) return route.fulfill({ status: 503 });
    return route.fallback();
  });
  await page.goto("/");
  await login(page);
  await selectGame(page);
  await purchase(page);
  const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
  await chat
    .getByLabel("Сообщение", { exact: true })
    .fill("Сохранённое сообщение");
  await chat
    .getByRole("button", { name: "Отправить сообщение", exact: true })
    .click();
  await expect(chat.getByRole("log")).toContainText("Сохранённое сообщение");
  await expect(chat.getByLabel("Сообщение", { exact: true })).toHaveValue("");
  unavailable = true;
  await page.clock.fastForward(3000);
  await expect(chat.getByRole("alert")).toContainText(
    "Сервер не смог выполнить запрос",
  );
  await expect(chat.getByRole("log")).toContainText("Сохранённое сообщение");
  unavailable = false;
  await page.clock.fastForward(3000);
  await expect(chat.getByRole("alert")).toHaveCount(0);
  await expect(chat.getByLabel("Сообщение", { exact: true })).toBeEnabled();
  await expect(chat.locator(".chat-message")).toHaveCount(1);
});

for (const [status, label] of [
  [401, "Требуется вход"],
  [403, "Нет доступа"],
  [404, "Недоступен"],
] as const) {
  test(`чат: потеря доступа (${status}) останавливает REST и переподключения`, async ({
    page,
  }) => {
    await page.clock.install({ time: new Date("2026-10-18T12:00:00Z") });
    await page.clock.pauseAt(new Date("2026-10-18T12:00:01Z"));
    let connections = 0;
    await server(page, undefined, (ws) => {
      connections++;
      ws.close();
    });
    let denied = false;
    let reads = 0;
    await page.route(/\/api\/orders\/73\/messages(?:\?|$)/, (route) => {
      reads++;
      if (denied)
        return route.fulfill({ status, json: { message: "Нет доступа" } });
      return route.fallback();
    });
    await page.goto("/");
    await login(page);
    await selectGame(page);
    await purchase(page);
    const chat = page.getByRole("region", { name: "Чат заказа", exact: true });
    await expect(chat.getByLabel("Сообщение", { exact: true })).toBeEnabled();
    denied = true;
    await page.clock.fastForward(3000);
    await expect(chat.locator(".order-chat-header")).toContainText(label);
    await expect(chat.getByLabel("Сообщение", { exact: true })).toBeDisabled();
    const stopped = { connections, reads };
    await page.clock.fastForward(30_000);
    expect({ connections, reads }).toEqual(stopped);
  });
}

for (const status of ["CLOSED", "REFUND"] as const) {
  test(`чат после ${status}: покупатель и продавец могут продолжить переписку`, async ({
    page,
  }) => {
    await server(page);
    await page.goto("/");
    await login(page);
    await selectGame(page);
    await purchase(page);
    if (status === "REFUND") {
      await logout(page);
      await login(page, "seller");
      await page.getByRole("button", { name: /Мои заказы/ }).click();
      await page.locator(".order-row").click();
    }
    await page
      .getByRole("button", {
        name: status === "CLOSED" ? "Подтвердить получение" : "Отменить заказ",
        exact: true,
      })
      .click();
    const send = async (text: string) => {
      const chat = page.getByRole("region", {
        name: "Чат заказа",
        exact: true,
      });
      await chat.getByLabel("Сообщение", { exact: true }).fill(text);
      await chat
        .getByRole("button", { name: "Отправить сообщение", exact: true })
        .click();
      await expect(chat.getByRole("log")).toContainText(text);
    };
    await send("Можно уточнить детали после сделки?");
    await logout(page);
    await login(page, status === "CLOSED" ? "seller" : "buyer");
    await page.getByRole("button", { name: /Мои заказы/ }).click();
    await page.locator(".order-row").click();
    await expect(page.getByRole("log")).toContainText(
      "Можно уточнить детали после сделки?",
    );
    await send("Да, конечно");
    await expect(page.locator(".order-status")).toHaveText(
      status === "CLOSED" ? "Закрыт" : "Отменён",
    );
    await expect(
      page.getByRole("region", { name: "Админ-функции заказа", exact: true }),
    ).toHaveCount(0);
  });
}

test("администратор: отдельные действия, повторное открытие, подтверждение и возврат", async ({
  page,
}, testInfo) => {
  const mutations = await server(page);
  await openAdminOrder(page);
  const workspace = page.getByRole("region", {
    name: "Заказ #74",
    exact: true,
  });
  const controls = workspace.getByRole("region", {
    name: "Админ-функции заказа",
    exact: true,
  });
  await expect(
    workspace.getByRole("button", {
      name: "Подтвердить получение",
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(
    workspace.getByRole("button", { name: "Отменить заказ", exact: true }),
  ).toHaveCount(0);
  await adminAction(page, "Подтвердить заказ");
  await expect(workspace.locator(".order-status")).toHaveText("Закрыт");
  await expect(
    controls.getByRole("button", { name: "Открыть заказ", exact: true }),
  ).toBeVisible();
  await expect(
    controls.getByRole("button", { name: "Вернуть деньги", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Закрыть уведомление", exact: true })
    .click();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("admin-closed-order-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await controls
    .getByRole("button", { name: "Открыть заказ", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Закрыть окно", exact: true })
    .click();
  expect(
    mutations.filter((item) => item.path.endsWith("/reopen")),
  ).toHaveLength(0);
  await adminAction(page, "Открыть заказ");
  await expect(workspace.locator(".order-status")).toHaveText("Открыт");
  await expect(
    controls.getByRole("button", { name: "Подтвердить заказ", exact: true }),
  ).toBeVisible();
  await expect(
    controls.getByRole("button", { name: "Вернуть деньги", exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".sidebar")).toBeHidden();
  await page
    .getByRole("button", { name: "Закрыть уведомление", exact: true })
    .click();
  await page.evaluate(() => window.scrollTo(0, 0));
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("admin-reopened-order-mobile.png"),
    fullPage: true,
    animations: "disabled",
  });
  await adminAction(page, "Вернуть деньги");
  await expect(workspace.locator(".order-status")).toHaveText("Отменён");
  await expect(controls).toHaveCount(0);
  const actions = mutations.filter((item) =>
    item.path.startsWith("/api/admin/orders/"),
  );
  expect(actions.map((item) => item.path)).toEqual([
    "/api/admin/orders/74/confirm",
    "/api/admin/orders/74/reopen",
    "/api/admin/orders/74/refund",
  ]);
  for (const request of actions) {
    expect(request.method).toBe("PATCH");
    expect(request.csrf).toBe("test-token");
    expect(request.authorization).toBeUndefined();
    expect(request.body).toBeNull();
  }
  expect(
    mutations.filter((item) =>
      /^\/api\/orders\/74\/(confirm|cancel)$/.test(item.path),
    ),
  ).toHaveLength(0);
});

for (const status of [400, 403, 404, 409, 500]) {
  test(`админ-действие: ошибка ${status} не меняет статус и оставляет возможность повтора`, async ({
    page,
  }) => {
    await server(page);
    let rejected = true;
    await page.route("**/api/admin/orders/74/confirm", (route) => {
      if (rejected)
        return route.fulfill({
          status,
          json: { message: "Операция отклонена" },
        });
      return route.fallback();
    });
    await openAdminOrder(page);
    await adminAction(page, "Подтвердить заказ");
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("alert")).toBeVisible();
    await expect(page.locator(".order-status")).toHaveText("Открыт");
    await expect(
      dialog.getByRole("button", { name: "Подтвердить заказ", exact: true }),
    ).toBeEnabled();
    rejected = false;
    await dialog
      .getByRole("button", { name: "Подтвердить заказ", exact: true })
      .click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator(".order-status")).toHaveText("Закрыт");
  });
}

test("успешное админ-действие не объявляется неудачным при ошибке последующего GET", async ({
  page,
}) => {
  await server(page);
  let reads = 0;
  await page.route("**/api/orders/74", (route) => {
    reads++;
    if (reads > 1) return route.fulfill({ status: 503 });
    return route.fallback();
  });
  await openAdminOrder(page);
  await adminAction(page, "Подтвердить заказ");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".order-status")).toHaveText("Закрыт");
  await expect(page.getByRole("alert")).toContainText(
    "Действие выполнено. Не удалось обновить данные заказа.",
  );
  await expect(page.locator(".toast")).toContainText("Заказ подтверждён");
});
