package tech.itassistant.mcp_kb_server.dto;

/**
 * Search hit: enough for the agent to decide whether it wants the full article.
 */
public record ArticleSummary(
        String id,
        String title,
        String snippet) {
}
