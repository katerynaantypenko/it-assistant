import { useEffect, useRef } from "react";
import type { ChatItem } from "../types";
import { ToolCallCard } from "./ToolCallCard";

interface MessageListProps {
  items: ChatItem[];
  onDecide: (toolCallId: string, approved: boolean) => void;
}

export function MessageList({ items, onDecide }: MessageListProps) {
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [items]);

  return (
    <main className="messages">
      {items.length === 0 && (
        <p className="hint">
          Ask something like “how do I set up the VPN?” or “write an article about booking a meeting
          room”.
        </p>
      )}

      {items.map((item) => {
        if (item.kind === "tool") {
          return <ToolCallCard key={item.key} call={item.call} onDecide={onDecide} />;
        }
        return (
          <div key={item.key} className={`bubble ${item.kind}`}>
            {item.text}
          </div>
        );
      })}

      <div ref={bottom} />
    </main>
  );
}
