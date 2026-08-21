package net.tridha.studysheet.web;

import jakarta.validation.Valid;
import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.Topic;
import net.tridha.studysheet.repo.NoteRepository;
import net.tridha.studysheet.repo.TopicRepository;
import net.tridha.studysheet.service.MarkdownImporterService;
import net.tridha.studysheet.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/topics")
public class TopicController {

    private final TopicRepository topicRepository;
    private final NoteRepository noteRepository;
    private final NoteService noteService;
    private final MarkdownImporterService markdownImporterService;

    public TopicController(TopicRepository topicRepository,
                           NoteRepository noteRepository,
                           NoteService noteService,
                           MarkdownImporterService markdownImporterService) {
        this.topicRepository = topicRepository;
        this.noteRepository = noteRepository;
        this.noteService = noteService;
        this.markdownImporterService = markdownImporterService;
    }

    @GetMapping
    public String list(Model model) {
        List<Topic> topics = topicRepository.findAllByOrderByNameAsc();
        Map<Long, Long> topicNoteCounts = topics.stream()
                .collect(Collectors.toMap(Topic::getId, t -> noteService.countByTopic(t.getId())));

        model.addAttribute("topics", topics);
        model.addAttribute("topicNoteCounts", topicNoteCounts);
        model.addAttribute("newTopic", new Topic());
        return "topics/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newTopic") Topic topic,
                         BindingResult binding, Model model) {
        if (!binding.hasErrors()
                && topicRepository.findAllByOrderByNameAsc().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(topic.getName().trim()))) {
            binding.rejectValue("name", "duplicate", "A topic with this name already exists");
        }
        if (binding.hasErrors()) {
            model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());
            return "topics/list";
        }
        topicRepository.save(topic);
        return "redirect:/topics";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id) {
        // Detach this topic from any notes so their content survives the delete.
        List<Note> notes = noteRepository.findByTopicIdOrderByUpdatedAtDesc(id);
        for (Note n : notes) {
            n.setTopic(null);
        }
        noteRepository.saveAll(notes);
        topicRepository.deleteById(id);
        return "redirect:/topics";
    }

    @PostMapping("/sync-files")
    public String syncFiles(RedirectAttributes redirectAttributes) {
        int count = markdownImporterService.cleanAndImportMarkdownFiles("./files");
        redirectAttributes.addFlashAttribute("message", "Clean sync complete! Database reset and " + count + " markdown study guides imported.");
        return "redirect:/notes";
    }
}
