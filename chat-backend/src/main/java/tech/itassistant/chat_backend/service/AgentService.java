package tech.itassistant.chat_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.itassistant.chat_backend.dto.ToolCallEvent;
import tech.itassistant.chat_backend.dto.ToolCallStatus;
import tech.itassistant.chat_backend.service.mcp.McpToolGateway;
import tech.itassistant.chat_backend.service.mcp.McpToolGatewayFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The agent loop: ask the model, stream its answer, run the tools it asked for, ask again.
 *
 * <p>Written by hand instead of with an agent framework. The loop is about twenty lines and the
 * interesting part of this task - the approval gate between "the model wants a tool" and "the tool
 * runs" - sits right in the middle of it. A framework would hide exactly that seam.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AgentService {

    private static final String DENIED_TOOL_RESULT =
            "The user denied this tool call, so it was not executed. Do not call it again unless the "
                    + "user asks for it. Tell the user that nothing was changed.";

    private final OpenAIClient openAIClient;
    private final McpToolGatewayFactory gatewayFactory;
    private final ObjectMapper objectMapper;

    @Value("${openai.model}")
    private String model;

    @Value("${agent.max-tool-rounds}")
    private int maxToolRounds;

    @Value("${agent.approval-timeout-seconds}")
    private long approvalTimeoutSeconds;

    public void run(ChatSession session, String accessToken, String userMessage, ChatEventSender events) {
        session.addMessage(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(userMessage).build()));

        try (McpToolGateway gateway = gatewayFactory.open(accessToken)) {
            for (int round = 1; round <= maxToolRounds; round++) {
                ChatCompletionMessage answer = streamAnswer(session, gateway, events);
                session.addAssistantAnswer(answer);

                List<ChatCompletionMessageToolCall> toolCalls = answer.toolCalls().orElse(List.of());
                if (toolCalls.isEmpty()) {
                    return;
                }

                log.info("run() : round={} : the model asked for {} tool call(s)", round, toolCalls.size());
                for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                    executeToolCall(session, gateway, toolCall.asFunction(), events);
                }
            }

            log.warn("run() : Stopped after the limit of {} tool rounds", maxToolRounds);
            events.error("The assistant kept calling tools and the run was stopped after "
                    + maxToolRounds + " rounds.");
        }
    }

    /** One model call, streamed to the browser token by token and accumulated into a full message. */
    private ChatCompletionMessage streamAnswer(ChatSession session, McpToolGateway gateway,
                                               ChatEventSender events) {
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(session.messageHistory());
        for (ChatCompletionFunctionTool tool : gateway.openAiTools()) {
            params.addTool(tool);
        }

        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        try (StreamResponse<ChatCompletionChunk> stream =
                     openAIClient.chat().completions().createStreaming(params.build())) {
            stream.stream()
                    .peek(accumulator::accumulate)
                    .flatMap(chunk -> chunk.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .forEach(events::token);
        }

        return accumulator.chatCompletion().choices().get(0).message();
    }

    /**
     * Runs one tool call: ask for approval if it is sensitive, execute it, report every status change
     * to the browser and feed the outcome back to the model.
     */
    private void executeToolCall(ChatSession session, McpToolGateway gateway,
                                 ChatCompletionMessageFunctionToolCall toolCall, ChatEventSender events) {
        String toolCallId = toolCall.id();
        String toolName = toolCall.function().name();
        Map<String, Object> arguments = parseArguments(toolCall.function().arguments());
        boolean sensitive = gateway.isSensitive(toolName);

        ToolCallEvent event = ToolCallEvent.of(toolCallId, toolName, arguments,
                sensitive ? ToolCallStatus.AWAITING_APPROVAL : ToolCallStatus.IN_FLIGHT, sensitive);
        events.tool(event);

        if (sensitive) {
            log.info("executeToolCall() : Waiting for approval of '{}' (toolCallId={})", toolName, toolCallId);
            boolean approved = session.getApprovals()
                    .awaitDecision(toolCallId, Duration.ofSeconds(approvalTimeoutSeconds));
            if (!approved) {
                log.info("executeToolCall() : '{}' denied by {}", toolName, session.getUserEmail());
                events.tool(event.withResult(ToolCallStatus.DENIED, "Denied by the user, nothing was executed."));
                session.addMessage(toolMessage(toolCallId, DENIED_TOOL_RESULT));
                return;
            }
            event = event.withResult(ToolCallStatus.IN_FLIGHT, null);
            events.tool(event);
        }

        try {
            CallToolResult result = gateway.callTool(toolCallId, toolName, arguments, session.getApprovals());
            String resultText = McpToolGateway.resultText(result);

            boolean failed = Boolean.TRUE.equals(result.isError());
            events.tool(event.withResult(failed ? ToolCallStatus.FAILED : ToolCallStatus.SUCCEEDED, resultText));
            session.addMessage(toolMessage(toolCallId, resultText));
        } catch (Exception e) {
            log.error("executeToolCall() : ERROR : tool={} : {}", toolName, e.getMessage(), e);
            events.tool(event.withResult(ToolCallStatus.FAILED, e.getMessage()));
            session.addMessage(toolMessage(toolCallId, "The tool call failed: " + e.getMessage()));
        }
    }

    private ChatCompletionMessageParam toolMessage(String toolCallId, String content) {
        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                .toolCallId(toolCallId)
                .content(content)
                .build());
    }

    /** The model produces the arguments as a JSON string; malformed JSON is reported as an empty call. */
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("parseArguments() : ERROR : cannot parse '{}' : {}", json, e.getMessage());
            return Map.of();
        }
    }
}
