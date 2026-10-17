export function readStored<T>(key: string, fallback: T): T {
  try {
    return JSON.parse(localStorage.getItem(key) || "null") ?? fallback;
  } catch {
    return fallback;
  }
}
export function writeStored(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* Приложение работает и без хранилища. */
  }
}
export function positiveId(value: string): number {
  const id = Number(value);
  if (!/^\d+$/.test(value) || !Number.isSafeInteger(id) || id < 1)
    throw new Error("Укажи целый положительный ID.");
  return id;
}
export function requiredText(value: FormDataEntryValue | null): string {
  const text = String(value ?? "").trim();
  if (!text)
    throw new Error("Поле не должно быть пустым или состоять из пробелов.");
  return text;
}
export const money = (value: number) =>
  new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(value);
