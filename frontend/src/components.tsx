import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { X, Package } from "lucide-react";

export function Logo() {
  return (
    <span className="logo">
      <span className="logo-mark">
        <Package size={30} strokeWidth={2} />
      </span>
      <span>
        item<span className="logo-light">bro</span>
        <span className="logo-dot">.</span>
      </span>
    </span>
  );
}

export function Modal({
  title,
  subtitle,
  children,
  onClose,
  variant = "default",
  canClose = true,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  onClose: () => void;
  variant?: string;
  canClose?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const closing = useRef(false);
  const exit = useRef<Animation | null>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current!;
    const opener = document.activeElement;
    dialog.showModal();
    return () => {
      exit.current?.cancel();
      if (dialog.open) dialog.close();
      if (
        opener instanceof HTMLElement &&
        opener.isConnected &&
        !opener.matches(":disabled")
      ) {
        opener.focus({ preventScroll: true });
      }
    };
  }, []);
  const requestClose = () => {
    if (!canClose || closing.current) return;
    const dialog = ref.current;
    if (
      !dialog ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      onClose();
      return;
    }
    closing.current = true;
    dialog.dataset.closing = "";
    dialog.inert = true;
    const tokens = getComputedStyle(dialog);
    exit.current = dialog.animate(
      [
        { opacity: tokens.opacity, transform: tokens.transform },
        { opacity: 0, transform: "translateY(8px) scale(.97)" },
      ],
      {
        duration:
          Number.parseFloat(tokens.getPropertyValue("--t-dismiss")) || 160,
        easing: tokens.getPropertyValue("--ease-out").trim(),
        fill: "forwards",
      },
    );
    void exit.current.finished
      .then(() => {
        if (dialog.isConnected) onClose();
      })
      .catch(() => {
        /* Unmounting during a completed action cancels the animation. */
      });
  };
  return (
    <dialog
      ref={ref}
      className={`modal modal-${variant}`}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        requestClose();
      }}
      onClick={(event) => {
        if (event.target === ref.current) requestClose();
      }}
    >
      <button
        type="button"
        className="icon-button modal-close"
        aria-label="Закрыть окно"
        disabled={!canClose}
        onClick={requestClose}
      >
        <X size={19} />
      </button>
      <h2 id={titleId}>{title}</h2>
      {subtitle && <p className="modal-subtitle">{subtitle}</p>}
      {children}
    </dialog>
  );
}

export function Empty({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Package size={28} strokeWidth={1.5} />
      </span>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}

export function ItemArtwork({
  seed,
  large = false,
}: {
  seed: number;
  large?: boolean;
}) {
  return (
    <div
      className={`item-art art-${Math.abs(seed) % 4}${large ? " art-large" : ""}`}
      aria-hidden="true"
    >
      <Package strokeWidth={1.2} />
    </div>
  );
}

export function HeroArt() {
  return (
    <div className="hero-art" aria-hidden="true">
      <svg viewBox="0 0 400 320" role="presentation">
        <g className="parcel">
          <path d="M196 30 342 109 200 190 54 109Z" fill="#fff" />
          <path d="M54 109 200 190 200 307 54 225Z" fill="#f1f2f5" />
          <path d="M342 109 200 190 200 307 342 225Z" fill="#dce0e7" />
          <path d="m113 77 144 80 38-22L151 57Z" fill="#202126" />
          <path d="m257 157 38-22v43l-38 21Z" fill="#353b47" />
          <path d="m80 163 88 49v46l-88-49Z" fill="#fff" />
          <path
            d="m92 184 38 21m-38-10 55 31"
            fill="none"
            stroke="#b7bec9"
            strokeWidth="5"
          />
          <path
            d="m223 232 16-9m6-4 16-9m6-4 16-9m6-4 16-9"
            stroke="#9aa3b2"
            strokeWidth="4"
          />
          <path
            d="M54 109 200 190 342 109M200 190v117"
            fill="none"
            stroke="#fff"
            strokeWidth="2"
          />
          <g transform="translate(244 42) rotate(13)">
            <path
              d="M-35-17h70c13 0 20 11 22 23l5 32c2 16-12 22-22 12L22 30h-44l-18 20c-10 10-24 4-22-12l5-32c2-12 9-23 22-23Z"
              fill="#202126"
            />
            <path
              d="M-31 1v23m-12-12h24"
              stroke="#fff"
              strokeWidth="6"
              strokeLinecap="round"
            />
            <circle cx="31" cy="4" r="4" fill="#fff" />
            <circle cx="43" cy="16" r="4" fill="#fff" />
          </g>
        </g>
      </svg>
    </div>
  );
}
