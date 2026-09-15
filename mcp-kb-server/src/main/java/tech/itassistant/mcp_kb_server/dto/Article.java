package tech.itassistant.mcp_kb_server.dto;

import java.util.List;

/**
 * One knowledge base article, as stored in a markdown file.
 * Everything above the '---' fence is the header, everything below it is the body.
 */
public record Article(
        String id,
        String title,
        List<String> tags,
        String author,
        String created,
        String body) {
}
