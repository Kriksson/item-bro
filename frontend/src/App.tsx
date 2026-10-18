import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Gamepad2,
  ImagePlus,
  LayoutGrid,
  LoaderCircle,
  LogOut,
  Menu,
  Plus,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Store,
  Trash2,
  Wallet,
  UserRound,
  X,
} from "lucide-react";
import type {
  Account,
  Advice,
  AdvicePage,
  Game,
  Order,
  OrderAction,
  Page,
} from "./types";
import { ApiMarketplace } from "./api";
import MarketPage from "./MarketPage";
import OrdersPage, { orderKind } from "./OrdersPage";
import OrderWorkspace from "./OrderWorkspace";
import { Logo, Modal } from "./components";
import { AdviceArtwork, Avatar, ImageEditor } from "./Media";
import ProfileSettings from "./ProfileSettings";
import { money, positiveId, requiredText } from "./storage";
import Username, { ProfileNavigation } from "./Username";
import ProfilePage from "./ProfilePage";
import AdminTrades from "./AdminTrades";
import SellerRating from "./SellerRating";

type Dialog =
  | {
      kind: "login" | "register" | "listing" | "settings" | "new-game";
    }
  | { kind: "buy" | "advice" | "archive-advice"; advice: Advice }
  | { kind: "archive-game" | "game-image"; game: Game };
const emptyPage: AdvicePage = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
};
const pageNames: Record<Page, string> = {
  market: "Маркет",
  orders: "Мои заказы",
  selling: "Мои объявления",
  admin: "Управление",
  order: "Заказ",
  profile: "Профиль",
};

function clearProfileAddress() {
  if (window.location.hash.startsWith("#/users/"))
    window.history.replaceState(
      null,
      "",
      window.location.pathname + window.location.search,
    );
}

export default function App() {
  const api = useRef(new ApiMarketplace());
  const client = api.current;
  const [account, setAccount] = useState<Account | null>(null);
  const [authChecking, setAuthChecking] = useState(true);
  const [sessionError, setSessionError] = useState("");
  const [sessionRetry, setSessionRetry] = useState(0);
  const [games, setGames] = useState<Game[]>([]);
  const [gamesLoading, setGamesLoading] = useState(true);
  const [gamesError, setGamesError] = useState("");
  const [gameId, setGameId] = useState<number | null>(null);
  const [page, setPage] = useState<Page>("market");
  const [pageNumber, setPageNumber] = useState(0);
  const [data, setData] = useState<AdvicePage>(emptyPage);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [orders, setOrders] = useState<Order[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [ordersError, setOrdersError] = useState("");
  const [activeOrder, setActiveOrder] = useState<Order | null>(null);
  const [profileName, setProfileName] = useState("");
  const [orderReturnPage, setOrderReturnPage] = useState<"orders" | "admin">(
    "orders",
  );
  const [focusChat, setFocusChat] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [notice, setNotice] = useState("");
  const [mobileNav, setMobileNav] = useState(false);
  const isAdmin = account?.role === "ADMIN";
  const [orderRevision, setOrderRevision] = useState(0);

  useEffect(() => {
    const restoreProfile = () => {
      const match = /^#\/users\/(.+)$/.exec(window.location.hash);
      if (!match) {
        if (!window.location.hash)
          setPage((previous) => (previous === "profile" ? "market" : previous));
        return;
      }
      try {
        const username = decodeURIComponent(match[1]);
        if (username) {
          setProfileName(username);
          setPage("profile");
          setDialog(null);
        }
      } catch {
        /* Некорректная ссылка не открывает профиль. */
      }
    };
    restoreProfile();
    window.addEventListener("hashchange", restoreProfile);
    return () => window.removeEventListener("hashchange", restoreProfile);
  }, []);

  const open = (next: Dialog) => {
    if (authChecking && (next.kind === "login" || next.kind === "register"))
      return;
    if (
      (next.kind === "new-game" ||
        next.kind === "archive-game" ||
        next.kind === "game-image") &&
      !isAdmin
    )
      return;
    setFormError("");
    setDialog(next);
  };
  const close = () => {
    if (!busy) {
      setDialog(null);
      setFormError("");
    }
  };
  const notify = useCallback((message: string) => setNotice(message), []);
  useEffect(() => {
    let active = true;
    setAuthChecking(true);
    setSessionError("");
    client
      .restoreSession()
      .then((result) => {
        if (active) setAccount(result);
      })
      .catch((error) => {
        if (active)
          setSessionError(
            error instanceof Error
              ? error.message
              : "Не удалось проверить сессию.",
          );
      })
      .finally(() => {
        if (active) setAuthChecking(false);
      });
    return () => {
      active = false;
    };
  }, [client, sessionRetry]);
  useEffect(() => {
    if (!notice) return;
    const timeout = setTimeout(() => setNotice(""), 5000);
    return () => clearTimeout(timeout);
  }, [notice]);

  useEffect(() => {
    let cancelled = false;
    setGamesLoading(true);
    setGamesError("");
    client
      .games()
      .then((result) => {
        if (!cancelled) setGames(result);
      })
      .catch((error) => {
        if (!cancelled) {
          setGames([]);
          setGamesError(error.message);
        }
      })
      .finally(() => {
        if (!cancelled) setGamesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, refresh]);

  useEffect(() => {
    let cancelled = false;
    setLoadError("");
    if (gameId === null) {
      setData(emptyPage);
      setLoading(false);
      return;
    }
    setLoading(true);
    client
      .advices(gameId, pageNumber)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((error) => {
        if (!cancelled) {
          setData(emptyPage);
          setLoadError(error.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, account?.name, gameId, pageNumber, refresh]);

  useEffect(() => {
    let cancelled = false;
    setOrdersError("");
    if (!account) {
      setOrders([]);
      setOrdersLoading(false);
      return;
    }
    setOrdersLoading(true);
    client
      .orders()
      .then((result) => {
        if (!cancelled) setOrders(result);
      })
      .catch((error) => {
        if (!cancelled) {
          setOrders([]);
          setOrdersError(error.message);
        }
      })
      .finally(() => {
        if (!cancelled) setOrdersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, account?.name, refresh]);

  useEffect(() => {
    if (!orderRevision || page !== "order" || !activeOrder || !account) return;
    let cancelled = false;
    const id = activeOrder.id;
    client
      .order(id)
      .then((fresh) => {
        if (cancelled) return;
        setActiveOrder((current) => (current?.id === id ? fresh : current));
        rememberOrder(fresh);
        updateBalance();
      })
      .catch(() => {
        if (!cancelled)
          setFormError(
            "Не удалось обновить данные заказа. Открой заказ повторно.",
          );
      });
    return () => {
      cancelled = true;
    };
  }, [orderRevision, client, page, account?.name, activeOrder?.id]);

  const rememberOrder = (order: Order) => {
    if (account && orderKind(order, account.name))
      setOrders((previous) => [
        order,
        ...previous.filter((item) => item.id !== order.id),
      ]);
  };
  const rememberGame = (game: Game) => {
    setGames((previous) => [
      ...previous.filter((item) => item.id !== game.id),
      game,
    ]);
    setRefresh((value) => value + 1);
  };
  const updateBalance = () => {
    setAccount((previous) =>
      previous ? { ...previous, balance: undefined } : previous,
    );
  };
  const work = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setFormError("");
    try {
      await action();
    } catch (error) {
      setFormError(
        error instanceof Error
          ? error.message
          : "Не удалось выполнить действие.",
      );
    } finally {
      setBusy(false);
    }
  };
  const navigate = (next: Page) => {
    if (next === "admin" && !isAdmin) return;
    setPage(next);
    if (next !== "profile") clearProfileAddress();
    if (next === "market" || next === "selling") setGameId(null);
    if (next === "market" || next === "orders")
      setRefresh((value) => value + 1);
    setMobileNav(false);
    setFormError("");
  };
  const openProfile = (username: string) => {
    if (busy) return;
    setProfileName(username);
    setPage("profile");
    setDialog(null);
    setFormError("");
    setMobileNav(false);
    window.location.hash = `/users/${encodeURIComponent(username)}`;
  };
  const chooseGame = (id: number) => {
    setGameId(id);
    setPageNumber(0);
  };
  const submit = (
    event: FormEvent<HTMLFormElement>,
    action: (form: FormData) => Promise<void>,
  ) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void work(() => action(form));
  };
  const buy = (advice: Advice) =>
    work(async () => {
      const order = await client.createOrder(advice.id);
      rememberOrder(order);
      updateBalance();
      setActiveOrder(order);
      setOrderReturnPage("orders");
      setFocusChat(false);
      navigate("order");
      setDialog(null);
      setRefresh((value) => value + 1);
      notify(`Заказ #${order.id} создан`);
    });
  const orderAction = (order: Order, action: OrderAction) =>
    work(async () => {
      await client.action(order.id, action);
      const statuses: Record<OrderAction, Order["orderStatus"]> = {
        confirm: "CLOSED",
        cancel: "REFUND",
        reopen: "OPENED",
        "admin-confirm": "CLOSED",
        refund: "REFUND",
      };
      const updated = { ...order, orderStatus: statuses[action] };
      rememberOrder(updated);
      updateBalance();
      setActiveOrder(updated);
      setRefresh((value) => value + 1);
      const notices: Record<OrderAction, string> = {
        confirm: "Получение подтверждено. Деньги переданы продавцу.",
        cancel: "Заказ отменён. Деньги возвращены покупателю.",
        reopen: "Заказ открыт снова.",
        "admin-confirm": "Заказ подтверждён. Деньги переданы продавцу.",
        refund: "Деньги возвращены покупателю.",
      };
      notify(notices[action]);
      try {
        const fresh = await client.order(order.id);
        rememberOrder(fresh);
        setActiveOrder(fresh);
      } catch {
        setFormError("Действие выполнено. Не удалось обновить данные заказа.");
      }
    });

  const openOrder = (order: Order, fromAdmin = false, chat = false) =>
    work(async () => {
      const fresh = await client.order(order.id);
      rememberOrder(fresh);
      setActiveOrder(fresh);
      setOrderReturnPage(fromAdmin ? "admin" : "orders");
      setFocusChat(chat);
      navigate("order");
    });

  return (
    <ProfileNavigation.Provider value={openProfile}>
      <div className="app-shell">
        {mobileNav && (
          <button
            className="nav-scrim"
            aria-label="Закрыть меню"
            onClick={() => setMobileNav(false)}
          />
        )}
        <aside
          id="main-navigation"
          className={`sidebar${mobileNav ? " sidebar-open" : ""}`}
        >
          <a
            href="#"
            className="brand"
            aria-label="ItemBro — на главную"
            onClick={(e) => {
              e.preventDefault();
              navigate("market");
            }}
          >
            <Logo />
          </a>
          <nav aria-label="Главное меню">
            <button
              className={`nav-item ${page === "market" ? "active" : ""}`}
              onClick={() => navigate("market")}
            >
              <LayoutGrid size={19} />
              Маркет
              <span className="nav-active-dot" />
            </button>
            <button
              className={`nav-item ${page === "orders" || page === "order" ? "active" : ""}`}
              onClick={() => navigate("orders")}
            >
              <ClipboardList size={19} />
              Мои заказы
              {orders.length > 0 && (
                <span className="nav-count">{orders.length}</span>
              )}
            </button>
            <button
              className={`nav-item ${page === "selling" ? "active" : ""}`}
              onClick={() => navigate("selling")}
            >
              <Store size={19} />
              Мои объявления
            </button>
            {account && (
              <button
                className={`nav-item ${page === "profile" && profileName === account.name ? "active" : ""}`}
                onClick={() => openProfile(account.name)}
              >
                <UserRound size={19} />
                Мой профиль
              </button>
            )}
          </nav>
          <div className="sidebar-rule" />
          {isAdmin && (
            <button
              className={`nav-item ${page === "admin" ? "active" : ""}`}
              onClick={() => navigate("admin")}
            >
              <SlidersHorizontal size={19} />
              Управление
            </button>
          )}
          <div className="sidebar-bottom">
            <button
              className="nav-item muted"
              onClick={() => open({ kind: "settings" })}
            >
              <Settings2 size={18} />
              Аккаунт
            </button>
          </div>
        </aside>

        <div className="workspace">
          <header className="topbar">
            <div className="topbar-left">
              <button
                className="icon-button mobile-menu"
                aria-label="Открыть меню"
                aria-controls="main-navigation"
                aria-expanded={mobileNav}
                onClick={() => setMobileNav(true)}
              >
                <Menu />
              </button>
              <a
                href="#"
                className="mobile-brand"
                aria-label="ItemBro — на главную"
                onClick={(event) => {
                  event.preventDefault();
                  navigate("market");
                }}
              >
                <Logo />
              </a>
              <span className="breadcrumb">
                Площадка <ChevronRight size={13} />{" "}
                <strong>{pageNames[page]}</strong>
              </span>
            </div>
            <div className="topbar-actions">
              <button
                className="server-badge"
                onClick={() => open({ kind: "settings" })}
              >
                <span className="small-dot" />
                Аккаунт
                <ChevronDown size={12} />
              </button>
              {account && account.balance !== undefined && (
                <div className="balance">
                  <Wallet size={17} />
                  <span>
                    {money(account.balance)} <span className="currency">₽</span>
                  </span>
                </div>
              )}
              <span className="topbar-divider" />
              {account ? (
                <button
                  className="profile"
                  aria-label={`Аккаунт ${account.name}`}
                  onClick={() => open({ kind: "settings" })}
                >
                  <Avatar
                    user={{
                      username: account.name,
                      avatarUrl: account.avatarUrl,
                    }}
                  />
                  <Username
                    user={{ username: account.name, userRole: account.role }}
                    interactive={false}
                  />
                  <ChevronDown size={13} />
                </button>
              ) : (
                <button
                  className="button button-small"
                  disabled={authChecking}
                  onClick={() => open({ kind: "login" })}
                >
                  {authChecking ? "Проверяем вход…" : "Войти"}{" "}
                  <ArrowUpRight size={15} />
                </button>
              )}
            </div>
          </header>

          <main>
            <div className="page-content" key={page}>
              {sessionError && (
                <div className="session-error" role="alert">
                  <p>{sessionError}</p>
                  <button
                    className="text-button"
                    disabled={authChecking}
                    onClick={() => setSessionRetry((value) => value + 1)}
                  >
                    Повторить проверку <ArrowRight size={15} />
                  </button>
                </div>
              )}
              {(page === "market" || page === "selling") && (
                <MarketPage
                  key={`${page}-${gameId ?? "games"}-${account?.name ?? "guest"}`}
                  kind={page}
                  account={account}
                  client={client}
                  games={games}
                  gamesLoading={gamesLoading}
                  gamesError={gamesError}
                  gameId={gameId}
                  data={data}
                  loading={loading}
                  error={loadError}
                  pageNumber={pageNumber}
                  onChooseGame={chooseGame}
                  onBack={() => {
                    setGameId(null);
                    setPageNumber(0);
                  }}
                  onRetry={() => setRefresh((value) => value + 1)}
                  onPage={setPageNumber}
                  onCreate={() =>
                    open(account ? { kind: "listing" } : { kind: "login" })
                  }
                  onBuy={(advice) =>
                    open(account ? { kind: "buy", advice } : { kind: "login" })
                  }
                  onArchive={(advice) =>
                    open({ kind: "archive-advice", advice })
                  }
                  onOpen={(advice) => open({ kind: "advice", advice })}
                />
              )}

              {page === "orders" && (
                <>
                  {formError && (
                    <p className="form-error" role="alert">
                      {formError}
                    </p>
                  )}
                  <OrdersPage
                    account={account}
                    orders={orders}
                    loading={ordersLoading}
                    error={ordersError}
                    busy={busy}
                    onOpen={(order) => void openOrder(order)}
                    onRetry={() => setRefresh((value) => value + 1)}
                    onLogin={() => open({ kind: "login" })}
                    onMarket={() => navigate("market")}
                  />
                </>
              )}

              {page === "order" && activeOrder && account && (
                <OrderWorkspace
                  order={activeOrder}
                  account={account}
                  client={client}
                  busy={busy}
                  error={formError}
                  onBack={() => navigate(orderReturnPage)}
                  backLabel={
                    orderReturnPage === "admin" ? "В управление" : "К заказам"
                  }
                  focusChat={focusChat}
                  onSystemEvent={() => setOrderRevision((value) => value + 1)}
                  onAction={(action) => orderAction(activeOrder, action)}
                  onFeedback={(rating, text) =>
                    work(async () => {
                      await client.feedback(
                        activeOrder.id,
                        rating,
                        requiredText(text),
                      );
                      const updated = await client.order(activeOrder.id);
                      rememberOrder(updated);
                      setActiveOrder(updated);
                      notify("Отзыв сохранён.");
                    })
                  }
                  onResponse={(text) =>
                    work(async () => {
                      await client.response(activeOrder.id, requiredText(text));
                      const updated = await client.order(activeOrder.id);
                      rememberOrder(updated);
                      setActiveOrder(updated);
                      notify("Ответ сохранён.");
                    })
                  }
                />
              )}

              {page === "profile" && profileName && (
                <ProfilePage
                  key={`${profileName}-${account?.name ?? "guest"}-${refresh}`}
                  username={profileName}
                  client={client}
                  account={account}
                  onBack={() => navigate("market")}
                  onOpenAdvice={(advice) => open({ kind: "advice", advice })}
                />
              )}

              {page === "admin" && isAdmin && (
                <section className="content-page">
                  <h1>
                    Управление<span className="heading-dot">.</span>
                  </h1>
                  <div className="admin-grid">
                    <div className="panel">
                      <Gamepad2 size={28} />
                      <h3>Каталог игр и сервисов</h3>
                      <button
                        className="button"
                        disabled={!account}
                        onClick={() => open({ kind: "new-game" })}
                      >
                        <Plus size={16} />
                        Создать категорию
                      </button>
                    </div>
                  </div>
                  {formError && !dialog && (
                    <p role="alert" className="form-error">
                      {formError}
                    </p>
                  )}
                  <h2 className="admin-section-title">Категории на площадке</h2>
                  <div className="managed-games">
                    {games.map((game) => (
                      <div className="managed-game" key={game.id}>
                        <Gamepad2 size={20} />
                        <span>
                          <strong>{game.name}</strong>
                          <small>ID {game.id}</small>
                        </span>
                        <button
                          className="icon-button"
                          aria-label={`Изображение категории ${game.name}`}
                          onClick={() => open({ kind: "game-image", game })}
                        >
                          <ImagePlus size={17} />
                        </button>
                        <button
                          className="icon-button"
                          aria-label={`Архивировать категорию ${game.name}`}
                          disabled={!account}
                          onClick={() => open({ kind: "archive-game", game })}
                        >
                          <Trash2 size={17} />
                        </button>
                      </div>
                    ))}
                  </div>
                  {account && (
                    <AdminTrades
                      client={client}
                      account={account}
                      busy={busy}
                      refresh={refresh}
                      onOpen={(order, chat) =>
                        void openOrder(order, true, chat)
                      }
                    />
                  )}
                </section>
              )}
            </div>
          </main>
        </div>

        {notice && (
          <div className="toast" role="status">
            <span>
              <Check size={17} />
            </span>
            {notice}
            <button
              className="icon-button"
              aria-label="Закрыть уведомление"
              onClick={() => setNotice("")}
            >
              <X size={16} />
            </button>
          </div>
        )}

        {dialog && (
          <Modal
            variant={dialog.kind}
            canClose={!busy}
            title={
              dialog.kind === "login"
                ? "Вход"
                : dialog.kind === "register"
                  ? "Регистрация"
                  : dialog.kind === "listing"
                    ? "Новое объявление"
                    : dialog.kind === "settings"
                      ? "Аккаунт"
                      : dialog.kind === "new-game"
                        ? "Новая категория"
                        : dialog.kind === "game-image"
                          ? dialog.game.name
                          : dialog.kind === "buy"
                            ? "Покупка"
                            : dialog.kind === "advice"
                              ? "Объявление"
                              : dialog.kind === "archive-advice"
                                ? "Архивировать объявление?"
                                : "Архивировать категорию?"
            }
            onClose={close}
          >
            <fieldset className="modal-body" disabled={busy}>
              {(dialog.kind === "login" || dialog.kind === "register") && (
                <form
                  onSubmit={(e) =>
                    submit(e, async (form) => {
                      const name = requiredText(form.get("name"));
                      const password = String(form.get("password"));
                      if (name.includes(":"))
                        throw new Error("Имя не должно содержать двоеточие.");
                      if (
                        dialog.kind === "register" &&
                        !/^[A-Za-z0-9!@#$%^&*()_+\-=]+$/.test(password)
                      )
                        throw new Error(
                          "Пароль: латинские буквы, цифры и символы !@#$%^&*()_+-=",
                        );
                      const result =
                        dialog.kind === "register"
                          ? await client.register(name, password)
                          : await client.login(name, password);
                      setOrders([]);
                      setAccount(result);
                      setSessionError("");
                      setPage("market");
                      clearProfileAddress();
                      setDialog(null);
                      setRefresh((value) => value + 1);
                      notify("Вход выполнен.");
                    })
                  }
                >
                  <label>
                    Имя пользователя
                    <input
                      name="name"
                      autoComplete="username"
                      required
                      maxLength={20}
                      placeholder="Твоё имя"
                    />
                  </label>
                  <label>
                    Пароль
                    <input
                      name="password"
                      type="password"
                      autoComplete={
                        dialog.kind === "register"
                          ? "new-password"
                          : "current-password"
                      }
                      required
                      minLength={dialog.kind === "register" ? 8 : 1}
                      maxLength={64}
                      placeholder={
                        dialog.kind === "register"
                          ? "От 8 символов"
                          : "Твой пароль"
                      }
                    />
                  </label>
                  <button className="button button-full">
                    {busy ? <LoaderCircle className="spin" size={17} /> : null}
                    {dialog.kind === "register" ? "Создать аккаунт" : "Войти"}
                    <ArrowRight size={17} />
                  </button>
                  <p className="form-switch">
                    {dialog.kind === "login"
                      ? "Пока нет аккаунта?"
                      : "Уже с нами?"}{" "}
                    <button
                      type="button"
                      onClick={() =>
                        open({
                          kind: dialog.kind === "login" ? "register" : "login",
                        })
                      }
                    >
                      {dialog.kind === "login" ? "Зарегистрироваться" : "Войти"}
                    </button>
                  </p>
                </form>
              )}

              {dialog.kind === "settings" && (
                <div className="settings-content">
                  <p className="form-intro">
                    {account ? (
                      <>
                        Аккаунт:{" "}
                        <Username
                          user={{
                            username: account.name,
                            userRole: account.role,
                          }}
                        />
                        .
                      </>
                    ) : (
                      "Вход не выполнен."
                    )}
                  </p>
                  {!account && (
                    <button
                      className="button button-full"
                      onClick={() => open({ kind: "login" })}
                    >
                      Войти в аккаунт
                    </button>
                  )}
                  {account && (
                    <ProfileSettings
                      account={account}
                      client={client}
                      onBusyChange={setBusy}
                      onUpdated={(updated) => {
                        setAccount(updated);
                        setRefresh((value) => value + 1);
                      }}
                    />
                  )}
                  {account && (
                    <button
                      className="button button-full"
                      onClick={() => openProfile(account.name)}
                    >
                      <UserRound size={17} />
                      Открыть мой профиль
                    </button>
                  )}
                  {account && (
                    <button
                      className="button button-secondary button-full"
                      onClick={() =>
                        void work(async () => {
                          await client.logout();
                          setAccount(null);
                          setOrders([]);
                          setActiveOrder(null);
                          setData(emptyPage);
                          setDialog(null);
                          setPage("market");
                          clearProfileAddress();
                          notify("Ты вышел из аккаунта.");
                        })
                      }
                    >
                      <LogOut size={16} />
                      Выйти
                    </button>
                  )}
                </div>
              )}

              {dialog.kind === "new-game" && (
                <form
                  onSubmit={(e) =>
                    submit(e, async (form) => {
                      const game = await client.createGame(
                        requiredText(form.get("name")),
                      );
                      rememberGame(game);
                      setDialog(null);
                      notify("Категория создана.");
                    })
                  }
                >
                  <label>
                    Название категории
                    <input
                      name="name"
                      required
                      maxLength={50}
                      placeholder="Например, Path of Exile"
                    />
                  </label>
                  <button className="button button-full">
                    Создать категорию <Plus size={16} />
                  </button>
                </form>
              )}

              {dialog.kind === "listing" && (
                <form
                  onSubmit={(e) =>
                    submit(e, async (form) => {
                      const price = Number(form.get("price"));
                      if (!Number.isFinite(price) || price < 1)
                        throw new Error("Цена должна быть не меньше 1.");
                      const advice = await client.createAdvice(
                        requiredText(form.get("name")),
                        positiveId(String(form.get("game"))),
                        price,
                      );
                      setGameId(advice.game.id);
                      setPageNumber(0);
                      setPage("selling");
                      setRefresh((value) => value + 1);
                      setDialog(null);
                      notify("Объявление опубликовано.");
                    })
                  }
                >
                  <p className="form-intro">До 5 активных объявлений.</p>
                  <label>
                    Категория
                    <select name="game" required defaultValue={gameId ?? ""}>
                      <option disabled value="">
                        Выбери категорию
                      </option>
                      {games.map((game) => (
                        <option key={game.id} value={game.id}>
                          {game.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Название объявления
                    <input
                      name="name"
                      required
                      maxLength={50}
                      placeholder="Что ты продаёшь?"
                    />
                  </label>
                  <label>
                    Цена, ₽
                    <input
                      name="price"
                      type="number"
                      required
                      min="1"
                      step="0.01"
                      placeholder="0"
                    />
                  </label>
                  <button
                    className="button button-full"
                    disabled={!games.length}
                  >
                    Опубликовать объявление <ArrowUpRight size={16} />
                  </button>
                  {!games.length && (
                    <p className="subtle-note">Нет доступных категорий.</p>
                  )}
                </form>
              )}

              {(dialog.kind === "buy" || dialog.kind === "advice") && (
                <>
                  <AdviceArtwork advice={dialog.advice} large />
                  <div className="purchase-heading">
                    <span>{dialog.advice.game.name}</span>
                    <h3>{dialog.advice.name}</h3>
                  </div>
                  <div className="summary-line">
                    <span>Продавец</span>
                    <strong className="seller-identity">
                      <Username
                        user={dialog.advice.userSeller}
                        account={account}
                      />
                      <SellerRating
                        client={client}
                        username={dialog.advice.userSeller.username}
                      />
                    </strong>
                  </div>
                  <div className="summary-line summary-total">
                    <span>К оплате</span>
                    <strong>{money(dialog.advice.price)} ₽</strong>
                  </div>
                  <p className="form-intro">
                    Оплата с баланса при создании заказа.
                  </p>
                  <button
                    className="button button-full"
                    disabled={
                      dialog.advice.userSeller.username === account?.name
                    }
                    onClick={() =>
                      account
                        ? void buy(dialog.advice)
                        : open({ kind: "login" })
                    }
                  >
                    {busy ? (
                      <LoaderCircle size={17} className="spin" />
                    ) : (
                      <CheckCheck size={17} />
                    )}
                    {dialog.advice.userSeller.username === account?.name
                      ? "Твоё объявление"
                      : account
                        ? "Создать заказ"
                        : "Войти для покупки"}
                  </button>
                  {(isAdmin ||
                    account?.name === dialog.advice.userSeller.username) && (
                    <ImageEditor
                      label="Изображение объявления"
                      showCurrent={false}
                      onBusyChange={setBusy}
                      currentUrl={dialog.advice.imageUrl}
                      onUpload={async (file) => {
                        const advice = await client.uploadAdviceImage(
                          dialog.advice.id,
                          file,
                        );
                        setDialog({ kind: "advice", advice });
                        setRefresh((value) => value + 1);
                      }}
                      onRemove={async () => {
                        const advice = await client.removeAdviceImage(
                          dialog.advice.id,
                        );
                        setDialog({ kind: "advice", advice });
                        setRefresh((value) => value + 1);
                      }}
                    />
                  )}
                  {isAdmin && (
                    <section
                      className="advice-admin-functions"
                      aria-label="Админ-функции"
                    >
                      <h3>
                        <ShieldCheck size={18} /> Админ-функции
                      </h3>
                      <button
                        className="button button-danger button-full"
                        onClick={() =>
                          open({
                            kind: "archive-advice",
                            advice: dialog.advice,
                          })
                        }
                      >
                        <Trash2 size={16} /> Удалить объявление
                      </button>
                    </section>
                  )}
                </>
              )}

              {dialog.kind === "archive-advice" && (
                <>
                  <p className="confirmation-name">{dialog.advice.name}</p>
                  <button
                    className="button button-danger button-full"
                    onClick={() =>
                      void work(async () => {
                        await client.archiveAdvice(dialog.advice.id);
                        setDialog(null);
                        setRefresh((value) => value + 1);
                        notify("Объявление отправлено в архив.");
                      })
                    }
                  >
                    <Trash2 size={16} />
                    Архивировать
                  </button>
                </>
              )}
              {dialog.kind === "game-image" && isAdmin && (
                <ImageEditor
                  label="Изображение категории"
                  onBusyChange={setBusy}
                  currentUrl={
                    games.find((game) => game.id === dialog.game.id)?.imageUrl
                  }
                  onUpload={async (file) =>
                    rememberGame(
                      await client.uploadGameImage(dialog.game.id, file),
                    )
                  }
                  onRemove={async () =>
                    rememberGame(await client.removeGameImage(dialog.game.id))
                  }
                />
              )}
              {dialog.kind === "archive-game" && (
                <>
                  <p className="confirmation-name">{dialog.game.name}</p>
                  <button
                    className="button button-danger button-full"
                    onClick={() =>
                      void work(async () => {
                        await client.archiveGame(dialog.game.id);
                        const next = games.filter(
                          (game) => game.id !== dialog.game.id,
                        );
                        setGames(next);
                        if (gameId === dialog.game.id) {
                          setGameId(null);
                          setPageNumber(0);
                        }
                        setDialog(null);
                        setRefresh((value) => value + 1);
                        notify("Категория отправлена в архив.");
                      })
                    }
                  >
                    <Trash2 size={16} />
                    Архивировать категорию
                  </button>
                </>
              )}
              {formError && (
                <p className="form-error" role="alert">
                  {formError}
                </p>
              )}
              {busy && (
                <div className="saving-status" role="status">
                  <LoaderCircle className="spin" size={15} />
                  Выполняем запрос…
                </div>
              )}
            </fieldset>
          </Modal>
        )}
      </div>
    </ProfileNavigation.Provider>
  );
}
