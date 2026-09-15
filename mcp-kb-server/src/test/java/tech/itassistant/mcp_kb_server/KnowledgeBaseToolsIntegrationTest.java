package tech.itassistant.mcp_kb_server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MCP server the way the chat backend does: over Streamable HTTP, with a bearer token.
 * The token is decoded by a stub decoder, so the test needs no OIDC provider - everything else
 * (transport, security chain, identity propagation, tools) is the real thing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "knowledge-base.path=target/test-knowledge-base",
        "spring.main.allow-bean-definition-overriding=true"
})
class KnowledgeBaseToolsIntegrationTest {

    private static final String CALLER_EMAIL = "tester@example.com";

    @Value("${local.server.port}")
    private int port;

    private McpSyncClient mcpClient;

    @BeforeEach
    void connect() {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .httpRequestCustomizer((request, method, uri, body, context) ->
                                request.header(HttpHeaders.AUTHORIZATION, "Bearer any-token"))
                        .build();

        mcpClient = McpClient.sync(transport).build();
        mcpClient.initialize();
    }

    @AfterEach
    void disconnect() {
        mcpClient.closeGracefully();
    }

    @Test
    void advertisesReadOnlyHintsThatDriveTheApprovalRule() {
        Map<String, Tool> tools = mcpClient.listTools().tools().stream()
                .collect(Collectors.toMap(Tool::name, tool -> tool));

        assertThat(tools.keySet()).containsExactlyInAnyOrder("search_articles", "get_article", "create_article");
        assertThat(tools.get("search_articles").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("get_article").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("create_article").annotations().readOnlyHint()).isFalse();
    }

    @Test
    void findsAndReadsAnArticle() {
        CallToolResult found = mcpClient.callTool(CallToolRequest.builder("search_articles")
                .arguments(Map.of("query", "vpn certificate", "limit", 3))
                .build());
        assertThat(text(found)).contains("kb-002");

        CallToolResult article = mcpClient.callTool(CallToolRequest.builder("get_article")
                .arguments(Map.of("article_id", "kb-002"))
                .build());
        assertThat(text(article)).contains("vpn.example.com");
    }

    @Test
    void recordsTheAuthenticatedCallerAsTheAuthorOfANewArticle() {
        CallToolResult created = mcpClient.callTool(CallToolRequest.builder("create_article")
                .arguments(Map.of("title", "Book a meeting room", "body", "Use the room panel."))
                .build());

        // The author is taken from the token, not from the tool arguments.
        assertThat(text(created)).contains(CALLER_EMAIL);
    }

    @Test
    void reportsAnUnknownArticleAsAToolError() {
        CallToolResult result = mcpClient.callTool(CallToolRequest.builder("get_article")
                .arguments(Map.of("article_id", "kb-999"))
                .build());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("kb-999");
    }

    private String text(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .findFirst()
                .orElse("");
    }

    /** Replaces the real decoder so the test does not need a running identity provider. */
    @TestConfiguration
    static class StubJwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("user-1")
                    .claim("email", CALLER_EMAIL)
                    .claim("name", "Test User")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .audience(List.of("test-client-id"))
                    .build();
        }
    }
}
