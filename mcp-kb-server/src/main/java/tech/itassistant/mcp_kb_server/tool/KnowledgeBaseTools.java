package tech.itassistant.mcp_kb_server.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tech.itassistant.mcp_kb_server.dto.Article;
import tech.itassistant.mcp_kb_server.dto.ArticleSummary;
import tech.itassistant.mcp_kb_server.service.KnowledgeBaseService;
import tech.itassistant.mcp_kb_server.util.CallerIdentity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The three tools this MCP server exposes.
 *
 * <p>The {@code readOnlyHint} annotation is what tells a client whether a tool changes state.
 * The chat backend reads it from the tool list and requires user approval for anything that is
 * not read-only, so this annotation is part of the contract and not just documentation.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class KnowledgeBaseTools {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 20;

    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public SyncToolSpecification searchArticles() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Keywords to look for, for example 'vpn certificate'."),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Maximum number of articles to return, 1 to " + MAX_SEARCH_LIMIT + ".")),
                "required", List.of("query"),
                "additionalProperties", false);

        Tool tool = Tool.builder("search_articles", schema)
                .title("Search knowledge base articles")
                .description("Searches the internal IT knowledge base by keyword and returns matching "
                        + "article ids with their titles and a short snippet.")
                .annotations(ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    CallerIdentity caller = CallerIdentity.from(exchange.transportContext());
                    String query = stringArgument(request, "query");
                    int limit = intArgument(request, "limit");

                    log.info("search_articles : REQUEST : caller={} : query='{}' : limit={}",
                            caller.email(), query, limit);

                    List<ArticleSummary> hits = knowledgeBaseService.search(query, limit);
                    log.info("search_articles : RESPONSE : caller={} : hits={}", caller.email(), hits.size());
                    return jsonResult(Map.of("count", hits.size(), "articles", hits));
                })
                .build();
    }

    public SyncToolSpecification getArticle() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "article_id", Map.of(
                                "type", "string",
                                "description", "Id of the article, for example 'kb-002'.")),
                "required", List.of("article_id"),
                "additionalProperties", false);

        Tool tool = Tool.builder("get_article", schema)
                .title("Read one knowledge base article")
                .description("Returns the full markdown text of one knowledge base article by its id.")
                .annotations(ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    CallerIdentity caller = CallerIdentity.from(exchange.transportContext());
                    String articleId = stringArgument(request, "article_id");

                    log.info("get_article : REQUEST : caller={} : articleId={}", caller.email(), articleId);

                    Optional<Article> article = knowledgeBaseService.findById(articleId);
                    if (article.isEmpty()) {
                        log.info("get_article : RESPONSE : caller={} : articleId={} not found",
                                caller.email(), articleId);
                        return errorResult("There is no article with id '" + articleId
                                + "'. Use search_articles to find a valid id.");
                    }

                    log.info("get_article : RESPONSE : caller={} : articleId={}", caller.email(), articleId);
                    return jsonResult(article.get());
                })
                .build();
    }

    /**
     * The state-changing tool. It is marked as not read-only, which is what makes the chat backend
     * ask the user for approval before it is ever executed.
     */
    public SyncToolSpecification createArticle() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of(
                                "type", "string",
                                "description", "Short title of the new article."),
                        "body", Map.of(
                                "type", "string",
                                "description", "Full article text in markdown.")),
                "required", List.of("title", "body"),
                "additionalProperties", false);

        Tool tool = Tool.builder("create_article", schema)
                .title("Write a new knowledge base article")
                .description("Creates a new article in the internal IT knowledge base and records the "
                        + "signed-in user as its author.")
                .annotations(ToolAnnotations.builder()
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(false)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    CallerIdentity caller = CallerIdentity.from(exchange.transportContext());
                    String title = stringArgument(request, "title");
                    String body = stringArgument(request, "body");

                    log.info("create_article : REQUEST : caller={} : title='{}'", caller.email(), title);

                    if (title.isBlank() || body.isBlank()) {
                        return errorResult("Both 'title' and 'body' are required and must not be empty.");
                    }

                    Article article = knowledgeBaseService.create(title, body, caller.email());
                    log.info("create_article : RESPONSE : caller={} : articleId={}", caller.email(), article.id());
                    return jsonResult(Map.of(
                            "id", article.id(),
                            "title", article.title(),
                            "author", article.author(),
                            "created", article.created()));
                })
                .build();
    }

    private String stringArgument(CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        return value == null ? "" : value.toString();
    }

    private int intArgument(CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof Number number)) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.clamp(number.intValue(), 1, MAX_SEARCH_LIMIT);
    }

    /** Tool results travel as text, so structured payloads are serialized to JSON. */
    private CallToolResult jsonResult(Object payload) {
        try {
            TextContent content = TextContent.builder(objectMapper.writeValueAsString(payload)).build();
            return CallToolResult.builder(List.of(content))
                    .isError(false)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("jsonResult() : ERROR : {}", e.getMessage(), e);
            return errorResult("Cannot serialize the tool result: " + e.getMessage());
        }
    }

    /**
     * A tool-level error: the model sees it as a normal response and can correct itself,
     * instead of the whole JSON-RPC call failing.
     */
    private CallToolResult errorResult(String message) {
        return CallToolResult.builder(List.of(TextContent.builder(message).build()))
                .isError(true)
                .build();
    }
}
