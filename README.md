# IT Assistant — a chat client for your tools

An internal IT knowledge base assistant. A signed-in employee chats with an LLM agent that reads and
writes the knowledge base through tools exposed by a separate MCP server. Anything that changes the
knowledge base is shown to the user with its exact arguments and is only executed after approval.

## Three processes

| Process | Stack | Port | Role |
| --- | --- | --- | --- |
| `mcp-kb-server` | Spring Boot 3.4, MCP Java SDK 2.0 | 8081 | MCP server over **Streamable HTTP** at `/mcp`. Owns the knowledge base. |
| `chat-backend` | Spring Boot 3.4, OpenAI Java SDK 4 | 8080 | OIDC login, agent loop, MCP **client**, approval enforcement. |
| `chat-web` | React 18 + Vite | 5173 | Chat UI, streaming, tool activity, approve/deny. |

The knowledge base is ten markdown files in [`knowledge-base/`](knowledge-base). Each file has a
small `---` header with `id`, `title`, `tags`, `author` and `created`. New articles are written into
the same directory.

## Prerequisites

- JDK 21 or newer, Maven 3.8+ — Maven itself has to run on that JDK, so `JAVA_HOME` must point at it
  if it is not your default. `run-local.sh` checks this and picks up an installed JDK 21+ on macOS.
- Node.js 20 or newer
- Docker, for the OIDC provider
- An OpenAI API key — the only credential you have to supply

## Signing in

Authentication is a real OAuth2 authorization code flow. The provider is a Keycloak container
described in [`docker-compose.yml`](docker-compose.yml), which imports
[`keycloak/it-assistant-realm.json`](keycloak/it-assistant-realm.json) on first start: one
confidential client and two test users. Nothing has to be registered anywhere.

| Username | Password | E-mail |
| --- | --- | --- |
| `olena` | `test` | `olena@example.com` |
| `andrii` | `test` | `andrii@example.com` |

Two users, because the interesting property to demonstrate is that the author recorded by
`create_article` is whoever is actually signed in.

The registered redirect URI is `http://localhost:5173/login/oauth2/code/oidc`. It points at the Vite
dev server because that proxies `/api`, `/oauth2` and `/login` to the backend, so everything the
browser sees is one origin and the session cookie stays same-site.

Logout is local to the chat backend, so the Keycloak SSO session outlives it and signing back in
needs no password. To sign in as the other user, end that session too:
<http://localhost:8180/realms/it-assistant/protocol/openid-connect/logout>.

Any other OIDC provider works instead — set `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID` and
`OIDC_CLIENT_SECRET`, register the redirect URI above, and `run-local.sh` will skip the container.
Its access tokens have to name an audience the MCP server can check: either configure the provider to
add `mcp-kb-server`, or point `MCP_TOKEN_AUDIENCE` at an audience it already issues.
The Keycloak admin console is on <http://localhost:8180> with `admin` / `admin`.

## Environment variables

Copy `.env.example` to `.env`.

| Variable | Required | Default |
| --- | --- | --- |
| `OPENAI_API_KEY` | yes | — |
| `OPENAI_MODEL` | no | `gpt-4o-mini` |
| `OIDC_ISSUER_URI` | no | `http://localhost:8180/realms/it-assistant` |
| `OIDC_CLIENT_ID` | no | `it-assistant-web` |
| `OIDC_CLIENT_SECRET` | no | `local-dev-only-not-a-real-secret` |
| `MCP_TOKEN_AUDIENCE` | no | `mcp-kb-server` |

**No credentials are committed.** `OPENAI_API_KEY` has no default, so the backend refuses to start
without one, and `.env` is git-ignored. The values that do look like secrets — the Keycloak client
secret, the two test passwords, `admin` / `admin` for the console — are fixtures of the provider that
ships with this repository. It only exists while your container runs and only knows two invented
users, so there is no account behind them. Pointing `OIDC_*` at a real provider moves its secret into
`.env`, where it stays uncommitted.

## Run it

```bash
cp .env.example .env      # then add your OpenAI key
cd chat-web && npm install && cd ..
./run-local.sh
```

The script starts Keycloak, waits until the realm is imported, then runs the three processes. Open
<http://localhost:5173>. `Ctrl+C` stops the processes; `docker compose down` stops Keycloak.

Or start everything by hand, each process in its own terminal:

```bash
docker compose up -d keycloak
set -a; source .env; set +a

mvn -pl mcp-kb-server spring-boot:run
mvn -pl chat-backend  spring-boot:run
cd chat-web && npm run dev
```

Tests:

```bash
mvn test
```

`KnowledgeBaseToolsIntegrationTest` drives the real MCP server over Streamable HTTP with a bearer
token, `McpToolGatewayTest` covers the approval enforcement, and `KnowledgeBaseServiceTest` covers the
round trip through disk — including that a title from the model cannot break the front matter and
take the recorded author with it. `AccessTokenProviderTest` covers how the token reaching the MCP
server is renewed, and `ChatSessionTest` covers that the conversation history stays valid to replay:
both are failures that only surface on a later turn, which is exactly what a test is good for.

## Try it

- *“How do I set up the VPN?”* — the agent searches, reads an article and answers with `(kb-002)`.
- *“Write an article about booking a meeting room.”* — the agent proposes a `create_article` call.
  The UI shows the tool name and the full title and body, and waits. **Approve** writes the file
  (`knowledge-base/kb-011-….md`) with the signed-in user's e-mail as the author; **Deny** executes
  nothing, tells the model, and the conversation continues.
- Sign in as the other user and create another article: the `author` in the new file's header follows
  the session, not the conversation.

## How the parts fit together

### Identity reaches the MCP server

The MCP server is a separate process and does not trust its caller. It runs as an OAuth2 resource
server: every request to `/mcp` must carry the user's access token as a bearer token, and the token is
validated against the provider's JWKS, including the audience. The audience is `mcp-kb-server` rather
than the web client id, so a token minted for some other API cannot be replayed here — Keycloak adds
it through an audience mapper on the client (see `keycloak/it-assistant-realm.json`).

Access tokens are short lived — five minutes on the bundled Keycloak — so the token is resolved once
per request from Spring's authorized client store, which renews it with the refresh token when it is
close to expiry ([`AccessTokenProvider`](chat-backend/src/main/java/tech/itassistant/chat_backend/service/AccessTokenProvider.java)).
Renewal writes the new token back into the session and therefore has to happen on the request thread,
which is why the token is read in the controller and handed to the agent's worker thread as a string.
When even the refresh token is gone, the API answers `401` and the web client asks the user to sign in
again. The chat backend opens **one MCP session per agent run** with that token
([`McpToolGatewayFactory`](chat-backend/src/main/java/tech/itassistant/chat_backend/service/mcp/McpToolGatewayFactory.java)),
and the transport's context extractor copies the verified claims into the MCP transport context
([`CallerIdentity`](mcp-kb-server/src/main/java/tech/itassistant/mcp_kb_server/util/CallerIdentity.java)).
`create_article` reads the author from there — never from the tool arguments, which the model controls.

### Approval is enforced at call time

[`McpToolGateway`](chat-backend/src/main/java/tech/itassistant/chat_backend/service/mcp/McpToolGateway.java)
is the only code path that executes a tool, and it checks the approval immediately before the call:

- A tool is **sensitive** unless the MCP server marked it `readOnlyHint: true`. The two processes
  therefore cannot drift apart, and a tool that is unknown to the client counts as sensitive.
- A sensitive call needs an approval recorded for **that exact tool call id**. Approvals are
  single-use, so a decision cannot be replayed for a second call.
- Prompting the model to ask first and labelling sensitive tools in their description are also done,
  but only as belt and braces. The model is not a trusted component.
- A denial is a normal outcome: nothing runs, the model gets a tool result saying it was denied, and
  the run continues.

### The backend/frontend contract

`POST /api/chat/messages` returns a server-sent event stream for one agent run:

| Event | Payload |
| --- | --- |
| `token` | `{ "text": "…" }` — a piece of assistant text |
| `tool` | the full tool call: `id`, `name`, `arguments`, `status`, `sensitive`, `result` |
| `error` | `{ "message": "…" }` |
| `done` | `{}` |

`status` moves through `AWAITING_APPROVAL` → `IN_FLIGHT` → `SUCCEEDED` / `FAILED`, or ends in
`DENIED`. The same `id` is repeated on every change, so the UI updates the card it already shows.
The decision goes back on `POST /api/chat/approvals/{toolCallId}` with `{"approved": true|false}`,
which returns `404` when no call is waiting.

`EventSource` cannot issue a POST, so the frontend reads the response body as a stream and parses the
SSE frames itself ([`api/client.ts`](chat-web/src/api/client.ts)) — about twenty lines, and it keeps
the API to a single request per run.

## Design decisions and trade-offs

**No agent framework.** The loop is roughly twenty lines in
[`AgentService`](chat-backend/src/main/java/tech/itassistant/chat_backend/service/AgentService.java):
ask the model, stream the answer, run the tools, ask again. The interesting seam in this task is
between *the model wants a tool* and *the tool runs* — a framework would hide exactly that, and I
would have had to fight it to put the approval gate there. The price is that retries, token budgets,
tracing and parallel tool calls are all things I would have to add by hand later.

**One MCP session per run instead of one long-lived client.** It costs an `initialize` round trip per
message, but each run carries the token of the user who started it, the tool catalogue is re-read every
time, and there is no shared mutable session between users. A long-lived shared client would keep
sending the token of whoever opened it first, and that token would be rejected the moment it expired.
With more traffic this would become a pool keyed by user.

**Approval waits on a blocking `CompletableFuture`.** The agent run occupies a thread while the user
thinks, with a timeout that counts as a denial. That is fine for one user at a time and very easy to
read. For real concurrency the run would have to be suspended and resumed instead — the state is
already keyed by tool call id, so that change is local to `ApprovalRegistry`.

**Sensitivity comes from the tool annotation, not from a list in the backend.** The MCP server owns
the statement “this tool changes state”, which is where it belongs. The cost is that a careless
server author could mark a writing tool read-only; a stricter deployment would pin a policy on the
client side as well.

**The identity provider is a container, not a table of users.** A local `users` table with a test
password would have been less moving parts, but it would not be OAuth2 or OIDC, and it would not
actually be less code: the identity has to cross a process boundary into the MCP server, so I would
have had to invent a token format and verify it myself. Running the provider gives me a signed ID
token and a JWKS endpoint that Spring Security already knows how to check, and it keeps the review
self-contained — no external account, no secret to obtain, works offline. Both services read the same
three environment variables, so swapping in a company provider is configuration, not code.

**CSRF is disabled for the API.** The session cookie is `SameSite=Lax` and the API is same-origin,
which blocks cross-site POSTs. A production setup would add CSRF tokens, or move to a bearer-token
API with no cookies at all.

## Deliberately out of scope

Per the task: roles and per-user permissions, retrieval quality, visual polish, concurrency,
persistence across restarts, multi-tenancy, deployment and CI. Conversation history and tool
approvals live in memory and are gone after a restart.
