package net.tridha.studysheet.web;

import jakarta.validation.Valid;
import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.domain.Topic;
import net.tridha.studysheet.repo.TopicRepository;
import net.tridha.studysheet.service.MarkdownService;
import net.tridha.studysheet.service.NoteService;
import net.tridha.studysheet.service.TagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notes")
public class NoteController {

    private final NoteService noteService;
    private final TopicRepository topicRepository;
    private final TagService tagService;
    private final MarkdownService markdownService;

    public NoteController(NoteService noteService, TopicRepository topicRepository,
                          TagService tagService, MarkdownService markdownService) {
        this.noteService = noteService;
        this.topicRepository = topicRepository;
        this.tagService = tagService;
        this.markdownService = markdownService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long topicId,
                       @RequestParam(required = false) Long tagId,
                       @RequestParam(required = false) StudyStatus status,
                       Model model) {
        if (q != null && !q.isBlank()) {
            model.addAttribute("notes", noteService.search(q));
            model.addAttribute("heading", "Search results for \"" + q + "\"");
        } else if (topicId != null) {
            model.addAttribute("notes", noteService.byTopic(topicId));
            Topic topic = topicRepository.findById(topicId).orElse(null);
            model.addAttribute("selectedTopic", topic);
            model.addAttribute("heading", topic != null ? "Topic: " + topic.getName() : "Notes");
        } else if (tagId != null) {
            model.addAttribute("notes", noteService.byTag(tagId));
            model.addAttribute("heading", "Tagged notes");
        } else if (status != null) {
            model.addAttribute("notes", noteService.byStatus(status));
            model.addAttribute("heading", "Status: " + status.getDisplayName());
        } else {
            model.addAttribute("notes", noteService.all());
            model.addAttribute("heading", "All notes");
        }
        java.util.List<Note> activeNotes = (java.util.List<Note>) model.getAttribute("notes");
        if (activeNotes != null) {
            java.util.List<Note> sortedNotes = activeNotes.stream()
                    .sorted(NATURAL_NOTE_COMPARATOR)
                    .toList();
            model.addAttribute("notes", sortedNotes);

            java.util.Map<Topic, java.util.List<Note>> groupedNotes = sortedNotes.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            n -> n.getTopic() != null ? n.getTopic() : new Topic("General Notes", "Uncategorized study guides"),
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    ));
            model.addAttribute("groupedNotes", groupedNotes);
        }

        model.addAttribute("q", q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", StudyStatus.values());
        model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());
        return "notes/list";
    }

    public static final java.util.Comparator<Note> NATURAL_NOTE_COMPARATOR = (n1, n2) -> {
        String t1 = n1.getTitle() != null ? n1.getTitle() : "";
        String t2 = n2.getTitle() != null ? n2.getTitle() : "";

        Double num1 = extractLeadingNumber(t1);
        Double num2 = extractLeadingNumber(t2);

        if (num1 != null && num2 != null) {
            int cmp = Double.compare(num1, num2);
            if (cmp != 0) return cmp;
        } else if (num1 != null) {
            return -1;
        } else if (num2 != null) {
            return 1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(t1, t2);
    };

    private static Double extractLeadingNumber(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)").matcher(text.trim());
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Note note = noteService.get(id);
        model.addAttribute("note", note);
        model.addAttribute("contentHtml", markdownService.toHtml(note.getContentMd()));
        model.addAttribute("statuses", StudyStatus.values());
        return "notes/view";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("note", new Note());
        model.addAttribute("tagsCsv", "");
        model.addAttribute("statuses", StudyStatus.values());
        model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());
        return "notes/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Note note = noteService.get(id);
        model.addAttribute("note", note);
        model.addAttribute("tagsCsv", noteService.tagsAsCsv(note));
        model.addAttribute("statuses", StudyStatus.values());
        model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());
        return "notes/form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("note") Note note,
                       BindingResult binding,
                       @RequestParam(required = false) Long topicId,
                       @RequestParam(required = false) String tagsCsv,
                       Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("tagsCsv", tagsCsv);
            model.addAttribute("statuses", StudyStatus.values());
            model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());
            return "notes/form";
        }

        // When editing, load the managed entity so createdAt/pinned are preserved.
        Note target = (note.getId() != null) ? noteService.get(note.getId()) : new Note();
        target.setTitle(note.getTitle());
        target.setContentMd(note.getContentMd());
        target.setTopic(topicId != null ? topicRepository.findById(topicId).orElse(null) : null);
        target.setTags(tagService.resolveTags(tagsCsv));
        if (note.getStatus() != null) {
            target.setStatus(note.getStatus());
        }

        Note saved = noteService.save(target);
        return "redirect:/notes/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        noteService.delete(id);
        return "redirect:/notes";
    }

    @PostMapping("/{id}/pin")
    public String togglePin(@PathVariable Long id) {
        noteService.togglePin(id);
        return "redirect:/notes/" + id;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam StudyStatus status) {
        noteService.updateStatus(id, status);
        return "redirect:/notes/" + id;
    }
}
