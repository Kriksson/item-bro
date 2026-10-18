import type {
  Account,
  Advice,
  AdvicePage,
  Game,
  Marketplace,
  Order,
  OrderAction,
  UserProfile,
  PublicProfile,
  Review,
  PublicUser,
  ProfileFields,
  ChatMessage,
  MessagePage,
} from "./types";
import { validateImage } from "./Media";

export function validProfileFields(
  profile: Partial<ProfileFields> & { avatarUrl?: string | null },
): boolean {
  return (
    (profile.age == null ||
      (Number.isInteger(profile.age) &&
        profile.age >= 1 &&
        profile.age <= 120)) &&
    (profile.city == null ||
      (typeof profile.city === "string" && profile.city.length <= 100)) &&
    (profile.about == null ||
      (typeof profile.about === "string" && profile.about.length <= 1000)) &&
    (profile.avatarUrl == null || typeof profile.avatarUrl === "string")
  );
}

export function parseChatMessage(value: unknown, orderId: number): ChatMessage {
  if (!value || typeof value !== "object")
    throw new Error("Сервер вернул некорректное сообщение.");
  const message = value as ChatMessage;
  const messageType =
    message.messageType === undefined ? "USER" : message.messageType;
  if (
    !Number.isSafeInteger(message.id) ||
    message.id < 1 ||
    message.orderId !== orderId ||
    !message.sender ||
    typeof message.sender.username !== "string" ||
    !message.sender.username.trim() ||
    typeof message.text !== "string" ||
    !message.text.trim() ||
    typeof message.sentAt !== "string" ||
    !Number.isFinite(Date.parse(message.sentAt)) ||
    (messageType !== "USER" && messageType !== "SYSTEM") ||
    (messageType === "SYSTEM" &&
      (typeof message.eventType !== "string" || !message.eventType.trim())) ||
    (messageType === "USER" && message.eventType != null)
  )
    throw new Error("Сервер вернул некорректное сообщение.");
  return { ...message, messageType };
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

const messages: Record<string, string> = {
  "game in archive": "Эта игра в архиве.",
  "game not opened": "Игра уже находится в архиве.",
  "Game not found": "Игра не найдена. Проверь её ID.",
  "game not found": "Игра не найдена. Проверь её ID.",
  "game already created": "Игра с таким названием уже есть.",
  "Advice not found": "Объявление не найдено.",
  "Advice has been deleted": "Объявление уже в архиве.",
  "Insufficient balance": "На балансе недостаточно средств.",
  "Order not found": "Заказ не найден.",
  "order not open": "Этот заказ уже завершён или отменён.",
  "Feedback not created": "Сначала покупатель должен оставить отзыв.",
  "Limit advice": "Можно разместить не больше пяти активных объявлений.",
};

export class ApiMarketplace implements Marketplace {
  // Basic нужен только для первого запроса входа. Дальше используется HttpOnly cookie.
  private authorization: string | undefined;

  private async request<T>(
    path: string,
    method = "GET",
    body?: unknown,
    anonymous = false,
  ): Promise<T> {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Requested-With": "XMLHttpRequest",
    };
    if (!anonymous && this.authorization)
      headers.Authorization = this.authorization;
    if (method !== "GET") {
      const csrfResponse = await this.fetch("/csrf", {
        headers,
        credentials: "include",
        cache: "no-store",
      });
      if (!csrfResponse.ok)
        throw new ApiError(
          csrfResponse.status,
          "Не удалось получить защитный токен. Повтори запрос.",
        );
      const csrf = (await csrfResponse.json()) as {
        headerName: string;
        token: string;
      };
      if (!csrf.headerName || !csrf.token)
        throw new Error("Сервер вернул некорректный защитный токен.");
      headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined && !(body instanceof FormData))
      headers["Content-Type"] = "application/json";
    const response = await this.fetch(path, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      body:
        body instanceof FormData
          ? body
          : body === undefined
            ? undefined
            : JSON.stringify(body),
    });
    const data = (await response.json().catch(() => null)) as Record<
      string,
      unknown
    > | null;
    if (!response.ok) {
      const raw = typeof data?.message === "string" ? data.message : "";
      const fallback: Record<number, string> = {
        400: "Проверь введённые данные.",
        401: "Неверное имя или пароль. Войди заново.",
        403: "У этого аккаунта нет прав на действие.",
        404: "Запись не найдена.",
        409: "Данные изменились. Обнови страницу и повтори действие.",
      };
      throw new ApiError(
        response.status,
        messages[raw] ||
          (response.status < 500 && raw && !raw.includes("Exception")
            ? raw
            : "") ||
          fallback[response.status] ||
          "Сервер не смог выполнить запрос. Попробуй позже.",
      );
    }
    return data as T;
  }

  private async fetch(path: string, init: RequestInit) {
    try {
      return await fetch(path, {
        ...init,
        signal: AbortSignal.timeout(
          init.body instanceof FormData ? 60_000 : 15_000,
        ),
      });
    } catch {
      throw new Error(
        "Не удалось связаться с сервером. Проверь, что приложение запущено.",
      );
    }
  }

  private async profile(): Promise<Account> {
    const profile = await this.request<UserProfile>("/api/user/me");
    return this.accountFrom(profile);
  }
  private accountFrom(profile: UserProfile): Account {
    if (
      !profile ||
      typeof profile.name !== "string" ||
      !profile.name.trim() ||
      (profile.userRole !== "USER" && profile.userRole !== "ADMIN") ||
      typeof profile.balance !== "number" ||
      !Number.isFinite(profile.balance) ||
      !validProfileFields(profile)
    ) {
      throw new Error("Сервер вернул некорректный профиль пользователя.");
    }
    return {
      name: profile.name,
      role: profile.userRole,
      balance: profile.balance,
      avatarUrl: profile.avatarUrl ?? null,
      age: profile.age ?? null,
      city: profile.city ?? null,
      about: profile.about ?? null,
    };
  }

  async restoreSession(): Promise<Account | null> {
    try {
      return await this.profile();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null;
      throw error;
    }
  }

  async login(name: string, password: string): Promise<Account> {
    const bytes = new TextEncoder().encode(`${name}:${password}`);
    this.authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`;
    try {
      await this.profile();
    } finally {
      this.authorization = undefined;
    }
    // Проверяем сохранение входа на сервере: без Basic, только с cookie.
    const account = await this.restoreSession();
    if (!account)
      throw new Error(
        "Сервер не сохранил сессию после входа. Проверь настройки Spring Security.",
      );
    return account;
  }
  async register(name: string, password: string) {
    await this.request<UserProfile>(
      "/api/user",
      "POST",
      { name, password },
      true,
    );
    return this.login(name, password);
  }
  async logout(): Promise<void> {
    await this.request<void>("/api/logout", "POST");
    this.authorization = undefined;
  }
  async games() {
    const result = await this.request<Game[]>("/api/games");
    if (!Array.isArray(result))
      throw new Error("Сервер вернул список игр в неожиданном формате.");
    return result;
  }
  async orders() {
    try {
      const result = await this.request<Order[]>("/api/orders");
      if (!Array.isArray(result))
        throw new Error("Сервер вернул список заказов в неожиданном формате.");
      return result;
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status === 404 &&
        error.message === "Заказы не найдены"
      )
        return [];
      throw error;
    }
  }
  advices(gameId: number, page: number) {
    return this.request<AdvicePage>(
      `/api/advices?gameId=${gameId}&page=${page}`,
    );
  }
  createAdvice(name: string, game_id: number, price: number) {
    return this.request<Advice>("/api/advices", "POST", {
      name,
      game_id,
      price,
    });
  }
  archiveAdvice(id: number) {
    return this.request<void>(`/api/advices/${id}`, "DELETE");
  }
  createOrder(id: number) {
    return this.request<Order>("/api/orders/", "POST", { id });
  }
  order(id: number) {
    return this.request<Order>(`/api/orders/${id}`);
  }
  action(id: number, action: OrderAction) {
    if (action === "reopen" || action === "refund")
      return this.request<void>(`/api/admin/orders/${id}/${action}`, "PATCH");
    if (action === "admin-confirm")
      return this.request<void>(`/api/admin/orders/${id}/confirm`, "PATCH");
    return this.request<void>(`/api/orders/${id}/${action}`, "PATCH");
  }
  feedback(id: number, rating: number, feedbackText: string) {
    return this.request<void>(`/api/orders/${id}/feedback`, "PATCH", {
      rating,
      feedbackText,
    });
  }
  response(id: number, responseText: string) {
    return this.request<void>(`/api/orders/${id}/response`, "PATCH", {
      responseText,
    });
  }
  createGame(name: string) {
    return this.request<Game>("/api/games", "POST", { name });
  }
  archiveGame(id: number) {
    return this.request<void>(`/api/games/${id}/delete`, "PATCH");
  }
  userProfile(username: string) {
    return this.request<PublicProfile>(
      `/api/users/${encodeURIComponent(username)}`,
    );
  }
  private async list<T>(path: string): Promise<T[]> {
    const result = await this.request<T[]>(path);
    if (!Array.isArray(result))
      throw new Error("Сервер вернул список в неожиданном формате.");
    return result;
  }
  userAdvices(username: string) {
    return this.list<Advice>(
      `/api/users/${encodeURIComponent(username)}/advices`,
    );
  }
  async userReviews(username: string): Promise<Review[]> {
    const result = await this.list<unknown>(
      `/api/users/${encodeURIComponent(username)}/reviews`,
    );
    return result.map((value) => {
      const fail = () => {
        throw new Error("Сервер вернул отзыв в неожиданном формате.");
      };
      if (!value || typeof value !== "object" || Array.isArray(value))
        return fail();
      const item = value as Record<string, unknown>;
      const feedback =
        "feedbackText" in item ? item.feedbackText : item.feedback;
      const response =
        "sellerResponseText" in item
          ? item.sellerResponseText
          : item.sellerResponse;
      if (
        typeof item.rating !== "number" ||
        !Number.isInteger(item.rating) ||
        item.rating < 1 ||
        item.rating > 5 ||
        typeof feedback !== "string" ||
        (response != null && typeof response !== "string")
      )
        return fail();
      let userBuyer: PublicUser | undefined;
      if (item.userBuyer != null) {
        if (typeof item.userBuyer !== "object" || Array.isArray(item.userBuyer))
          return fail();
        const buyer = item.userBuyer as Record<string, unknown>;
        if (
          typeof buyer.username !== "string" ||
          !buyer.username.trim() ||
          (buyer.userRole !== undefined &&
            buyer.userRole !== "USER" &&
            buyer.userRole !== "ADMIN")
        )
          return fail();
        userBuyer = { username: buyer.username, userRole: buyer.userRole };
      }
      if (
        item.orderId !== undefined &&
        (typeof item.orderId !== "number" ||
          !Number.isSafeInteger(item.orderId) ||
          item.orderId < 1)
      )
        return fail();
      if (item.adviceName !== undefined && typeof item.adviceName !== "string")
        return fail();
      return {
        rating: item.rating,
        feedbackText: feedback,
        sellerResponseText: response == null ? null : response,
        userBuyer,
        orderId: item.orderId as number | undefined,
        adviceName: item.adviceName as string | undefined,
      };
    });
  }
  adminOrders() {
    return this.list<Order>("/api/admin/orders");
  }
  async updateProfile(fields: ProfileFields) {
    return this.accountFrom(
      await this.request<UserProfile>("/api/user/me", "PATCH", fields),
    );
  }
  private async upload<T>(path: string, file: File): Promise<T> {
    validateImage(file);
    const form = new FormData();
    form.append("file", file);
    return this.request<T>(path, "PUT", form);
  }
  async uploadAvatar(file: File) {
    return this.accountFrom(
      await this.upload<UserProfile>("/api/user/me/avatar", file),
    );
  }
  async removeAvatar() {
    return this.accountFrom(
      await this.request<UserProfile>("/api/user/me/avatar", "DELETE"),
    );
  }
  uploadGameImage(id: number, file: File) {
    return this.upload<Game>(`/api/games/${id}/image`, file);
  }
  removeGameImage(id: number) {
    return this.request<Game>(`/api/games/${id}/image`, "DELETE");
  }
  uploadAdviceImage(id: number, file: File) {
    return this.upload<Advice>(`/api/advices/${id}/image`, file);
  }
  removeAdviceImage(id: number) {
    return this.request<Advice>(`/api/advices/${id}/image`, "DELETE");
  }
  async messages(
    id: number,
    cursor: { beforeId?: number; afterId?: number } = {},
  ): Promise<MessagePage> {
    const params = new URLSearchParams({ limit: "50" });
    if (cursor.beforeId !== undefined)
      params.set("beforeId", String(cursor.beforeId));
    if (cursor.afterId !== undefined)
      params.set("afterId", String(cursor.afterId));
    const value = await this.request<MessagePage>(
      `/api/orders/${id}/messages?${params}`,
    );
    if (
      !value ||
      !Array.isArray(value.items) ||
      typeof value.hasMore !== "boolean"
    )
      throw new Error("Сервер вернул некорректную историю чата.");
    const items = value.items
      .map((item) => parseChatMessage(item, id))
      .sort((a, b) => a.id - b.id);
    return { items, hasMore: value.hasMore };
  }
  async sendMessage(id: number, text: string, clientMessageId: string) {
    return parseChatMessage(
      await this.request<unknown>(`/api/orders/${id}/messages`, "POST", {
        text,
        clientMessageId,
      }),
      id,
    );
  }
}
