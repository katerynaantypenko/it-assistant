package tech.itassistant.chat_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Log4j2
public class OpenAiConfig {

    private static final Duration OPENAI_TIMEOUT = Duration.ofSeconds(120);

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Bean
    public OpenAIClient openAIClient() {
        log.info("openAIClient() : Using model {}", model);
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(OPENAI_TIMEOUT)
                .build();
    }

    /** Spring Boot 3.x uses Jackson 2, so the MCP SDK is bound to the application ObjectMapper. */
    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }
}
