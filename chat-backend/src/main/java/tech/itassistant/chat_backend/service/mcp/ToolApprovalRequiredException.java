package tech.itassistant.chat_backend.service.mcp;

/**
 * Thrown when a state-changing tool is executed without a matching user approval.
 * Reaching this exception means the enforcement in {@link McpToolGateway} did its job.
 */
public class ToolApprovalRequiredException extends RuntimeException {

    public ToolApprovalRequiredException(String toolName) {
        super("The tool '" + toolName + "' changes the knowledge base and was not approved by the user.");
    }
}
