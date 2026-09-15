package tech.itassistant.chat_backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat sessions, one per browser session. Nothing is persisted.
 */
@Component
@Log4j2
public class ChatSessionRegistry {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    public void readSystemPrompt() {
        try (InputStreamReader reader = new InputStreamReader(systemPromptResource.getInputStream(),
                StandardCharsets.UTF_8)) {
            systemPrompt = FileCopyUtils.copyToString(reader).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the system prompt", e);
        }
        log.info("readSystemPrompt() : System prompt loaded, {} characters", systemPrompt.length());
    }

    public ChatSession get(String httpSessionId, String userEmail) {
        return sessions.computeIfAbsent(httpSessionId, id -> {
            log.info("get() : New chat session for {}", userEmail);
            return new ChatSession(userEmail, systemPrompt);
        });
    }

    public void remove(String httpSessionId) {
        ChatSession removed = sessions.remove(httpSessionId);
        if (removed != null) {
            log.info("remove() : Chat session of {} discarded", removed.getUserEmail());
        }
    }
}
