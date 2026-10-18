import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { LoaderCircle, MessageSquare, Send } from "lucide-react";
import type { Account, ChatMessage, Marketplace, Order } from "./types";
import { ApiError, parseChatMessage } from "./api";
import Username from "./Username";
import { Avatar } from "./Media";

const time = new Intl.DateTimeFormat("ru-RU", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});
export default function OrderChat({
  order,
  account,
  client,
}: {
  order: Order;
  account: Account;
  client: Marketplace;
}) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [olderBusy, setOlderBusy] = useState(false);
  const [hasOlder, setHasOlder] = useState(false);
  const [error, setError] = useState("");
  const [sendError, setSendError] = useState("");
  const [status, setStatus] = useState("Загрузка истории…");
  const [retry, setRetry] = useState(0);
  const list = useRef<HTMLDivElement>(null);
  const following = useRef(true);
  const anchor = useRef<{ height: number; top: number } | null>(null);
  const firstId = useRef(0);
  const pending = useRef<{ text: string; id: string } | null>(null);
  const active = useRef(true);
  const counterpart =
    order.userBuyerDTO.username === account.name
      ? order.adviceDTO.userSeller
      : order.userBuyerDTO;
  const observer =
    account.role === "ADMIN" &&
    account.name !== order.userBuyerDTO.username &&
    account.name !== order.adviceDTO.userSeller.username;
  const merge = (items: ChatMessage[]) => {
    if (!active.current || !items.length) return;
    firstId.current = firstId.current
      ? Math.min(firstId.current, ...items.map((item) => item.id))
      : Math.min(...items.map((item) => item.id));
    setMessages((previous) =>
      [
        ...new Map(
          [...previous, ...items].map((item) => [item.id, item]),
        ).values(),
      ].sort((a, b) => a.id - b.id),
    );
  };
  useLayoutEffect(() => {
    if (!list.current) return;
    if (anchor.current) {
      list.current.scrollTop =
        anchor.current.top + list.current.scrollHeight - anchor.current.height;
      anchor.current = null;
    } else if (following.current)
      list.current.scrollTop = list.current.scrollHeight;
  }, [messages]);
  useEffect(() => {
    active.current = true;
    let stopped = false;
    let socket: WebSocket | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let initialized = false;
    let historyError = false;
    let syncing: Promise<void> | undefined;
    let failures = 0;
    // Только прочитанная история двигает курсор. Ответ на POST во время
    // разрыва может иметь больший ID, чем ещё не полученные чужие сообщения.
    let syncedId = 0;
    firstId.current = 0;
    following.current = true;
    setMessages([]);
    setError("");
    setLoading(true);
    setHasOlder(false);
    setStatus("Загрузка истории…");
    const report = (reason: unknown) => {
      if (stopped) return;
      historyError = true;
      setStatus("Недоступен");
      setError(
        reason instanceof Error ? reason.message : "Не удалось загрузить чат.",
      );
      if (reason instanceof ApiError) {
        if (reason.status === 401) setStatus("Требуется вход");
        if (reason.status === 403) setStatus("Нет доступа");
        if (reason.status === 404) setError("Чат пока недоступен.");
      }
      if (
        reason instanceof ApiError &&
        [401, 403, 404].includes(reason.status)
      ) {
        stopped = true;
        clearTimeout(timer);
        socket?.close();
      }
    };
    const sync = async (cursor: number) => {
      let next = cursor;
      while (!stopped) {
        const page = await client.messages(order.id, { afterId: next });
        if (stopped) return;
        merge(page.items);
        const newest = page.items.at(-1)?.id;
        if (newest) syncedId = Math.max(syncedId, newest);
        if (!page.hasMore) return;
        if (!newest || newest <= next)
          throw new Error("Сервер вернул некорректную страницу чата.");
        next = newest;
      }
    };
    const refreshHistory = () => {
      if (stopped || !initialized) return Promise.resolve();
      if (syncing) return syncing;
      syncing = sync(syncedId)
        .then(() => {
          if (stopped) return;
          historyError = false;
          setError("");
          setStatus(
            socket?.readyState === WebSocket.OPEN
              ? "Подключён"
              : "Автообновление",
          );
        })
        .catch(report)
        .finally(() => {
          syncing = undefined;
        });
      return syncing;
    };
    const connect = () => {
      if (stopped) return;
      const url = new URL(
        `/api/orders/${order.id}/chat/ws`,
        window.location.href,
      );
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      try {
        socket = new WebSocket(url);
      } catch {
        if (!historyError) setStatus("Автообновление");
        return;
      }
      socket.onopen = () => {
        if (stopped) return;
        failures = 0;
        if (!historyError) setStatus("Подключён");
        void refreshHistory();
      };
      socket.onmessage = (event) => {
        if (stopped) return;
        try {
          const frame = JSON.parse(String(event.data));
          if (frame.type !== "message.created") return;
          merge([parseChatMessage(frame.message, order.id)]);
        } catch {
          setSendError("Не удалось прочитать новое сообщение. Обнови чат.");
        }
      };
      socket.onerror = () => {
        if (!stopped && !historyError) setStatus("Автообновление");
      };
      socket.onclose = () => {
        if (stopped) return;
        if (!historyError) setStatus("Автообновление");
        if (!document.hidden) void refreshHistory();
        timer = setTimeout(connect, Math.min(1000 * 2 ** failures++, 15000));
      };
    };
    // Пока сокета нет или он оборвался, новые сообщения приходят через REST.
    // Один запрос синхронизации за раз сохраняет порядок движения курсора.
    const poll = setInterval(() => {
      if (!document.hidden && socket?.readyState !== WebSocket.OPEN)
        void refreshHistory();
    }, 3000);
    const resume = () => {
      if (!document.hidden) void refreshHistory();
    };
    document.addEventListener("visibilitychange", resume);
    window.addEventListener("online", resume);
    void client
      .messages(order.id)
      .then((page) => {
        if (stopped) return;
        merge(page.items);
        syncedId = page.items.at(-1)?.id ?? 0;
        setHasOlder(page.hasMore);
        setLoading(false);
        initialized = true;
        setStatus("Автообновление");
        connect();
      })
      .catch((reason) => {
        report(reason);
        if (active.current) setLoading(false);
      });
    return () => {
      stopped = true;
      active.current = false;
      clearTimeout(timer);
      clearInterval(poll);
      document.removeEventListener("visibilitychange", resume);
      window.removeEventListener("online", resume);
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
    };
  }, [client, order.id, retry]);
  return (
    <section id="order-chat" className="order-chat" aria-label="Чат заказа">
      <header className="order-chat-header">
        <Avatar user={counterpart} className="chat-avatar" />
        <span>
          <strong>
            {observer ? (
              <>
                <Username user={order.userBuyerDTO} account={account} /> ↔{" "}
                <Username user={order.adviceDTO.userSeller} account={account} />
              </>
            ) : (
              <Username user={counterpart} account={account} />
            )}
          </strong>
          <small>
            {observer ? "Просмотр администратором · " : ""}
            {status}
          </small>
        </span>
        <button
          type="button"
          className="text-button chat-refresh"
          onClick={() => setRetry((value) => value + 1)}
        >
          Обновить
        </button>
      </header>
      {error && (
        <div className="chat-load-error" role="alert">
          <p>{error}</p>
          <button
            type="button"
            className="button button-secondary button-small"
            onClick={() => setRetry((value) => value + 1)}
          >
            Повторить
          </button>
        </div>
      )}
      <div
        className="chat-messages"
        ref={list}
        role="log"
        aria-label="Сообщения заказа"
        aria-live="polite"
        onScroll={() => {
          if (list.current)
            following.current =
              list.current.scrollHeight -
                list.current.scrollTop -
                list.current.clientHeight <
              80;
        }}
      >
        {loading ? <p role="status">Загружаем сообщения…</p> : null}
        {hasOlder && !error && (
          <button
            type="button"
            className="text-button chat-load-older"
            disabled={olderBusy}
            onClick={() => {
              setOlderBusy(true);
              setSendError("");
              void client
                .messages(order.id, { beforeId: firstId.current })
                .then((page) => {
                  if (!active.current) return;
                  if (list.current)
                    anchor.current = {
                      height: list.current.scrollHeight,
                      top: list.current.scrollTop,
                    };
                  merge(page.items);
                  setHasOlder(page.hasMore);
                })
                .catch((reason) => {
                  if (active.current)
                    setSendError(
                      reason instanceof Error
                        ? reason.message
                        : "Не удалось загрузить сообщения.",
                    );
                })
                .finally(() => {
                  if (active.current) setOlderBusy(false);
                });
            }}
          >
            {olderBusy ? "Загружаем…" : "Ранние сообщения"}
          </button>
        )}
        {!loading && !error && !messages.length && (
          <div className="chat-empty">
            <MessageSquare size={32} />
            <h2>Сообщений нет</h2>
          </div>
        )}
        {messages.map((message) => (
          <article
            className={`chat-message${message.sender.username === account.name ? " chat-message-own" : " chat-message-other"}`}
            key={message.id}
          >
            <span className="chat-message-author">
              <Avatar user={message.sender} className="message-avatar" />
              <Username user={message.sender} account={account} />
            </span>
            <p>{message.text}</p>
            <time dateTime={message.sentAt}>
              {time.format(new Date(message.sentAt))}
            </time>
          </article>
        ))}
      </div>
      <form
        className="chat-composer"
        onSubmit={(event) => {
          event.preventDefault();
          const text = draft.trim();
          if (!text || sending || loading || error) return;
          const request =
            pending.current?.text === text
              ? pending.current
              : { text, id: crypto.randomUUID() };
          pending.current = request;
          setSending(true);
          setSendError("");
          void client
            .sendMessage(order.id, text, request.id)
            .then((message) => {
              if (!active.current) return;
              following.current = true;
              merge([message]);
              setDraft("");
              pending.current = null;
            })
            .catch((reason) => {
              if (active.current)
                setSendError(
                  reason instanceof Error
                    ? reason.message
                    : "Не удалось отправить сообщение.",
                );
            })
            .finally(() => {
              if (active.current) setSending(false);
            });
        }}
      >
        {sendError && (
          <p role="alert" className="form-error">
            {sendError}
          </p>
        )}
        <div>
          <textarea
            aria-label="Сообщение"
            placeholder="Сообщение…"
            value={draft}
            maxLength={1000}
            rows={2}
            disabled={loading || sending || !!error}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (
                event.key === "Enter" &&
                !event.shiftKey &&
                !event.nativeEvent.isComposing
              ) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }}
          />
          <button
            className="chat-send"
            aria-label="Отправить сообщение"
            disabled={!draft.trim() || loading || sending || !!error}
          >
            {sending ? (
              <LoaderCircle className="spin" size={18} />
            ) : (
              <Send size={19} />
            )}
          </button>
        </div>
      </form>
    </section>
  );
}
