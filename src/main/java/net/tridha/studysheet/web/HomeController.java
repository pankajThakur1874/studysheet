package net.tridha.studysheet.web;

import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.repo.TopicRepository;
import net.tridha.studysheet.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final NoteService noteService;
    private final TopicRepository topicRepository;

    public HomeController(NoteService noteService, TopicRepository topicRepository) {
        this.noteService = noteService;
        this.topicRepository = topicRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        long totalNotes = noteService.countAll();
        long masteredCount = noteService.countByStatus(StudyStatus.MASTERED);
        long needsReviewCount = noteService.countByStatus(StudyStatus.NEEDS_REVIEW);
        long inProgressCount = noteService.countByStatus(StudyStatus.IN_PROGRESS);
        long toStudyCount = noteService.countByStatus(StudyStatus.TO_STUDY);
        int masteryPercentage = totalNotes > 0 ? (int) Math.round(((double) masteredCount / totalNotes) * 100) : 0;

        model.addAttribute("pinnedNotes", noteService.pinned());
        model.addAttribute("recentNotes", noteService.recent());
        model.addAttribute("topics", topicRepository.findAllByOrderByNameAsc());

        model.addAttribute("totalNotes", totalNotes);
        model.addAttribute("masteredCount", masteredCount);
        model.addAttribute("needsReviewCount", needsReviewCount);
        model.addAttribute("inProgressCount", inProgressCount);
        model.addAttribute("toStudyCount", toStudyCount);
        model.addAttribute("masteryPercentage", masteryPercentage);

        return "dashboard";
    }
}
