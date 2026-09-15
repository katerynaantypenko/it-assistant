import { LOGIN_URL } from "../api/client";

export function LoginScreen() {
  return (
    <div className="login">
      <h1>IT Assistant</h1>
      <p>Internal IT knowledge base. Sign in with your company account to start.</p>
      <a className="button primary" href={LOGIN_URL}>
        Sign in with company SSO
      </a>
    </div>
  );
}
