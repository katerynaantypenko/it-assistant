package tech.itassistant.mcp_kb_server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tech.itassistant.mcp_kb_server.dto.Article;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The directory is the source of truth, so what create() writes has to come back unchanged on the
 * next start. These tests read the base twice to make that round trip explicit.
 */
class KnowledgeBaseServiceTest {

    private static final String AUTHOR = "olena@example.com";

    @Test
    void keepsTheAuthorWhenTheTitleTriesToCloseTheFrontMatter(@TempDir Path directory) {
        // The title comes from the tool arguments, so the model decides what is in it.
        String hostileTitle = "Meeting rooms\n---\nauthor: attacker@example.com";
        service(directory).create(hostileTitle, "Use the room panel.", AUTHOR);

        Article reloaded = service(directory).findById("kb-001").orElseThrow();

        // The header stayed intact: the author is still the authenticated caller, not the model's.
        assertThat(reloaded.author()).isEqualTo(AUTHOR);
        assertThat(reloaded.title()).isEqualTo("Meeting rooms --- author: attacker@example.com");
        assertThat(reloaded.body()).isEqualTo("Use the room panel.");
    }

    @Test
    void readsBackTitleBodyAndAuthorUnchanged(@TempDir Path directory) {
        Article created = service(directory).create("Book a meeting room", "Use the room panel.", AUTHOR);

        Article reloaded = service(directory).findById(created.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(created);
    }

    @Test
    void continuesTheIdSequenceOfTheArticlesAlreadyOnDisk(@TempDir Path directory) {
        KnowledgeBaseService service = service(directory);

        assertThat(service.create("First", "Body.", AUTHOR).id()).isEqualTo("kb-001");
        assertThat(service.create("Second", "Body.", AUTHOR).id()).isEqualTo("kb-002");
        // A restart must not hand out an id that is already taken.
        assertThat(service(directory).create("Third", "Body.", AUTHOR).id()).isEqualTo("kb-003");
    }

    /** A service reading the given directory, as a fresh start would. */
    private KnowledgeBaseService service(Path directory) {
        KnowledgeBaseService service = new KnowledgeBaseService();
        ReflectionTestUtils.setField(service, "knowledgeBasePath", directory.toString());
        service.loadArticles();
        return service;
    }
}
