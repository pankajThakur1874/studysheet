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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private net.tridha.studysheet.repo.TopicRepository topicRepository;

    @Test
    void testMarkMasteredAndNextController() throws Exception {
        net.tridha.studysheet.domain.Topic topic = topicRepository.save(new net.tridha.studysheet.domain.Topic("Test Agentic AI Topic", "Test Topic Description"));

        Note n1 = new Note();
        n1.setTitle("00 — Overview");
        n1.setContentMd("Test content");
        n1.setTopic(topic);
        n1 = noteService.save(n1);

        Note n2 = new Note();
        n2.setTitle("01 — What Is Agentic AI");
        n2.setContentMd("Test content 2");
        n2.setTopic(topic);
        n2 = noteService.save(n2);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/notes/" + n1.getId() + "/master-and-next"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/notes/" + n2.getId()));

        Note reloadedN1 = noteService.get(n1.getId());
        assertThat(reloadedN1.getStatus()).isEqualTo(StudyStatus.MASTERED);

        noteService.delete(n1.getId());
        noteService.delete(n2.getId());
        topicRepository.delete(topic);
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
