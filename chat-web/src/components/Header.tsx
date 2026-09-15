import { logout } from "../api/client";
import type { User } from "../types";

interface HeaderProps {
  user: User;
}

export function Header({ user }: HeaderProps) {
  // A full reload after logout is the simplest way to get back to a clean, signed-out state.
  async function signOut() {
    await logout();
    window.location.reload();
  }

  return (
    <header className="header">
      <div className="header-title">IT Assistant</div>
      <div className="header-user">
        {user.picture && <img className="avatar" src={user.picture} alt="" />}
        <div className="header-identity">
          <span className="header-name">{user.name ?? user.email}</span>
          <span className="header-email">{user.email}</span>
        </div>
        <button className="button" type="button" onClick={signOut}>
          Sign out
        </button>
      </div>
    </header>
  );
}
