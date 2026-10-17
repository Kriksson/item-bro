import type { Account, PublicUser } from "./types";
import { createContext, useContext } from "react";

export const ProfileNavigation = createContext<
  ((username: string) => void) | null
>(null);

export default function Username({
  user,
  account,
  interactive = true,
}: {
  user: PublicUser;
  account?: Account | null;
  interactive?: boolean;
}) {
  const onOpen = useContext(ProfileNavigation);
  const role = account?.name === user.username ? account.role : user.userRole;
  const isAdmin = role === "ADMIN";
  const className = `username${isAdmin ? " username-admin" : ""}`;
  if (onOpen && interactive)
    return (
      <button
        type="button"
        className={`${className} username-link`}
        title={isAdmin ? "Профиль администратора" : "Открыть профиль"}
        onClick={(event) => {
          event.stopPropagation();
          onOpen(user.username);
        }}
      >
        {user.username}
      </button>
    );
  return (
    <span className={className} title={isAdmin ? "Администратор" : undefined}>
      {user.username}
    </span>
  );
}
