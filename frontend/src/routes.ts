import type { Page } from "./types";
export type Route = {
  page: Page;
  gameId: number | null;
  pageNumber: number;
  profileName: string;
  orderId?: number;
  adviceId?: number;
  dialog?: "login" | "register" | "listing" | "new-game" | "advice" | "buy";
  notFound?: boolean;
};
export function readRoute(): Route {
  const url = new URL(window.location.href);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const value = Number(url.searchParams.get("gameId"));
  const index = Number(url.searchParams.get("page"));
  const route: Route = {
    page: "market",
    gameId: Number.isSafeInteger(value) && value > 0 ? value : null,
    pageNumber: Number.isSafeInteger(index) && index > 0 ? index : 0,
    profileName: "",
  };
  if (path === "/") return route;
  if (path === "/orders") return { ...route, page: "orders" };
  if (path === "/advices") return { ...route, page: "selling" };
  if (path === "/admin") return { ...route, page: "admin" };
  if (path === "/login" || path === "/register")
    return { ...route, dialog: path === "/login" ? "login" : "register" };
  if (path === "/profile/edit") return { ...route, page: "settings" };
  if (path === "/advices/new")
    return { ...route, page: "selling", dialog: "listing" };
  if (path === "/admin/categories/new")
    return { ...route, page: "admin", dialog: "new-game" };
  const order = /^\/orders\/([1-9]\d*)$/.exec(path);
  if (order) return { ...route, page: "order", orderId: Number(order[1]) };
  const game = /^\/games\/([1-9]\d*)(?:\/advices\/([1-9]\d*)(\/buy)?)?$/.exec(
    path,
  );
  if (game)
    return {
      ...route,
      gameId: Number(game[1]),
      adviceId: game[2] ? Number(game[2]) : undefined,
      dialog: game[2] ? (game[3] ? "buy" : "advice") : undefined,
    };
  const profile = /^\/users\/([^/]+)$/.exec(path);
  if (profile) {
    try {
      return {
        ...route,
        page: "profile",
        profileName: decodeURIComponent(profile[1]),
      };
    } catch {
      /* Malformed paths use the not-found state. */
    }
  }
  return { ...route, notFound: true };
}
export function changeAddress(path: string, replace = false) {
  if (
    window.location.pathname + window.location.search + window.location.hash ===
    path
  )
    return;
  window.history[replace ? "replaceState" : "pushState"](null, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
export function catalogAddress(
  page: Page,
  gameId: number | null,
  pageNumber = 0,
) {
  const path =
    page === "selling" ? "/advices" : gameId ? `/games/${gameId}` : "/";
  const query = new URLSearchParams();
  if (page === "selling" && gameId) query.set("gameId", String(gameId));
  if (pageNumber) query.set("page", String(pageNumber));
  return path + (query.size ? `?${query}` : "");
}
