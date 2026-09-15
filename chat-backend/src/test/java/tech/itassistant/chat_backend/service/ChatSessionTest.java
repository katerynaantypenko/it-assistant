package tech.itassistant.chat_backend.service;

import com.openai.core.ObjectMappers;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The history of a conversation is replayed on every request, so one malformed entry breaks every
 * later turn rather than the turn that produced it. These tests build the assistant answer the way
 * the streaming accumulator does and check what would actually go back over the wire.
 */
class ChatSessionTest {

    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

    @Test
    void leavesOutToolCallsWhenTheModelJustAnswered() {
        ChatSession session = new ChatSession("olena@example.com", SYSTEM_PROMPT);
        session.addAssistantAnswer(streamed("All set.", null));

        // The accumulator reports "no tool calls" as an empty list, which the API rejects as
        // "Invalid 'messages[n].tool_calls': empty array" on the next turn of the conversation.
        assertThat(json(session, 1)).contains("All set.").doesNotContain("tool_calls");
    }

    @Test
    void keepsTheToolCallsTheModelAskedFor() {
        ChatSession session = new ChatSession("olena@example.com", SYSTEM_PROMPT);
        session.addAssistantAnswer(streamed(null, "search_articles"));

        assertThat(json(session, 1)).contains("tool_calls").contains("search_articles");
    }

    private String json(ChatSession session, int index) {
        ChatCompletionMessageParam message = session.messageHistory().get(index);
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("The message could not be serialized.", e);
        }
    }

    /** Streams one answer through the real accumulator, with text, a tool call, or both. */
    private ChatCompletionMessage streamed(String text, String toolName) {
        ChatCompletionChunk.Choice.Delta.Builder delta = ChatCompletionChunk.Choice.Delta.builder()
                .role(ChatCompletionChunk.Choice.Delta.Role.ASSISTANT);
        if (text != null) {
            delta.content(text);
        }
        if (toolName != null) {
            delta.addToolCall(ChatCompletionChunk.Choice.Delta.ToolCall.builder()
                    .index(0)
                    .id("call-1")
                    .type(ChatCompletionChunk.Choice.Delta.ToolCall.Type.FUNCTION)
                    .function(ChatCompletionChunk.Choice.Delta.ToolCall.Function.builder()
                            .name(toolName)
                            .arguments("{\"query\":\"vpn\"}")
                            .build())
                    .build());
        }

        ChatCompletionChunk chunk = ChatCompletionChunk.builder()
                .id("chunk-1")
                .created(0L)
                .model("gpt-4o-mini")
                .choices(List.of(ChatCompletionChunk.Choice.builder()
                        .index(0)
                        .delta(delta.build())
                        .finishReason(toolName == null
                                ? ChatCompletionChunk.Choice.FinishReason.STOP
                                : ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS)
                        .build()))
                .build();

        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        accumulator.accumulate(chunk);
        return accumulator.chatCompletion().choices().get(0).message();
    }
}
