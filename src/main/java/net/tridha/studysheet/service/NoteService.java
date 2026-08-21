package net.tridha.studysheet.service;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.repo.NoteRepository;
import net.tridha.studysheet.storage.NoteMirrorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteMirrorService noteMirrorService;

    public NoteService(NoteRepository noteRepository, NoteMirrorService noteMirrorService) {
        this.noteRepository = noteRepository;
        this.noteMirrorService = noteMirrorService;
    }

    public List<Note> all() {
        return noteRepository.findAllByOrderByTitleAsc();
    }

    public List<Note> recent() {
        return noteRepository.findTop6ByOrderByUpdatedAtDesc();
    }

    public List<Note> pinned() {
        return noteRepository.findByPinnedTrueOrderByUpdatedAtDesc();
    }

    public List<Note> byTopic(Long topicId) {
        return noteRepository.findByTopicIdOrderByTitleAsc(topicId);
    }

    public List<Note> byTag(Long tagId) {
        return noteRepository.findByTagId(tagId);
    }

    public List<Note> byStatus(StudyStatus status) {
        return noteRepository.findByStatusOrderByUpdatedAtDesc(status);
    }

    public long countByStatus(StudyStatus status) {
        return noteRepository.countByStatus(status);
    }

    public long countByTopic(Long topicId) {
        return noteRepository.countByTopicId(topicId);
    }

    public long countAll() {
        return noteRepository.count();
    }

    public List<Note> search(String q) {
        if (q == null || q.isBlank()) {
            return all();
        }
        return noteRepository.search(q.trim());
    }

    public Note get(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + id));
    }

    @Transactional
    public Note save(Note note) {
        Note saved = noteRepository.save(note);
        noteMirrorService.mirror(saved);   // Option C: portable .md mirror (best-effort)
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        noteRepository.deleteById(id);
        noteMirrorService.remove(id);
    }

    @Transactional
    public void togglePin(Long id) {
        Note note = get(id);
        note.setPinned(!note.isPinned());
        noteMirrorService.mirror(noteRepository.save(note));
    }

    @Transactional
    public void updateStatus(Long id, StudyStatus status) {
        Note note = get(id);
        note.setStatus(status);
        noteMirrorService.mirror(noteRepository.save(note));
    }

    /** Turns a note's tag set into a comma-separated string for editing forms. */
    public String tagsAsCsv(Note note) {
        return note.getTags().stream()
                .map(t -> t.getName())
                .collect(Collectors.joining(", "));
    }
}
