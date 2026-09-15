import type { ToolCall, ToolCallStatus } from "../types";

interface ToolCallCardProps {
  call: ToolCall;
  onDecide: (toolCallId: string, approved: boolean) => void;
}

const STATUS_LABELS: Record<ToolCallStatus, string> = {
  AWAITING_APPROVAL: "waiting for your approval",
  IN_FLIGHT: "running",
  SUCCEEDED: "succeeded",
  FAILED: "failed",
  DENIED: "denied",
};

export function ToolCallCard({ call, onDecide }: ToolCallCardProps) {
  const awaitingApproval = call.status === "AWAITING_APPROVAL";

  return (
    <div className={`tool-card status-${call.status.toLowerCase()}`}>
      <div className="tool-head">
        <span className="tool-name">{call.name}</span>
        {call.sensitive && <span className="tool-badge">changes data</span>}
        <span className="tool-status">{STATUS_LABELS[call.status]}</span>
      </div>

      {/* The exact arguments that will be executed - this is what the user approves. */}
      <pre className="tool-arguments">{JSON.stringify(call.arguments, null, 2)}</pre>

      {awaitingApproval && (
        <div className="tool-actions">
          <button className="button primary" type="button" onClick={() => onDecide(call.id, true)}>
            Approve and run
          </button>
          <button className="button" type="button" onClick={() => onDecide(call.id, false)}>
            Deny
          </button>
        </div>
      )}

      {call.result && <pre className="tool-result">{call.result}</pre>}
    </div>
  );
}
