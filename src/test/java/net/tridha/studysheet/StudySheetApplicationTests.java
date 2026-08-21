package net.tridha.studysheet;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.service.MarkdownService;
import net.tridha.studysheet.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StudySheetApplicationTests {

    @Autowired
    private NoteService noteService;

    @Autowired
    private MarkdownService markdownService;

    @Test
    void contextLoads() {
        assertThat(noteService).isNotNull();
        assertThat(markdownService).isNotNull();
    }

    @Test
    void testSaveNoteAndStatusUpdate() {
        Note note = new Note();
        note.setTitle("Spring Boot Architecture");
        note.setContentMd("# Spring Boot\n## Core Concepts\n- Dependency Injection\n- Auto-configuration");
        note.setStatus(StudyStatus.IN_PROGRESS);

        Note saved = noteService.save(note);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(StudyStatus.IN_PROGRESS);

        noteService.updateStatus(saved.getId(), StudyStatus.MASTERED);
        Note reloaded = noteService.get(saved.getId());
        assertThat(reloaded.getStatus()).isEqualTo(StudyStatus.MASTERED);

        assertThat(noteService.countByStatus(StudyStatus.MASTERED)).isGreaterThanOrEqualTo(1);

        // Cleanup
        noteService.delete(saved.getId());
    }

    @Autowired
    private net.tridha.studysheet.service.MarkdownImporterService markdownImporterService;

    @Test
    void testMarkdownImporterService() {
        int imported = markdownImporterService.importMarkdownFiles("./files");
        assertThat(imported).isGreaterThanOrEqualTo(1);
        assertThat(noteService.countAll()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testMarkdownRendering() {
        String markdown = "# Heading 1\n## Heading 2\n- List Item";
        String html = markdownService.toHtml(markdown);
        assertThat(html).contains("<h1>Heading 1</h1>");
        assertThat(html).contains("<h2>Heading 2</h2>");
        assertThat(html).contains("<li>List Item</li>");
    }
}
