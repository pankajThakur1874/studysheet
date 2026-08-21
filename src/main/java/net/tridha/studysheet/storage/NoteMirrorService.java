package net.tridha.studysheet.storage;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Mirrors each note to the {@link ObjectStorage} as a portable Markdown file with YAML
 * frontmatter (Obsidian / Jekyll compatible), so the knowledge base is never trapped in
 * the database — the {@code .md} files can be pulled, git-synced, or opened in any editor.
 * <p>
 * The database remains the source of truth (Option C). Mirroring is therefore best-effort:
 * a storage failure is logged but never propagated, so it can't break a note save.
 */
@Service
public class NoteMirrorService {

    private static final Logger log = LoggerFactory.getLogger(NoteMirrorService.class);

    private final ObjectStorage storage;
    private final boolean enabled;

    public NoteMirrorService(ObjectStorage storage,
                             @Value("${studysheet.mirror.enabled:true}") boolean enabled) {
        this.storage = storage;
        this.enabled = enabled;
    }

    /** Deterministic key so the mirror stays in sync without storing a pointer column. */
    public String keyFor(Long noteId) {
        return "notes/" + noteId + ".md";
    }

    public void mirror(Note note) {
        if (!enabled || note.getId() == null) {
            return;
        }
        try {
            storage.putText(keyFor(note.getId()), toMarkdownFile(note));
        } catch (RuntimeException e) {
            // DB is source of truth — never let a mirror failure break the save.
            log.warn("Failed to mirror note {} to object storage: {}", note.getId(), e.toString());
        }
    }

    public void remove(Long noteId) {
        if (!enabled || noteId == null) {
            return;
        }
        try {
            storage.delete(keyFor(noteId));
        } catch (RuntimeException e) {
            log.warn("Failed to remove mirrored note {}: {}", noteId, e.toString());
        }
    }

    /** Builds a self-describing .md file: YAML frontmatter + the raw Markdown body. */
    String toMarkdownFile(Note note) {
        String tags = note.getTags().stream()
                .map(Tag::getName)
                .map(NoteMirrorService::yamlScalar)
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(yamlScalar(note.getTitle())).append('\n');
        sb.append("topic: ").append(note.getTopic() != null ? yamlScalar(note.getTopic().getName()) : "null").append('\n');
        sb.append("tags: [").append(tags).append("]\n");
        sb.append("status: ").append(note.getStatus()).append('\n');
        sb.append("pinned: ").append(note.isPinned()).append('\n');
        if (note.getCreatedAt() != null) {
            sb.append("created: ").append(note.getCreatedAt()).append('\n');
        }
        if (note.getUpdatedAt() != null) {
            sb.append("updated: ").append(note.getUpdatedAt()).append('\n');
        }
        sb.append("---\n\n");
        sb.append(note.getContentMd() == null ? "" : note.getContentMd());
        if (!sb.toString().endsWith("\n")) {
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Quote YAML scalars so titles/tags with special characters stay valid. */
    private static String yamlScalar(String v) {
        if (v == null) {
            return "\"\"";
        }
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
