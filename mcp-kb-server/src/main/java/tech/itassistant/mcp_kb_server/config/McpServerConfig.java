package tech.itassistant.mcp_kb_server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.itassistant.mcp_kb_server.tool.KnowledgeBaseTools;
import tech.itassistant.mcp_kb_server.util.CallerIdentity;

/**
 * Wires the MCP server onto the Streamable HTTP transport.
 *
 * <p>The transport provider is a plain servlet, so it is registered on /mcp and goes through the
 * Spring Security filter chain like any other request - which is how the caller identity gets in.
 */
@Configuration
@Log4j2
@RequiredArgsConstructor
public class McpServerConfig {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String SERVER_NAME = "it-knowledge-base";
    private static final String SERVER_VERSION = "1.0.0";

    private final KnowledgeBaseTools knowledgeBaseTools;

    /** Spring Boot 3.x uses Jackson 2, so the MCP SDK is bound to the application ObjectMapper. */
    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper mcpJsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                // Runs on the servlet thread, where the validated JWT is still in the security context.
                // The identity is copied into the transport context so tool handlers can read it later.
                .contextExtractor(request -> CallerIdentity.fromSecurityContext().toTransportContext())
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider mcpTransportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(mcpTransportProvider, MCP_ENDPOINT);
        registration.setName("mcpServlet");
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider mcpTransportProvider) {
        log.info("mcpSyncServer() : Starting MCP server {} {} on {}", SERVER_NAME, SERVER_VERSION, MCP_ENDPOINT);
        return McpServer.sync(mcpTransportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(false)
                        .build())
                .tools(knowledgeBaseTools.searchArticles(),
                        knowledgeBaseTools.getArticle(),
                        knowledgeBaseTools.createArticle())
                .build();
    }
}
