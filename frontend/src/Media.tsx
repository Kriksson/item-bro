import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { ImagePlus, LoaderCircle, Trash2 } from "lucide-react";
import type { Advice, PublicUser } from "./types";
import { ItemArtwork } from "./components";

export function Picture({
  src,
  alt,
  className,
  fallback,
}: {
  src?: string | null;
  alt: string;
  className?: string;
  fallback: ReactNode;
}) {
  const [failed, setFailed] = useState<string | null>(null);
  return src && failed !== src ? (
    <img
      src={src}
      alt={alt}
      className={className}
      loading="lazy"
      onError={() => setFailed(src)}
    />
  ) : (
    <>{fallback}</>
  );
}
export function Avatar({
  user,
  className = "avatar",
}: {
  user: Pick<PublicUser, "username" | "avatarUrl">;
  className?: string;
}) {
  return (
    <span className={`${className} user-avatar`}>
      <Picture
        src={user.avatarUrl}
        alt={`Аватар ${user.username}`}
        fallback={user.username[0]?.toUpperCase()}
      />
    </span>
  );
}
export function AdviceArtwork({
  advice,
  large = false,
}: {
  advice: Advice;
  large?: boolean;
}) {
  return (
    <Picture
      src={advice.imageUrl}
      alt={advice.name}
      className={`advice-image${large ? " advice-image-large" : ""}`}
      fallback={<ItemArtwork seed={advice.id} large={large} />}
    />
  );
}
export function validateImage(file: File) {
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type))
    throw new Error("Выбери JPEG, PNG или WebP.");
  if (file.size === 0 || file.size > 5 * 1024 * 1024)
    throw new Error("Размер изображения — до 5 МБ.");
}
export function ImageEditor({
  label,
  currentUrl,
  onUpload,
  onRemove,
  onBusyChange,
  variant,
  showCurrent = true,
}: {
  label: string;
  currentUrl?: string | null;
  onUpload: (file: File) => Promise<void>;
  onRemove: () => Promise<void>;
  onBusyChange?: (busy: boolean) => void;
  variant?: "avatar";
  showCurrent?: boolean;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (!file) {
      setPreview("");
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  const act = async (action: () => Promise<void>) => {
    setBusy(true);
    onBusyChange?.(true);
    setError("");
    try {
      await action();
      setFile(null);
      if (input.current) input.current.value = "";
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Не удалось сохранить изображение.",
      );
    } finally {
      setBusy(false);
      onBusyChange?.(false);
    }
  };
  return (
    <section
      className={`image-editor${variant === "avatar" ? " image-editor-avatar" : ""}`}
      aria-label={label}
    >
      <h3>{label}</h3>
      {(preview || (showCurrent && currentUrl)) && (
        <Picture
          src={preview || currentUrl}
          alt={label}
          className="image-editor-preview"
          fallback={<span>Изображение недоступно</span>}
        />
      )}
      <label className="image-file-label">
        <ImagePlus size={17} />
        <span>Выбрать изображение</span>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          aria-label={label}
          disabled={busy}
          onChange={(event) => {
            const next = event.target.files?.[0];
            setError("");
            setFile(null);
            if (!next) return;
            try {
              validateImage(next);
              setFile(next);
            } catch (reason) {
              setError((reason as Error).message);
              event.target.value = "";
            }
          }}
        />
      </label>
      {file && <span className="image-file-name">{file.name}</span>}
      <small>JPEG, PNG, WebP · до 5 МБ</small>
      <div className="image-editor-actions">
        {file && (
          <button
            type="button"
            className="button button-small"
            disabled={busy}
            onClick={() => void act(() => onUpload(file))}
          >
            {busy ? <LoaderCircle className="spin" size={16} /> : null}Сохранить
            изображение
          </button>
        )}
        {currentUrl && (
          <button
            type="button"
            className="button button-secondary button-small"
            disabled={busy}
            onClick={() => void act(onRemove)}
          >
            <Trash2 size={15} />
            Удалить изображение
          </button>
        )}
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
