import { useState } from "react";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  ChevronRight,
  Package,
} from "lucide-react";
import type { Account, Order } from "./types";
import { Empty } from "./components";
import { money } from "./storage";
import Username from "./Username";
import { Picture } from "./Media";

export function orderKind(order: Order, name: string) {
  if (order.userBuyerDTO.username === name) return "purchase";
  if (order.adviceDTO.userSeller.username === name) return "sale";
  return null;
}

export function OrderKindBadge({ kind }: { kind: "purchase" | "sale" | null }) {
  if (!kind) return null;
  return (
    <span className={`order-kind order-kind-${kind}`}>
      {kind === "purchase" ? (
        <ArrowDownLeft size={13} />
      ) : (
        <ArrowUpRight size={13} />
      )}
      {kind === "purchase" ? "Покупка" : "Продажа"}
    </span>
  );
}

export function OrderStatusBadge({ status }: { status: Order["orderStatus"] }) {
  const labels = {
    OPENED: "Открыт",
    CLOSED: "Закрыт",
    REFUND: "Отменён",
    ARCHIVED: "В архиве",
  };
  if (!status || !(status in labels)) return null;
  return (
    <span className={`order-status order-status-${status}`}>
      {labels[status]}
    </span>
  );
}

export default function OrdersPage({
  account,
  orders,
  loading,
  error,
  busy,
  onOpen,
  onRetry,
  onLogin,
  onMarket,
}: {
  account: Account | null;
  orders: Order[];
  loading: boolean;
  error: string;
  busy: boolean;
  onOpen: (order: Order) => void;
  onRetry: () => void;
  onLogin: () => void;
  onMarket: () => void;
}) {
  const [filter, setFilter] = useState<"all" | "purchase" | "sale">("all");
  const mine = account
    ? orders.filter((order) => orderKind(order, account.name) !== null)
    : [];
  const purchases = mine.filter(
    (order) => orderKind(order, account?.name ?? "") === "purchase",
  );
  const sales = mine.filter(
    (order) => orderKind(order, account?.name ?? "") === "sale",
  );
  const visible =
    filter === "purchase" ? purchases : filter === "sale" ? sales : mine;
  return (
    <section className="content-page" aria-label="Мои заказы">
      <div className="section-heading">
        <div>
          <h1>
            Мои заказы<span className="heading-dot">.</span>
          </h1>
        </div>
        <button className="text-button" onClick={onMarket}>
          Перейти в маркет <ArrowUpRight size={16} />
        </button>
      </div>
      {account && (
        <div className="order-filters" role="group" aria-label="Тип заказов">
          {(
            [
              ["all", "Все заказы", mine.length],
              ["purchase", "Покупки", purchases.length],
              ["sale", "Продажи", sales.length],
            ] as const
          ).map(([value, label, count]) => (
            <button
              key={value}
              className={filter === value ? "selected" : ""}
              aria-pressed={filter === value}
              onClick={() => setFilter(value)}
            >
              {label}
              <span>{count}</span>
            </button>
          ))}
        </div>
      )}
      {!account ? (
        <Empty
          title="Войди, чтобы посмотреть заказы"
          action={
            <button className="button" onClick={onLogin}>
              Войти <ArrowRight size={16} />
            </button>
          }
        />
      ) : loading ? (
        <div className="orders-loading" role="status">
          Загружаем покупки и продажи…
        </div>
      ) : error ? (
        <Empty
          title="Не удалось загрузить заказы"
          description={error}
          action={
            <button className="button button-secondary" onClick={onRetry}>
              Попробовать снова
            </button>
          }
        />
      ) : !visible.length ? (
        <Empty
          title={
            filter === "sale"
              ? "Пока нет продаж"
              : filter === "purchase"
                ? "Пока нет покупок"
                : "Заказов нет"
          }
          action={
            <button className="button" onClick={onMarket}>
              Перейти в маркет <ArrowRight size={16} />
            </button>
          }
        />
      ) : (
        <div className="order-list">
          {visible.map((order) => {
            const kind = orderKind(order, account.name);
            const counterpart =
              kind === "purchase"
                ? order.adviceDTO.userSeller
                : order.userBuyerDTO;
            return (
              <button
                className="order-row"
                key={order.id}
                disabled={busy}
                onClick={() => onOpen(order)}
              >
                <span
                  className={`order-thumb game-theme-${order.adviceDTO.game.theme ?? order.id % 4}`}
                >
                  <Picture
                    src={order.adviceDTO.imageUrl}
                    alt={order.adviceDTO.name}
                    fallback={<Package size={25} strokeWidth={1.5} />}
                  />
                </span>
                <span className="order-row-main">
                  <span className="listing-subtitle">
                    ЗАКАЗ #{order.id} · {order.adviceDTO.game.name}
                  </span>
                  <strong>{order.adviceDTO.name}</strong>
                  <span className="order-participants">
                    {kind === "purchase" ? "Продавец" : "Покупатель"}:{" "}
                    <Username
                      user={counterpart}
                      account={account}
                      interactive={false}
                    />
                  </span>
                </span>
                <OrderKindBadge kind={kind} />
                <OrderStatusBadge status={order.orderStatus} />
                <strong className="order-price">{money(order.price)} ₽</strong>
                <ChevronRight size={18} />
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}
