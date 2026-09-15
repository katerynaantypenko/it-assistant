package tech.itassistant.chat_backend.service;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * One conversation. History lives in memory only and is gone after a restart, which the task allows.
 */
@Getter
public class ChatSession {

    private final String userEmail;
    private final ApprovalRegistry approvals = new ApprovalRegistry();
    private final List<ChatCompletionMessageParam> messages = new ArrayList<>();

    public ChatSession(String userEmail, String systemPrompt) {
        this.userEmail = userEmail;
        this.messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(systemPrompt).build()));
    }

    public void addMessage(ChatCompletionMessageParam message) {
        messages.add(message);
    }

    /**
     * Records what the model answered.
     *
     * <p>The streamed answer is rebuilt field by field instead of being converted wholesale, because
     * the accumulator reports "the model asked for no tools" as an empty list. Sent back as an empty
     * {@code tool_calls} array, that is rejected by the API - and not on the turn that produced it,
     * but on the next one, once the whole history is replayed.
     */
    public void addAssistantAnswer(ChatCompletionMessage answer) {
        ChatCompletionAssistantMessageParam.Builder assistant = ChatCompletionAssistantMessageParam.builder();
        answer.content().ifPresent(assistant::content);
        answer.refusal().ifPresent(assistant::refusal);
        answer.toolCalls().filter(toolCalls -> !toolCalls.isEmpty()).ifPresent(assistant::toolCalls);

        messages.add(ChatCompletionMessageParam.ofAssistant(assistant.build()));
    }

    /** A copy, so a request being built cannot be changed by the next turn. */
    public List<ChatCompletionMessageParam> messageHistory() {
        return List.copyOf(messages);
    }
}
