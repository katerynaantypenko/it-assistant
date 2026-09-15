package tech.itassistant.chat_backend.dto;

import java.util.Map;

/**
 * One tool activity update pushed to the browser. The same id is reported for every status change,
 * so the web client can update the card it already shows instead of adding a new one.
 *
 * @param arguments the exact arguments that will be executed - this is what the user approves
 * @param result    tool output, error text or the reason for a denial; null while still running
 */
public record ToolCallEvent(
        String id,
        String name,
        Map<String, Object> arguments,
        ToolCallStatus status,
        boolean sensitive,
        String result) {

    public static ToolCallEvent of(String id, String name, Map<String, Object> arguments,
                                   ToolCallStatus status, boolean sensitive) {
        return new ToolCallEvent(id, name, arguments, status, sensitive, null);
    }

    public ToolCallEvent withResult(ToolCallStatus newStatus, String newResult) {
        return new ToolCallEvent(id, name, arguments, newStatus, sensitive, newResult);
    }
}
