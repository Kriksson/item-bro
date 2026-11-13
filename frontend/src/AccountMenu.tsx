import { useEffect, useId, useRef, useState } from "react";
import { ChevronDown, LogOut, Settings2 } from "lucide-react";
import type { Account } from "./types";
import { Avatar } from "./Media";
import Username from "./Username";

export default function AccountMenu({
  account,
  busy,
  onSettings,
  onLogout,
}: {
  account: Account;
  busy: boolean;
  onSettings: () => void;
  onLogout: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();
  useEffect(() => {
    if (!expanded) return;
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setExpanded(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setExpanded(false);
        trigger.current?.focus();
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [expanded]);
  return (
    <div className="account-menu" ref={root}>
      <button
        ref={trigger}
        className="profile"
        aria-label={`Аккаунт ${account.name}`}
        aria-expanded={expanded}
        aria-controls={id}
        onClick={() => setExpanded(!expanded)}
      >
        <Avatar
          user={{ username: account.name, avatarUrl: account.avatarUrl }}
        />
        <Username
          user={{ username: account.name, userRole: account.role }}
          interactive={false}
        />
        <ChevronDown size={13} />
      </button>
      {expanded && (
        <section
          className="account-dropdown"
          id={id}
          aria-label="Меню аккаунта"
        >
          <span className="account-dropdown-name">{account.name}</span>
          <a
            href="/profile/edit"
            aria-disabled={busy || undefined}
            onClick={(event) => {
              if (busy) {
                event.preventDefault();
                return;
              }
              if (
                event.ctrlKey ||
                event.metaKey ||
                event.shiftKey ||
                event.altKey
              )
                return;
              event.preventDefault();
              setExpanded(false);
              onSettings();
            }}
          >
            <Settings2 size={17} />
            Настройки профиля
          </a>
          <button
            disabled={busy}
            onClick={() => {
              setExpanded(false);
              onLogout();
            }}
          >
            <LogOut size={17} />
            Выйти
          </button>
        </section>
      )}
    </div>
  );
}
