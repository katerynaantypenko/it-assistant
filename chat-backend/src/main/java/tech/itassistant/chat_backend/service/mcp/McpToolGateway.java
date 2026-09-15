package tech.itassistant.chat_backend.service.mcp;

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.log4j.Log4j2;
import tech.itassistant.chat_backend.service.ApprovalRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The MCP side of one agent run: the open MCP session, the tool catalogue it advertises, and the
 * single method through which tools are executed.
 *
 * <p>This class is the enforcement point. {@link #callTool} refuses to execute a state-changing
 * tool without an approval for that exact tool call, so the rule holds even if the model never
 * asks and even if the agent loop has a bug. The model is not a trusted component.
 */
@Log4j2
public class McpToolGateway implements AutoCloseable {

    private static final String APPROVAL_NOTE =
            " This tool changes the knowledge base, so the user has to approve the call before it runs.";

    private final McpSyncClient mcpClient;
    private final Map<String, Tool> toolsByName;

    McpToolGateway(McpSyncClient mcpClient) {
        this.mcpClient = mcpClient;
        this.toolsByName = mcpClient.listTools().tools().stream()
                .collect(Collectors.toMap(Tool::name, tool -> tool, (first, second) -> first, LinkedHashMap::new));
        log.info("McpToolGateway() : MCP session ready, tools={}", toolsByName.keySet());
    }

    /**
     * Whether a tool needs approval. The answer comes from the MCP server's own {@code readOnlyHint}
     * annotation, so the two processes cannot drift apart. An unknown tool counts as sensitive,
     * which keeps a newly added state-changing tool safe by default.
     */
    public boolean isSensitive(String toolName) {
        Tool tool = toolsByName.get(toolName);
        return tool == null
                || tool.annotations() == null
                || !Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    /**
     * The MCP tool catalogue translated into OpenAI function tools. Sensitive tools also say so in
     * their description - that is a hint for the model, not the enforcement.
     */
    public List<ChatCompletionFunctionTool> openAiTools() {
        return toolsByName.values().stream().map(this::toOpenAiTool).toList();
    }

    /**
     * Executes a tool on the MCP server. Server-side check at call time, not at planning time.
     *
     * @throws ToolApprovalRequiredException when a sensitive tool has no approval for this call id
     */
    public CallToolResult callTool(String toolCallId, String toolName, Map<String, Object> arguments,
                                   ApprovalRegistry approvals) {
        if (isSensitive(toolName) && !approvals.consumeApproval(toolCallId)) {
            log.warn("callTool() : Blocked unapproved call to '{}' (toolCallId={})", toolName, toolCallId);
            throw new ToolApprovalRequiredException(toolName);
        }

        log.info("callTool() : REQUEST : tool={} : arguments={}", toolName, arguments);
        CallToolResult result = mcpClient.callTool(CallToolRequest.builder(toolName)
                .arguments(arguments)
                .build());
        log.info("callTool() : RESPONSE : tool={} : isError={}", toolName, result.isError());
        return result;
    }

    /** Tool output arrives as content blocks; the agent and the web client both want plain text. */
    public static String resultText(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        mcpClient.closeGracefully();
    }

    private ChatCompletionFunctionTool toOpenAiTool(Tool tool) {
        // The MCP input schema is already a JSON Schema object, so it is copied over as it is.
        FunctionParameters.Builder parameters = FunctionParameters.builder();
        tool.inputSchema().forEach((key, value) -> parameters.putAdditionalProperty(key, JsonValue.from(value)));

        String description = tool.description() == null ? tool.name() : tool.description();
        if (isSensitive(tool.name())) {
            description = description + APPROVAL_NOTE;
        }

        return ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(tool.name())
                        .description(description)
                        .parameters(parameters.build())
                        .build())
                .build();
    }
}
