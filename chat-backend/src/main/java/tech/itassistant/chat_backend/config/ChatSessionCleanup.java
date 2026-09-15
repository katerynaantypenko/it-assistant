package tech.itassistant.chat_backend.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tech.itassistant.chat_backend.service.ChatSessionRegistry;

/**
 * Drops the conversation when the browser session ends, so a logout really starts over.
 */
@Component
@RequiredArgsConstructor
public class ChatSessionCleanup implements HttpSessionListener {

    private final ChatSessionRegistry chatSessionRegistry;

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        chatSessionRegistry.remove(event.getSession().getId());
    }
}
