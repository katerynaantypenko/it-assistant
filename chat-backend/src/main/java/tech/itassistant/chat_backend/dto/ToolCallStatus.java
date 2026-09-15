package tech.itassistant.chat_backend.dto;

/**
 * Lifecycle of one tool call as the web client sees it.
 */
public enum ToolCallStatus {

    /** A state-changing tool is blocked and waits for the user to approve or deny it. */
    AWAITING_APPROVAL,

    /** The call has been handed to the MCP server. */
    IN_FLIGHT,

    /** The MCP server returned a result. */
    SUCCEEDED,

    /** The tool reported an error, or the call itself failed. */
    FAILED,

    /** The user denied the call, so it was never executed. */
    DENIED
}
