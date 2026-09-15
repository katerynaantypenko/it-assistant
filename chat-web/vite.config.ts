import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const BACKEND = process.env.CHAT_BACKEND_URL ?? "http://localhost:8080";

// Everything is served from one origin (localhost:5173) and proxied to the backend. That keeps the
// session cookie same-site and lets the OIDC redirect URI stay http://localhost:5173/login/oauth2/code/oidc.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: BACKEND },
      "/oauth2": { target: BACKEND },
      "/login": { target: BACKEND },
    },
  },
});
