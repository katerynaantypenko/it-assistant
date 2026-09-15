export type ToolCallStatus =
  | "AWAITING_APPROVAL"
  | "IN_FLIGHT"
  | "SUCCEEDED"
  | "FAILED"
  | "DENIED";

export interface User {
  subject: string;
  email: string;
  name: string | null;
  picture: string | null;
}

export interface ToolCall {
  id: string;
  name: string;
  arguments: Record<string, unknown>;
  status: ToolCallStatus;
  /** True when the tool changes state, which is why it needs approval. */
  sensitive: boolean;
  result: string | null;
}

/** One entry of the transcript. Tool activity is shown inline, between the assistant answers. */
export type ChatItem =
  | { kind: "user"; key: string; text: string }
  | { kind: "assistant"; key: string; text: string }
  | { kind: "tool"; key: string; call: ToolCall }
  | { kind: "error"; key: string; text: string };
