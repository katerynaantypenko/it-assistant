package tech.itassistant.chat_backend.service.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.itassistant.chat_backend.service.ApprovalRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The approval rule is enforced server-side at call time, so it is worth testing without a model,
 * a browser or a running MCP server in the picture.
 */
class McpToolGatewayTest {

    private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object");
    private static final String TOOL_CALL_ID = "call-1";

    private McpSyncClient mcpClient;
    private McpToolGateway gateway;
    private ApprovalRegistry approvals;

    @BeforeEach
    void setUp() {
        Tool readOnlyTool = Tool.builder("search_articles", EMPTY_SCHEMA)
                .description("Reads the knowledge base.")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();
        Tool sensitiveTool = Tool.builder("create_article", EMPTY_SCHEMA)
                .description("Writes a new article.")
                .annotations(ToolAnnotations.builder().readOnlyHint(false).build())
                .build();

        mcpClient = mock(McpSyncClient.class);
        when(mcpClient.listTools()).thenReturn(new ListToolsResult(List.of(readOnlyTool, sensitiveTool), null, null));
        when(mcpClient.callTool(any(CallToolRequest.class))).thenReturn(
                CallToolResult.builder(List.of(TextContent.builder("done").build())).isError(false).build());

        gateway = new McpToolGateway(mcpClient);
        approvals = new ApprovalRegistry();
    }

    @Test
    void derivesSensitivityFromTheReadOnlyHintOfTheMcpServer() {
        assertThat(gateway.isSensitive("search_articles")).isFalse();
        assertThat(gateway.isSensitive("create_article")).isTrue();
        // A tool the server does not advertise is treated as sensitive.
        assertThat(gateway.isSensitive("delete_everything")).isTrue();
    }

    @Test
    void executesAReadOnlyToolWithoutAnyApproval() {
        CallToolResult result = gateway.callTool(TOOL_CALL_ID, "search_articles", Map.of("query", "vpn"), approvals);

        assertThat(McpToolGateway.resultText(result)).isEqualTo("done");
        verify(mcpClient).callTool(any(CallToolRequest.class));
    }

    @Test
    void refusesToExecuteASensitiveToolThatWasNotApproved() {
        assertThatThrownBy(() ->
                gateway.callTool(TOOL_CALL_ID, "create_article", Map.of("title", "x", "body", "y"), approvals))
                .isInstanceOf(ToolApprovalRequiredException.class);

        // The point of the exercise: the MCP server never saw the call.
        verify(mcpClient, never()).callTool(any(CallToolRequest.class));
    }

    @Test
    void executesAnApprovedSensitiveToolExactlyOnce() {
        // The user's decision has to reach a call that is already waiting for it.
        Thread agentRun = new Thread(() -> approvals.awaitDecision(TOOL_CALL_ID, Duration.ofSeconds(5)));
        agentRun.start();
        await(() -> approvals.decide(TOOL_CALL_ID, true));

        gateway.callTool(TOOL_CALL_ID, "create_article", Map.of("title", "x", "body", "y"), approvals);
        verify(mcpClient).callTool(any(CallToolRequest.class));

        // An approval is single use, so replaying the same id is refused.
        assertThatThrownBy(() ->
                gateway.callTool(TOOL_CALL_ID, "create_article", Map.of("title", "x", "body", "y"), approvals))
                .isInstanceOf(ToolApprovalRequiredException.class);
    }

    /** Retries the decision until the waiting thread has registered itself. */
    private void await(java.util.function.BooleanSupplier decision) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (decision.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("The tool call never started waiting for a decision.");
    }
}
