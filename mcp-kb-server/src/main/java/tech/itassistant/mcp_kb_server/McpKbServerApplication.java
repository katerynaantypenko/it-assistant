package tech.itassistant.mcp_kb_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP server for the internal IT knowledge base.
 * Runs as its own process and speaks MCP over Streamable HTTP on /mcp.
 */
@SpringBootApplication
public class McpKbServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpKbServerApplication.class, args);
    }
}
