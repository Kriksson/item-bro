import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { X, Package } from "lucide-react";

export function Logo() {
  return (
    <span className="logo">
      <span className="logo-mark">
        <Package size={24} strokeWidth={2.5} />
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
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => {
      if (dialog.open) dialog.close();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
    >
      <button
        type="button"
        className="icon-button modal-close"
        aria-label="Закрыть окно"
        onClick={onClose}
      >
        <X size={20} />
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
        <Package size={30} />
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
  const id = useId().replaceAll(":", "");
  const variant = Math.abs(seed) % 4;
  const color = ["#eaa5ae", "#d6b6c1", "#edb2a2", "#e192a3"][variant];
  return (
    <div
      className={`item-art art-${variant}${large ? " art-large" : ""}`}
      aria-hidden="true"
    >
      <div className="art-grid" />
      <span className="art-orbit" />
      <svg viewBox="0 0 300 180" role="presentation">
        <defs>
          <linearGradient id={`blade-${id}`} x1="0" y1="0" x2="1" y2="1">
            <stop stopColor="#fff" />
            <stop offset=".35" stopColor={color} />
            <stop offset="1" stopColor="#874858" />
          </linearGradient>
          <linearGradient id={`handle-${id}`}>
            <stop stopColor="#563b43" />
            <stop offset=".5" stopColor="#d3a4ae" />
            <stop offset="1" stopColor="#6f414e" />
          </linearGradient>
        </defs>
        <g transform={`translate(150 90) rotate(${variant === 1 ? -28 : 32})`}>
          {variant === 0 && (
            <>
              <path
                d="M-8 45 -9-20Q-55-44-40-79L-19-64Q-34-47-6-43L4-63 19-45Q75-40 72 11L54-2Q49-23 12-17L7 45Z"
                fill={`url(#blade-${id})`}
              />
              <path d="m-7 40 14 0 0 40-14 0Z" fill={`url(#handle-${id})`} />
              <path d="m-15 39 30 0 3 7-36 0Z" fill={color} />
              <path
                d="M-5-31Q29-32 55-10"
                fill="none"
                stroke="#ffe6eb"
                strokeWidth="2"
              />
            </>
          )}
          {variant === 1 && (
            <>
              <path
                d="m-8 27-3-87 11-31 11 31-3 87Z"
                fill={`url(#blade-${id})`}
              />
              <path d="M0-77V25" stroke="#fff2f4" strokeWidth="2" />
              <path d="m-35 24 35 8 35-8-6 16-29 4-29-4Z" fill={color} />
              <path d="m-6 42 12 0 3 38-18 0Z" fill={`url(#handle-${id})`} />
              <circle cy="82" r="8" fill={color} />
              <path
                d="m-40-30-2-30 12-15M40-30l2-30-12-15"
                fill="none"
                stroke={color}
                strokeWidth="4"
              />
            </>
          )}
          {variant === 2 && (
            <>
              <path
                d="M-7 27-11-61 0-88 11-61 7 27Z"
                fill={`url(#blade-${id})`}
              />
              <path
                d="m-35-47 26-8-2-18-31 4-14 27 23 25 24-4v-13l-20 1-9-10Z"
                fill={`url(#blade-${id})`}
              />
              <path
                d="m35-47-26-8 2-18 31 4 14 27-23 25-24-4v-13l20 1 9-10Z"
                fill={`url(#blade-${id})`}
              />
              <path d="m-25 25 50 0-8 12-34 0Z" fill={color} />
              <path d="m-6 37 12 0 0 44-12 0Z" fill={`url(#handle-${id})`} />
              <circle cy="83" r="7" fill={color} />
            </>
          )}
          {variant === 3 && (
            <>
              <path
                d="M-8 44-10-48Q-43-63-36-91L-13-72 1-86 13-65 39-87Q46-54 10-46L7 44Z"
                fill={`url(#blade-${id})`}
              />
              <path d="M-3-52 0 33" stroke="#ffe2e8" strokeWidth="3" />
              <path d="m-20 36 40 0-5 11-30 0Z" fill={color} />
              <path d="m-6 47 12 0 3 35-18 0Z" fill={`url(#handle-${id})`} />
              <circle cy="84" r="8" fill={color} />
            </>
          )}
        </g>
      </svg>
      <span className="art-spark spark-one" />
      <span className="art-spark spark-two" />
    </div>
  );
}

export function HeroArt() {
  const id = useId().replaceAll(":", "");
  return (
    <div className="hero-art" aria-hidden="true">
      <div className="hero-glow" />
      <span className="hero-orbit orbit-one" />
      <span className="hero-orbit orbit-two" />
      <svg viewBox="0 0 400 310">
        <defs>
          <linearGradient id={`cubeTop-${id}`} x1="0" y1="0" x2="1" y2="1">
            <stop stopColor="#ff8793" />
            <stop offset="1" stopColor="#d82740" />
          </linearGradient>
          <linearGradient id={`cubeSide-${id}`} x1="0" x2="1">
            <stop stopColor="#99172e" />
            <stop offset="1" stopColor="#eb3c55" />
          </linearGradient>
        </defs>
        <g transform="translate(200 155) rotate(-9)">
          <path d="M0-100 105-44 0 15-105-44Z" fill={`url(#cubeTop-${id})`} />
          <path d="M-105-44 0 15 0 131-105 72Z" fill="#c7233a" />
          <path d="M105-44 0 15 0 131 105 72Z" fill={`url(#cubeSide-${id})`} />
          <path d="m-75-60 103 57v35L-75-26Z" fill="#ffc1c8" opacity=".7" />
          <path d="m-15-91 105 57-27 15L-42-76Z" fill="#fff0f2" opacity=".8" />
          <path d="M0 15v116" stroke="#ff929f" strokeWidth="1.5" />
          <path d="m-75 38 40 23v15l-40-23Z" fill="#7c152a" />
          <path d="m-75 61 25 14v5l-25-14Z" fill="#9c1a31" />
        </g>
      </svg>
    </div>
  );
}
