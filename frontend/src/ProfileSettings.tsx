import { useId, useState } from "react";
import type { Account, Marketplace } from "./types";
import { ImageEditor } from "./Media";

export default function ProfileSettings({
  account,
  client,
  onUpdated,
  onBusyChange,
}: {
  account: Account;
  client: Marketplace;
  onUpdated: (account: Account) => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const fieldId = useId();
  return (
    <div className="profile-settings">
      <ImageEditor
        label="Аватар"
        variant="avatar"
        currentUrl={account.avatarUrl}
        onUpload={async (file) => onUpdated(await client.uploadAvatar(file))}
        onRemove={async () => onUpdated(await client.removeAvatar())}
        onBusyChange={onBusyChange}
      />
      <form
        onChange={() => setSaved(false)}
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          const ageText = String(form.get("age") ?? "").trim();
          const age = ageText ? Number(ageText) : null;
          setError("");
          setSaved(false);
          if (
            age !== null &&
            (!Number.isInteger(age) || age < 1 || age > 120)
          ) {
            setError("Возраст — целое число от 1 до 120.");
            return;
          }
          setBusy(true);
          onBusyChange(true);
          void client
            .updateProfile({
              age,
              city: String(form.get("city") ?? "").trim() || null,
              about: String(form.get("about") ?? "").trim() || null,
            })
            .then((updated) => {
              onUpdated(updated);
              setSaved(true);
            })
            .catch((reason) =>
              setError(
                reason instanceof Error
                  ? reason.message
                  : "Не удалось сохранить профиль.",
              ),
            )
            .finally(() => {
              setBusy(false);
              onBusyChange(false);
            });
        }}
      >
        <fieldset disabled={busy}>
          <div className="profile-field">
            <label htmlFor={`${fieldId}-age`}>Возраст</label>
            <input
              id={`${fieldId}-age`}
              name="age"
              type="number"
              min="1"
              max="120"
              step="1"
              defaultValue={account.age ?? ""}
            />
          </div>
          <div className="profile-field">
            <label htmlFor={`${fieldId}-city`}>Город</label>
            <input
              id={`${fieldId}-city`}
              name="city"
              maxLength={100}
              defaultValue={account.city ?? ""}
            />
          </div>
          <div className="profile-field">
            <label htmlFor={`${fieldId}-about`}>О себе</label>
            <textarea
              id={`${fieldId}-about`}
              name="about"
              rows={4}
              maxLength={1000}
              defaultValue={account.about ?? ""}
            />
          </div>
          <button className="button button-full">
            {busy ? "Сохраняем…" : "Сохранить профиль"}
          </button>
        </fieldset>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {saved && (
          <p role="status" className="settings-saved">
            Профиль сохранён.
          </p>
        )}
      </form>
    </div>
  );
}
