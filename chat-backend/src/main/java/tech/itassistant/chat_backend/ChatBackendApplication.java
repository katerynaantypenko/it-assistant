package tech.itassistant.chat_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chat backend: signs the user in with OIDC, runs the OpenAI agent loop and acts as the MCP client
 * for the knowledge base server. Tool approval is enforced here, because this is the only component
 * that both talks to the model and executes the tools.
 */
@SpringBootApplication
public class ChatBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatBackendApplication.class, args);
    }
}
