package tech.itassistant.chat_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.itassistant.chat_backend.dto.ToolCallEvent;

import java.util.Map;

/**
 * The whole backend to frontend contract for one agent run, in one place.
 *
 * <p>Event names: {@code token} for a piece of assistant text, {@code tool} for every tool status
 * change, {@code error} for a run that could not finish, {@code done} when the run is over.
 */
@RequiredArgsConstructor
@Log4j2
public class ChatEventSender {

    private final SseEmitter emitter;

    public void token(String text) {
        send("token", Map.of("text", text));
    }

    public void tool(ToolCallEvent event) {
        send("tool", event);
    }

    public void error(String message) {
        send("error", Map.of("message", message));
    }

    public void done() {
        send("done", Map.of());
    }

    private void send(String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // The browser reloaded or closed the tab. There is nothing to recover, the run simply
            // finishes without a viewer.
            log.warn("send() : Cannot deliver the '{}' event: {}", name, e.getMessage());
        }
    }
}
