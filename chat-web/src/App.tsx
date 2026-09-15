import { useCallback, useEffect, useRef, useState } from "react";
import { decideApproval, fetchCurrentUser, streamChat, type ChatEvent } from "./api/client";
import { Composer } from "./components/Composer";
import { Header } from "./components/Header";
import { LoginScreen } from "./components/LoginScreen";
import { MessageList } from "./components/MessageList";
import type { ChatItem, ToolCall, User } from "./types";

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [checkingUser, setCheckingUser] = useState(true);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [running, setRunning] = useState(false);

  // Key of the assistant bubble that streaming text is currently appended to.
  const openAssistantKey = useRef<string | null>(null);
  const itemCounter = useRef(0);

  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .finally(() => setCheckingUser(false));
  }, []);

  const nextKey = useCallback(() => `item-${(itemCounter.current += 1)}`, []);

  const appendAssistantText = useCallback(
    (text: string) => {
      const key = openAssistantKey.current;
      if (!key) {
        const newKey = nextKey();
        openAssistantKey.current = newKey;
        setItems((previous) => [...previous, { kind: "assistant", key: newKey, text }]);
        return;
      }
      setItems((previous) =>
        previous.map((item) =>
          item.kind === "assistant" && item.key === key ? { ...item, text: item.text + text } : item,
        ),
      );
    },
    [nextKey],
  );

  // The same tool call id is reported for every status change, so the existing card is updated.
  const upsertToolCall = useCallback((call: ToolCall) => {
    setItems((previous) => {
      const known = previous.some((item) => item.kind === "tool" && item.call.id === call.id);
      if (known) {
        return previous.map((item) =>
          item.kind === "tool" && item.call.id === call.id ? { ...item, call } : item,
        );
      }
      return [...previous, { kind: "tool", key: `tool-${call.id}`, call }];
    });
  }, []);

  const handleEvent = useCallback(
    (event: ChatEvent) => {
      if (event.name === "token") {
        appendAssistantText(event.data.text);
        return;
      }
      if (event.name === "tool") {
        // A tool card ends the current bubble, so text after the call starts a new one.
        openAssistantKey.current = null;
        upsertToolCall(event.data);
        return;
      }
      if (event.name === "error") {
        openAssistantKey.current = null;
        setItems((previous) => [...previous, { kind: "error", key: nextKey(), text: event.data.message }]);
      }
    },
    [appendAssistantText, nextKey, upsertToolCall],
  );

  const send = useCallback(
    async (message: string) => {
      setItems((previous) => [...previous, { kind: "user", key: nextKey(), text: message }]);
      openAssistantKey.current = null;
      setRunning(true);
      try {
        await streamChat(message, handleEvent);
      } catch (error) {
        setItems((previous) => [...previous, { kind: "error", key: nextKey(), text: String(error) }]);
      } finally {
        openAssistantKey.current = null;
        setRunning(false);
      }
    },
    [handleEvent, nextKey],
  );

  // The new status arrives over the stream, so nothing is changed optimistically here.
  const decide = useCallback(
    async (toolCallId: string, approved: boolean) => {
      try {
        await decideApproval(toolCallId, approved);
      } catch (error) {
        setItems((previous) => [...previous, { kind: "error", key: nextKey(), text: String(error) }]);
      }
    },
    [nextKey],
  );

  if (checkingUser) {
    return <div className="loading">Loading…</div>;
  }
  if (!user) {
    return <LoginScreen />;
  }

  return (
    <div className="app">
      <Header user={user} />
      <MessageList items={items} onDecide={decide} />
      <Composer disabled={running} onSend={send} />
    </div>
  );
}
