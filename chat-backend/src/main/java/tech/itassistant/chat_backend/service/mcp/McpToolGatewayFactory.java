package tech.itassistant.chat_backend.service.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Opens an MCP session against the knowledge base server.
 *
 * <p>A session is opened per agent run and carries the access token of the user who started that
 * run, which is how the signed-in identity reaches the other process. Opening it per run also keeps
 * the token current: a long-lived shared session would keep sending the token of whoever opened it
 * first, and that token would be rejected as soon as it expired.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class McpToolGatewayFactory {

    private static final String CLIENT_NAME = "it-assistant-chat-backend";
    private static final String CLIENT_VERSION = "1.0.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final McpJsonMapper mcpJsonMapper;

    @Value("${mcp.kb-server.url}")
    private String serverUrl;

    @Value("${mcp.kb-server.endpoint}")
    private String serverEndpoint;

    public McpToolGateway open(String accessToken) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(serverUrl)
                .endpoint(serverEndpoint)
                .jsonMapper(mcpJsonMapper)
                .httpRequestCustomizer((request, method, uri, body, context) ->
                        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .build();

        McpSyncClient mcpClient = McpClient.sync(transport)
                .clientInfo(Implementation.builder(CLIENT_NAME, CLIENT_VERSION).build())
                .requestTimeout(REQUEST_TIMEOUT)
                .build();

        log.info("open() : Connecting to the MCP server at {}{}", serverUrl, serverEndpoint);
        mcpClient.initialize();
        return new McpToolGateway(mcpClient);
    }
}
