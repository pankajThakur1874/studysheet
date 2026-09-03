package net.tridha.studysheet.web;

import jakarta.validation.Valid;
import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.domain.Topic;
import net.tridha.studysheet.repo.NoteRepository;
import net.tridha.studysheet.repo.TopicRepository;
import net.tridha.studysheet.service.MarkdownService;
import net.tridha.studysheet.service.NoteService;
import net.tridha.studysheet.service.TagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notes")
public class NoteController {

    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final TopicRepository topicRepository;
    private final TagService tagService;
    private final MarkdownService markdownService;

    public NoteController(NoteService noteService, NoteRepository noteRepository,
                          TopicRepository topicRepository, TagService tagService,
                          MarkdownService markdownService) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.topicRepository = topicRepository;
        this.tagService = tagService;
        this.markdownService = markdownService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long topicId,
                       @RequestParam(required = false) Long tagId,
                       @RequestParam(required = false) StudyStatus status,
                       @RequestParam(required = false) Boolean bookmarked,
                       Model model) {
        java.util.List<Note> activeNotes;
        if (q != null && !q.isBlank()) {
            activeNotes = noteService.search(q);
            model.addAttribute("heading", "Search results for \"" + q + "\"");
        } else if (topicId != null) {
            activeNotes = noteService.byTopic(topicId);
            Topic topic = topicRepository.findById(topicId).orElse(null);
            model.addAttribute("selectedTopic", topic);
            model.addAttribute("heading", topic != null ? "Topic: " + topic.getName() : "Notes");
        } else if (tagId != null) {
            activeNotes = noteService.byTag(tagId);
            model.addAttribute("heading", "Tagged notes");
        } else if (status != null) {
            activeNotes = noteService.byStatus(status);
            model.addAttribute("heading", "Status: " + status.getDisplayName());
        } else if (Boolean.TRUE.equals(bookmarked)) {
            activeNotes = noteService.bookmarked();
            model.addAttribute("heading", "Bookmarked Notes 🔖");
            model.addAttribute("selectedBookmarked", true);
        } else {
            activeNotes = noteService.all();
            model.addAttribute("heading", "All notes");
        }

        if (activeNotes != null) {
            java.util.List<Note> sortedNotes = activeNotes.stream()
                    .sorted(NATURAL_NOTE_COMPARATOR)
                    .toList();
            model.addAttribute("notes", sortedNotes);

            // Group notes by Topic, sorted strictly by NATURAL_TOPIC_COMPARATOR (folder prefix order)
            java.util.List<Topic> sortedTopics = topicRepository.findAllByOrderByNameAsc().stream()
                    .sorted(NATURAL_TOPIC_COMPARATOR)
                    .toList();

            java.util.Map<Topic, java.util.List<Note>> groupedNotes = new java.util.LinkedHashMap<>();
            for (Topic topic : sortedTopics) {
                java.util.List<Note> notesInTopic = activeNotes.stream()
                        .filter(n -> n.getTopic() != null && n.getTopic().getId().equals(topic.getId()))
                        .sorted(NATURAL_NOTE_COMPARATOR)
                        .toList();
                if (!notesInTopic.isEmpty()) {
                    groupedNotes.put(topic, notesInTopic);
                }
            }

            java.util.List<Note> uncategorized = activeNotes.stream()
                    .filter(n -> n.getTopic() == null)
                    .sorted(NATURAL_NOTE_COMPARATOR)
                    .toList();
            if (!uncategorized.isEmpty()) {
                groupedNotes.put(new Topic("General Notes", "Uncategorized study guides"), uncategorized);
            }

            model.addAttribute("groupedNotes", groupedNotes);

            java.util.Map<Topic, Long> folderMasteredCounts = new java.util.LinkedHashMap<>();
            java.util.Map<Topic, Integer> folderPercentages = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<Topic, java.util.List<Note>> entry : groupedNotes.entrySet()) {
                long mCount = entry.getValue().stream().filter(n -> n.getStatus() == StudyStatus.MASTERED).count();
                int pct = entry.getValue().size() > 0 ? (int) Math.round(((double) mCount / entry.getValue().size()) * 100) : 0;
                folderMasteredCounts.put(entry.getKey(), mCount);
                folderPercentages.put(entry.getKey(), pct);
            }
            model.addAttribute("folderMasteredCounts", folderMasteredCounts);
            model.addAttribute("folderPercentages", folderPercentages);
        }

        long totalCount = noteService.countAll();
        long masteredCount = noteService.countByStatus(StudyStatus.MASTERED);
        long inProgressCount = noteService.countByStatus(StudyStatus.IN_PROGRESS);
        long needsReviewCount = noteService.countByStatus(StudyStatus.NEEDS_REVIEW);
        long toStudyCount = noteService.countByStatus(StudyStatus.TO_STUDY);
        long bookmarkedCount = noteService.bookmarked().size();
        int percentage = totalCount > 0 ? (int) Math.round(((double) masteredCount / totalCount) * 100) : 0;

        // Resume reading note: pick first IN_PROGRESS, or NEEDS_REVIEW, or TO_STUDY
        List<Note> allAll = noteService.all();
        Note resumeNote = allAll.stream()
                .filter(n -> n.getStatus() == StudyStatus.IN_PROGRESS)
                .findFirst()
                .orElseGet(() -> allAll.stream()
                        .filter(n -> n.getStatus() == StudyStatus.NEEDS_REVIEW || n.getStatus() == StudyStatus.TO_STUDY)
                        .findFirst()
                        .orElse(!allAll.isEmpty() ? allAll.get(0) : null));

        // 30 Ticks Mastery Distribution Bar
        java.util.List<String> masteryTicksColors = new java.util.ArrayList<>();
        int masteredTicks = totalCount > 0 ? (int) Math.round(((double) masteredCount / totalCount) * 30) : 0;
        int inProgressTicks = totalCount > 0 ? (int) Math.round(((double) (inProgressCount + needsReviewCount) / totalCount) * 30) : 0;
        for (int i = 0; i < 30; i++) {
            if (i < masteredTicks) {
                masteryTicksColors.add("#101828");
            } else if (i < masteredTicks + inProgressTicks) {
                masteryTicksColors.add("#B45309");
            } else {
                masteryTicksColors.add("#DDD8CE");
            }
        }

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("masteredCount", masteredCount);
        model.addAttribute("inProgressCount", inProgressCount);
        model.addAttribute("needsReviewCount", needsReviewCount);
        model.addAttribute("toStudyCount", toStudyCount);
        model.addAttribute("bookmarkedCount", bookmarkedCount);
        model.addAttribute("masteryPercentage", percentage);
        model.addAttribute("resumeNote", resumeNote);
        model.addAttribute("masteryTicksColors", masteryTicksColors);

        model.addAttribute("q", q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", StudyStatus.values());
        return "notes/list";
    }

    public static final java.util.Comparator<Topic> NATURAL_TOPIC_COMPARATOR = (t1, t2) -> {
        String name1 = t1 != null && t1.getName() != null ? t1.getName() : "";
        String name2 = t2 != null && t2.getName() != null ? t2.getName() : "";

        Double num1 = extractLeadingNumber(name1);
        Double num2 = extractLeadingNumber(name2);

        if (num1 != null && num2 != null) {
            int cmp = Double.compare(num1, num2);
            if (cmp != 0) return cmp;
        } else if (num1 != null) {
            return -1;
        } else if (num2 != null) {
            return 1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
    };

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

    public static final java.util.Comparator<net.tridha.studysheet.repo.NoteRepository.NoteSummary> NATURAL_SUMMARY_COMPARATOR = (n1, n2) -> {
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

    private int findNoteIndex(List<Note> notes, Long targetId) {
        if (notes == null || targetId == null) return -1;
        for (int i = 0; i < notes.size(); i++) {
            if (targetId.equals(notes.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private List<Note> getOrderedCourseNotes(Note currentNote) {
        List<Note> notes;
        if (currentNote != null && currentNote.getTopic() != null) {
            notes = new java.util.ArrayList<>(noteRepository.findByTopicIdOrderByTitleAsc(currentNote.getTopic().getId()));
        } else {
            notes = new java.util.ArrayList<>(noteRepository.findAll());
        }
        notes.sort(NATURAL_NOTE_COMPARATOR);
        return notes;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Note note = noteService.get(id);

        List<Note> peerNotes = getOrderedCourseNotes(note);
        int index = findNoteIndex(peerNotes, id);
        Note previousNote = (index > 0) ? peerNotes.get(index - 1) : null;
        Note nextNote = (index >= 0 && index < peerNotes.size() - 1) ? peerNotes.get(index + 1) : null;

        String notePositionFormatted = String.format("%02d of %02d", index >= 0 ? index + 1 : 1, Math.max(peerNotes.size(), 1));

        // Group ALL notes for the Left Course Navigation Sidebar, ordered by NATURAL_TOPIC_COMPARATOR
        List<Topic> sortedTopics = topicRepository.findAllByOrderByNameAsc().stream()
                .sorted(NATURAL_TOPIC_COMPARATOR)
                .toList();

        // Lightweight summaries (id/title/status/topicId only) — the sidebar never needs note
        // content, so this avoids loading every guide's full markdown on each page view.
        List<net.tridha.studysheet.repo.NoteRepository.NoteSummary> allSummaries = noteRepository.findAllSummaries();
        java.util.Map<Topic, List<net.tridha.studysheet.repo.NoteRepository.NoteSummary>> groupedNotes = new java.util.LinkedHashMap<>();
        for (Topic t : sortedTopics) {
            List<net.tridha.studysheet.repo.NoteRepository.NoteSummary> notesInTopic = allSummaries.stream()
                    .filter(s -> t.getId().equals(s.getTopicId()))
                    .sorted(NATURAL_SUMMARY_COMPARATOR)
                    .toList();
            if (!notesInTopic.isEmpty()) {
                groupedNotes.put(t, notesInTopic);
            }
        }

        long totalCount = noteService.countAll();
        long masteredCount = noteService.countByStatus(StudyStatus.MASTERED);
        int percentage = totalCount > 0 ? (int) Math.round(((double) masteredCount / totalCount) * 100) : 0;

        model.addAttribute("note", note);
        model.addAttribute("previousNote", previousNote);
        model.addAttribute("nextNote", nextNote);
        model.addAttribute("notePositionFormatted", notePositionFormatted);
        model.addAttribute("groupedNotes", groupedNotes);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("masteredCount", masteredCount);
        model.addAttribute("masteryPercentage", percentage);
        model.addAttribute("contentHtml", markdownService.toHtml(note.getContentMd()));
        model.addAttribute("statuses", StudyStatus.values());
        return "notes/view";
    }

    @PostMapping("/{id}/master-and-next")
    public String markMasteredAndNext(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Note currentNote = noteService.get(id);
        noteService.updateStatus(id, StudyStatus.MASTERED);

        List<Note> peerNotes = getOrderedCourseNotes(currentNote);
        int index = findNoteIndex(peerNotes, id);
        if (index >= 0 && index < peerNotes.size() - 1) {
            Note nextNote = peerNotes.get(index + 1);
            redirectAttributes.addFlashAttribute("message", "Marked '" + currentNote.getTitle() + "' as Mastered! 🎉");
            return "redirect:/notes/" + nextNote.getId();
        }

        redirectAttributes.addFlashAttribute("message", "🎉 Topic Completed! All guides in '" + (currentNote.getTopic() != null ? currentNote.getTopic().getName() : "Chapter") + "' are now mastered.");
        return "redirect:/notes";
    }

    @PostMapping("/{id}/bookmark")
    public String toggleBookmark(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Note note = noteService.get(id);
        noteService.toggleBookmark(id);
        redirectAttributes.addFlashAttribute("message", note.isBookmarked() ? "Removed bookmark." : "Saved note to Bookmarks! 🔖");
        return "redirect:/notes/" + id;
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
