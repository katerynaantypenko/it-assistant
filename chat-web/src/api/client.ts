import type { ToolCall, User } from "../types";

/** Spring Security starts the authorization code flow on this path. */
export const LOGIN_URL = "/oauth2/authorization/oidc";

export type ChatEvent =
  | { name: "token"; data: { text: string } }
  | { name: "tool"; data: ToolCall }
  | { name: "error"; data: { message: string } }
  | { name: "done"; data: Record<string, never> };

export async function fetchCurrentUser(): Promise<User | null> {
  const response = await fetch("/api/me");
  return response.ok ? response.json() : null;
}

export async function logout(): Promise<void> {
  await fetch("/api/logout", { method: "POST" });
}

export async function decideApproval(toolCallId: string, approved: boolean): Promise<void> {
  const response = await fetch(`/api/chat/approvals/${toolCallId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ approved }),
  });
  if (!response.ok) {
    throw new Error(`The decision was not accepted (HTTP ${response.status}).`);
  }
}

/**
 * Sends a message and reads back the events of one agent run.
 *
 * EventSource can only issue GET requests, so the response body of the POST is read as a stream and
 * the server-sent event frames are parsed here. Frames are separated by a blank line.
 */
export async function streamChat(message: string, onEvent: (event: ChatEvent) => void): Promise<void> {
  const response = await fetch("/api/chat/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (response.status === 401) {
    // The tokens behind the session can no longer be renewed, so only a new sign-in helps.
    throw new Error("Your session has expired. Please sign in again.");
  }
  if (!response.ok || !response.body) {
    throw new Error(`The assistant could not be reached (HTTP ${response.status}).`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });

    let separator = buffer.indexOf("\n\n");
    while (separator >= 0) {
      const event = parseFrame(buffer.slice(0, separator));
      buffer = buffer.slice(separator + 2);
      if (event) {
        onEvent(event);
      }
      separator = buffer.indexOf("\n\n");
    }
  }
}

function parseFrame(frame: string): ChatEvent | null {
  let name = "";
  const dataLines: string[] = [];

  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) {
      name = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trim());
    }
  }

  if (!name || dataLines.length === 0) {
    return null;
  }
  return { name, data: JSON.parse(dataLines.join("\n")) } as ChatEvent;
}
