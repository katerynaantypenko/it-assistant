package tech.itassistant.mcp_kb_server.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.itassistant.mcp_kb_server.dto.Article;
import tech.itassistant.mcp_kb_server.dto.ArticleSummary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * The knowledge base itself: markdown files in one directory.
 * Files are read once at startup and kept in memory; a new article is added to memory and
 * written back as a markdown file, so the directory stays the source of truth after a restart.
 */
@Service
@Log4j2
public class KnowledgeBaseService {

    private static final String FRONT_MATTER_FENCE = "---";
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final int SNIPPET_LENGTH = 220;
    private static final int MAX_SLUG_WORDS = 6;

    /** Front matter keys. Reading and writing share them, so the two cannot drift apart. */
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_CREATED = "created";

    @Value("${knowledge-base.path}")
    private String knowledgeBasePath;

    /** Sorted by article id, so listings and generated ids are stable. */
    private final Map<String, Article> articles = new ConcurrentSkipListMap<>();

    @PostConstruct
    public void loadArticles() {
        Path directory = Path.of(knowledgeBasePath).toAbsolutePath().normalize();
        log.info("loadArticles() : Reading the knowledge base from {}", directory);

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(MARKDOWN_EXTENSION))
                    .sorted()
                    .map(this::readArticle)
                    .forEach(article -> articles.put(article.id(), article));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the knowledge base directory " + directory, e);
        }

        log.info("loadArticles() : Loaded {} articles: {}", articles.size(), articles.keySet());
    }

    /**
     * Plain keyword matching: an article is a hit when it contains at least one of the query words.
     * Ranking is the number of matched words, which is good enough - retrieval quality is out of scope.
     */
    public List<ArticleSummary> search(String query, int limit) {
        List<String> words = words(query);
        if (words.isEmpty()) {
            return List.of();
        }

        return articles.values().stream()
                .map(article -> Map.entry(article, countMatches(article, words)))
                .filter(hit -> hit.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Article, Long>>comparingLong(Map.Entry::getValue).reversed())
                .limit(limit)
                .map(hit -> new ArticleSummary(hit.getKey().id(), hit.getKey().title(), snippet(hit.getKey(), words)))
                .toList();
    }

    public Optional<Article> findById(String articleId) {
        return Optional.ofNullable(articles.get(articleId.trim()));
    }

    /**
     * Writes a new article and records who wrote it. This is the state-changing operation, so the
     * author comes from the authenticated caller and not from the tool arguments.
     */
    public synchronized Article create(String title, String body, String author) {
        String id = nextArticleId();
        Article article = new Article(id, headerValue(title), List.of(), headerValue(author),
                Instant.now().toString(), body.trim());

        Path file = Path.of(knowledgeBasePath).resolve(id + "-" + slug(article.title()) + MARKDOWN_EXTENSION);
        try {
            Files.writeString(file, render(article), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the article to " + file, e);
        }

        articles.put(id, article);
        log.info("create() : Article {} written to {} by {}", id, file.getFileName(), author);
        return article;
    }

    private Article readArticle(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the article " + file, e);
        }

        Map<String, String> header = new LinkedHashMap<>();
        int bodyStart = 0;

        if (!lines.isEmpty() && FRONT_MATTER_FENCE.equals(lines.get(0).trim())) {
            for (int i = 1; i < lines.size(); i++) {
                if (FRONT_MATTER_FENCE.equals(lines.get(i).trim())) {
                    bodyStart = i + 1;
                    break;
                }
                // Only the first colon separates the key from the value, so timestamps stay intact.
                int colon = lines.get(i).indexOf(':');
                if (colon > 0) {
                    header.put(lines.get(i).substring(0, colon).trim(), lines.get(i).substring(colon + 1).trim());
                }
            }
        }

        String fileName = file.getFileName().toString();
        return new Article(
                header.getOrDefault(KEY_ID, fileName.replace(MARKDOWN_EXTENSION, "")),
                header.getOrDefault(KEY_TITLE, fileName),
                tags(header.get(KEY_TAGS)),
                header.getOrDefault(KEY_AUTHOR, "unknown"),
                header.getOrDefault(KEY_CREATED, ""),
                String.join("\n", lines.subList(bodyStart, lines.size())).trim());
    }

    /** Every header value is a single line, which create() guarantees before the article is built. */
    private String render(Article article) {
        return FRONT_MATTER_FENCE + "\n"
                + KEY_ID + ": " + article.id() + "\n"
                + KEY_TITLE + ": " + article.title() + "\n"
                + KEY_TAGS + ": " + String.join(", ", article.tags()) + "\n"
                + KEY_AUTHOR + ": " + article.author() + "\n"
                + KEY_CREATED + ": " + article.created() + "\n"
                + FRONT_MATTER_FENCE + "\n\n"
                + article.body() + "\n";
    }

    /** Ids look like kb-011. The next one continues the highest number already in the base. */
    private String nextArticleId() {
        int highest = articles.keySet().stream()
                .map(id -> id.replaceAll("\\D", ""))
                .filter(digits -> !digits.isEmpty())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return String.format("kb-%03d", highest + 1);
    }

    private long countMatches(Article article, List<String> words) {
        String haystack = (article.title() + " " + String.join(" ", article.tags()) + " " + article.body())
                .toLowerCase(Locale.ROOT);
        return words.stream().filter(haystack::contains).count();
    }

    private String snippet(Article article, List<String> words) {
        String body = article.body().replaceAll("\\s+", " ").trim();
        String lowerCaseBody = body.toLowerCase(Locale.ROOT);

        int position = words.stream()
                .mapToInt(lowerCaseBody::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(0);

        int from = Math.max(0, position - SNIPPET_LENGTH / 4);
        int to = Math.min(body.length(), from + SNIPPET_LENGTH);
        return (from > 0 ? "..." : "") + body.substring(from, to) + (to < body.length() ? "..." : "");
    }

    private List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2)
                .distinct()
                .toList();
    }

    /**
     * Collapses a value to a single line before it goes into the front matter. The title arrives from
     * the tool arguments, so the model controls it: a line break in it would close the header early,
     * and everything below - including the author - would be read back as body text.
     */
    private String headerValue(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private List<String> tags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(tag -> !tag.isEmpty()).toList();
    }

    private String slug(String title) {
        List<String> parts = new ArrayList<>(Arrays.asList(title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .split("-")));
        return String.join("-", parts.subList(0, Math.min(parts.size(), MAX_SLUG_WORDS)));
    }
}
