import { useState } from "react";

interface ComposerProps {
  disabled: boolean;
  onSend: (message: string) => void;
}

export function Composer({ disabled, onSend }: ComposerProps) {
  const [text, setText] = useState("");

  function submit() {
    const message = text.trim();
    if (!message || disabled) {
      return;
    }
    setText("");
    onSend(message);
  }

  return (
    <footer className="composer">
      <textarea
        value={text}
        rows={2}
        placeholder={disabled ? "The assistant is working…" : "Ask about an IT topic"}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          // Enter sends, Shift+Enter adds a line break.
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            submit();
          }
        }}
      />
      <button className="button primary" type="button" disabled={disabled} onClick={submit}>
        Send
      </button>
    </footer>
  );
}
