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
    public String index() {
        return "redirect:/notes";
    }
}
