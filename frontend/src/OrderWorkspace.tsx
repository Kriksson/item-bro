import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  CheckCheck,
  ShieldCheck,
  Star,
} from "lucide-react";
import type { Account, Marketplace, Order, OrderAction } from "./types";
import { Modal } from "./components";
import { money } from "./storage";
import { OrderKindBadge, OrderStatusBadge, orderKind } from "./OrdersPage";
import Username from "./Username";
import OrderChat from "./OrderChat";

export default function OrderWorkspace({
  order,
  account,
  client,
  busy,
  error,
  onBack,
  onAction,
  onFeedback,
  onResponse,
  onSystemEvent,
  focusChat = false,
  backLabel = "К заказам",
}: {
  order: Order;
  account: Account;
  client: Marketplace;
  busy: boolean;
  error: string;
  onBack: () => void;
  onAction: (action: OrderAction) => Promise<void>;
  onFeedback: (rating: number, text: string) => Promise<void>;
  onResponse: (text: string) => Promise<void>;
  onSystemEvent: () => void;
  focusChat?: boolean;
  backLabel?: string;
}) {
  const [adminAction, setAdminAction] = useState<
    "reopen" | "admin-confirm" | "refund" | null
  >(null);
  useEffect(() => setAdminAction(null), [order.id, order.orderStatus]);
  useEffect(() => {
    if (focusChat)
      document.getElementById("order-chat")?.scrollIntoView({ block: "start" });
  }, [focusChat, order.id]);
  const kind = orderKind(order, account.name);
  const isAdmin = account.role === "ADMIN";
  const canReview = kind === "purchase" || isAdmin;
  const canRespond = kind === "sale" || isAdmin;
  const isOpen = order.orderStatus === "OPENED";
  return (
    <section className="order-workspace" aria-label={`Заказ #${order.id}`}>
      <button className="text-button back-link" onClick={onBack}>
        <ArrowLeft size={17} />
        {backLabel}
      </button>
      <div className="order-workspace-heading">
        <div>
          <h1>
            Заказ #{order.id}
            <span className="heading-dot">.</span>
          </h1>
          <span className="order-game-name">{order.adviceDTO.game.name}</span>
        </div>
        <div className="order-heading-badges">
          <OrderKindBadge kind={kind} />
          <OrderStatusBadge status={order.orderStatus} />
        </div>
      </div>
      <div className="order-workspace-grid">
        <aside className="order-summary-panel" aria-label="Действия и отзыв">
          <fieldset disabled={busy} className="order-summary-fields">
            <div className="order-summary-product">
              <h2>{order.adviceDTO.name}</h2>
              <strong>
                {money(order.price)} <span>₽</span>
              </strong>
            </div>
            <div className="order-parties">
              <div>
                <small>Покупатель</small>
                <strong>
                  <Username user={order.userBuyerDTO} account={account} />
                </strong>
              </div>
              <ArrowRight size={18} />
              <div>
                <small>Продавец</small>
                <strong>
                  <Username
                    user={order.adviceDTO.userSeller}
                    account={account}
                  />
                </strong>
              </div>
            </div>
            {isOpen && !isAdmin && (
              <div className="order-actions">
                {canReview && (
                  <>
                    <button
                      className="button button-full"
                      onClick={() => void onAction("confirm")}
                    >
                      <CheckCheck size={17} />
                      Подтвердить получение
                    </button>
                    <p className="order-action-hint">
                      После подтверждения деньги получит продавец.
                    </p>
                  </>
                )}
                {canRespond && (
                  <button
                    className="button button-secondary button-full"
                    onClick={() => void onAction("cancel")}
                  >
                    Отменить заказ
                  </button>
                )}
              </div>
            )}
            {isAdmin && (isOpen || order.orderStatus === "CLOSED") && (
              <section
                className="advice-admin-functions"
                aria-label="Админ-функции заказа"
              >
                <h3>
                  <ShieldCheck size={17} />
                  Админ-функции
                </h3>
                {order.orderStatus === "CLOSED" ? (
                  <button
                    className="button button-secondary button-full"
                    onClick={() => setAdminAction("reopen")}
                  >
                    Открыть заказ
                  </button>
                ) : (
                  <div className="order-actions">
                    <button
                      className="button button-full"
                      onClick={() => setAdminAction("admin-confirm")}
                    >
                      Подтвердить заказ
                    </button>
                    <button
                      className="button button-secondary button-full"
                      onClick={() => setAdminAction("refund")}
                    >
                      Вернуть деньги
                    </button>
                  </div>
                )}
              </section>
            )}
            {order.orderStatus === "CLOSED" && (
              <p className="order-terminal-note">
                <CheckCheck size={17} />
                Сделка завершена
              </p>
            )}
            {order.orderStatus === "REFUND" && (
              <p className="order-terminal-note">
                Заказ отменён, средства возвращены покупателю.
              </p>
            )}
            {error && !adminAction && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            {order.feedbackText && (
              <div className="review-block">
                <strong>
                  <Username user={order.userBuyerDTO} account={account} />
                </strong>
                <div
                  className="review-stars"
                  aria-label={`Оценка ${order.rating} из 5`}
                >
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Star
                      size={15}
                      key={star}
                      fill={
                        star <= (order.rating ?? 0) ? "currentColor" : "none"
                      }
                    />
                  ))}
                </div>
                <p>{order.feedbackText}</p>
                {order.sellerResponseText && (
                  <blockquote>
                    <small>Ответ продавца</small>
                    <strong>
                      <Username
                        user={order.adviceDTO.userSeller}
                        account={account}
                      />
                    </strong>
                    <p>{order.sellerResponseText}</p>
                  </blockquote>
                )}
              </div>
            )}
            {canReview && (
              <form
                className="review-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  const form = new FormData(event.currentTarget);
                  void onFeedback(
                    Number(form.get("rating")),
                    String(form.get("text")),
                  );
                }}
              >
                <h3>
                  {order.feedbackText ? "Изменить отзыв" : "Отзыв о сделке"}
                </h3>
                <label>
                  Оценка
                  <select
                    name="rating"
                    defaultValue={
                      order.rating && order.rating > 0 ? order.rating : 5
                    }
                  >
                    {[5, 4, 3, 2, 1].map((rating) => (
                      <option value={rating} key={rating}>
                        {rating} из 5
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Отзыв
                  <textarea
                    key={`feedback-${order.id}-${order.feedbackText}`}
                    name="text"
                    required
                    maxLength={300}
                    rows={3}
                    placeholder="Как всё прошло?"
                    defaultValue={order.feedbackText ?? ""}
                  />
                </label>
                <button className="button button-secondary button-full">
                  Сохранить отзыв
                </button>
              </form>
            )}
            {order.feedbackText && canRespond && (
              <form
                className="review-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  const form = new FormData(event.currentTarget);
                  void onResponse(String(form.get("text")));
                }}
              >
                <label>
                  Ответ продавца
                  <textarea
                    key={`response-${order.id}-${order.sellerResponseText}`}
                    name="text"
                    required
                    maxLength={300}
                    rows={3}
                    placeholder="Ответь покупателю"
                    defaultValue={order.sellerResponseText ?? ""}
                  />
                </label>
                <button className="button button-secondary button-full">
                  Сохранить ответ
                </button>
              </form>
            )}
            {busy && (
              <p className="saving-status" role="status">
                Сохраняем изменения…
              </p>
            )}
          </fieldset>
        </aside>
        <OrderChat
          key={order.id}
          order={order}
          account={account}
          client={client}
          onSystemEvent={onSystemEvent}
        />
      </div>
      {adminAction && (
        <Modal
          title={
            adminAction === "reopen"
              ? "Открыть заказ?"
              : adminAction === "refund"
                ? "Вернуть деньги?"
                : "Подтвердить заказ?"
          }
          canClose={!busy}
          onClose={() => setAdminAction(null)}
        >
          <p className="confirmation-name">
            Заказ #{order.id} · {money(order.price)} ₽
          </p>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button
            className="button button-full"
            disabled={busy}
            onClick={() => void onAction(adminAction)}
          >
            {adminAction === "reopen"
              ? "Открыть заказ"
              : adminAction === "refund"
                ? "Вернуть деньги"
                : "Подтвердить заказ"}
          </button>
        </Modal>
      )}
    </section>
  );
}
