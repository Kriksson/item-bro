import { useEffect, useState } from "react";
import {
  ArrowUpRight,
  ClipboardList,
  MessageSquare,
  RefreshCw,
  Search,
} from "lucide-react";
import type { Account, Marketplace, Order } from "./types";
import { Empty } from "./components";
import { OrderStatusBadge } from "./OrdersPage";
import Username from "./Username";
import { money } from "./storage";

export default function AdminTrades({
  client,
  account,
  busy,
  refresh,
  onOpen,
}: {
  client: Marketplace;
  account: Account;
  busy: boolean;
  refresh: number;
  onOpen: (order: Order, chat: boolean) => void;
}) {
  const [tab, setTab] = useState<"orders" | "chats">("orders");
  const [query, setQuery] = useState("");
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    setOrders([]);
    if (account.role !== "ADMIN") {
      setLoading(false);
      return;
    }
    void client
      .adminOrders()
      .then((result) => {
        if (active) setOrders(result);
      })
      .catch((reason) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : "Не удалось загрузить заказы.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [client, account.name, account.role, refresh, retry]);
  if (account.role !== "ADMIN") return null;
  const needle = query.trim().toLowerCase();
  const visible = orders.filter((order) =>
    `${order.adviceDTO.name} ${order.adviceDTO.game.name} ${order.userBuyerDTO.username} ${order.adviceDTO.userSeller.username} ${order.id}`
      .toLowerCase()
      .includes(needle),
  );
  return (
    <section className="admin-trades" aria-label="Заказы и чаты площадки">
      <div className="section-heading">
        <div>
          <h2>
            Заказы и чаты<span className="heading-dot">.</span>
          </h2>
        </div>
        <button
          className="icon-button"
          aria-label="Обновить все заказы"
          onClick={() => setRetry((value) => value + 1)}
        >
          <RefreshCw size={18} />
        </button>
      </div>
      <div className="admin-trades-toolbar">
        <div
          className="profile-tabs"
          role="group"
          aria-label="Разделы администрирования"
        >
          <button
            className={tab === "orders" ? "selected" : ""}
            aria-pressed={tab === "orders"}
            onClick={() => setTab("orders")}
          >
            <ClipboardList size={16} />
            Все заказы
          </button>
          <button
            className={tab === "chats" ? "selected" : ""}
            aria-pressed={tab === "chats"}
            onClick={() => setTab("chats")}
          >
            <MessageSquare size={16} />
            Все чаты
          </button>
        </div>
        <label className="search-field">
          <Search size={17} />
          <input
            aria-label="Поиск заказов по названию"
            placeholder="Название, участник или номер"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
      </div>
      {loading ? (
        <p role="status" className="orders-loading">
          Загружаем все заказы…
        </p>
      ) : error ? (
        <Empty
          title="Не удалось загрузить заказы площадки"
          description={error}
          action={
            <button
              className="button button-secondary"
              onClick={() => setRetry((value) => value + 1)}
            >
              Попробовать снова
            </button>
          }
        />
      ) : !visible.length ? (
        <Empty title={needle ? "Заказы не найдены" : "Заказов пока нет"} />
      ) : (
        <div className="admin-order-list">
          {visible.map((order) => (
            <article className="admin-order-card" key={order.id}>
              <div className="admin-order-main">
                <span className="listing-subtitle">
                  ЗАКАЗ #{order.id} · {order.adviceDTO.game.name}
                </span>
                <h3>{order.adviceDTO.name}</h3>
                <div className="admin-order-parties">
                  <span>
                    Покупатель:{" "}
                    <Username user={order.userBuyerDTO} account={account} />
                  </span>
                  <span>
                    Продавец:{" "}
                    <Username
                      user={order.adviceDTO.userSeller}
                      account={account}
                    />
                  </span>
                </div>
              </div>
              <OrderStatusBadge status={order.orderStatus} />
              <strong>{money(order.price)} ₽</strong>
              <button
                className="button button-secondary button-small"
                disabled={busy}
                onClick={() => onOpen(order, tab === "chats")}
              >
                {tab === "chats" ? "Открыть чат" : "Открыть заказ"}
                <ArrowUpRight size={15} />
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
