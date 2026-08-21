package net.tridha.studysheet.service;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.repo.NoteRepository;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Markdown source into HTML for display.
 * Supports GitHub-flavored tables, autolinking, fenced code blocks,
 * and automatically resolves relative markdown file references into clickable document links.
 */
@Service
public class MarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final NoteRepository noteRepository;

    public MarkdownService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                AutolinkExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
    }

    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String sanitized = sanitizeMermaid(markdown);
        Node document = parser.parse(sanitized);
        String rawHtml = renderer.render(document);
        return resolveNoteLinks(rawHtml);
    }

    /**
     * Quotes unquoted Mermaid node labels containing special characters like `/`, `&`, or `,`
     * (e.g. `ID[Text / Info]` -> `ID["Text / Info"]`) to prevent text rendering/parsing glitches.
     */
    public String sanitizeMermaid(String markdown) {
        if (markdown == null || !markdown.contains("```mermaid")) {
            return markdown;
        }
        return markdown.replaceAll("(\\b[A-Za-z0-9_]+)\\[([^\\]\"\\r\\n]+[&/,][^\\]\"\\r\\n]+)\\]", "$1[\"$2\"]");
    }

    /**
     * Resolves relative markdown file references (e.g. `01-reliability.md`, `01-foundations/01-reliability.md`)
     * into direct web application links (`/notes/12`).
     */
    public String resolveNoteLinks(String html) {
        if (html == null || html.isBlank() || noteRepository == null) {
            return html;
        }

        List<Note> notes;
        try {
            notes = noteRepository.findAll();
        } catch (Exception e) {
            return html;
        }

        if (notes.isEmpty()) {
            return html;
        }

        Map<String, Long> linkMap = new HashMap<>();

        for (Note note : notes) {
            Long id = note.getId();
            String title = note.getTitle().toLowerCase().trim();

            linkMap.put(title, id);

            String slug = title.replaceAll("[^a-z0-9]+", "-");
            linkMap.put(slug + ".md", id);

            if (title.contains("what is a data-intensive")) {
                linkMap.put("01-what-is-a-data-system.md", id);
                linkMap.put("00-orientation/01-what-is-a-data-system.md", id);
            } else if (title.contains("reliability")) {
                linkMap.put("01-reliability.md", id);
                linkMap.put("01-foundations/01-reliability.md", id);
            } else if (title.contains("scalability")) {
                linkMap.put("02-scalability.md", id);
                linkMap.put("01-foundations/02-scalability.md", id);
            } else if (title.contains("maintainability")) {
                linkMap.put("03-maintainability.md", id);
                linkMap.put("01-foundations/03-maintainability.md", id);
            } else if (title.contains("relational vs document")) {
                linkMap.put("04-relational-vs-document.md", id);
                linkMap.put("01-foundations/04-relational-vs-document.md", id);
            } else if (title.contains("graph models")) {
                linkMap.put("05-graph-models-and-query-languages.md", id);
                linkMap.put("01-foundations/05-graph-models-and-query-languages.md", id);
            } else if (title.contains("log-structured storage")) {
                linkMap.put("06-log-structured-storage.md", id);
                linkMap.put("01-foundations/06-log-structured-storage.md", id);
            } else if (title.contains("b-trees")) {
                linkMap.put("07-b-trees-and-comparison.md", id);
                linkMap.put("01-foundations/07-b-trees-and-comparison.md", id);
            } else if (title.contains("oltp vs olap")) {
                linkMap.put("08-oltp-olap-column-storage.md", id);
                linkMap.put("01-foundations/08-oltp-olap-column-storage.md", id);
            } else if (title.contains("encoding")) {
                linkMap.put("09-encoding-and-evolution.md", id);
                linkMap.put("01-foundations/09-encoding-and-evolution.md", id);
            } else if (title.contains("single-leader")) {
                linkMap.put("01-single-leader-replication.md", id);
                linkMap.put("02-distributed-data/01-single-leader-replication.md", id);
            } else if (title.contains("foundations of data systems (overview)")) {
                linkMap.put("01-foundations/readme.md", id);
            } else if (title.contains("distributed data systems (overview)")) {
                linkMap.put("02-distributed-data/readme.md", id);
            } else if (title.contains("ddia master learning guide")) {
                linkMap.put("readme.md", id);
            }
        }

        String result = html;
        for (Map.Entry<String, Long> entry : linkMap.entrySet()) {
            String key = entry.getKey();
            Long noteId = entry.getValue();

            result = result.replace("href=\"" + key + "\"", "href=\"/notes/" + noteId + "\"");
            result = result.replace("href=\"./" + key + "\"", "href=\"/notes/" + noteId + "\"");

            String codeTarget = "<code>" + key + "</code>";
            if (result.contains(codeTarget)) {
                result = result.replace(codeTarget,
                        "<a href=\"/notes/" + noteId + "\" class=\"text-indigo-600 dark:text-indigo-400 font-semibold underline hover:text-indigo-800 transition\">" + key + " ↗</a>");
            }
        }

        return result;
    }
}
