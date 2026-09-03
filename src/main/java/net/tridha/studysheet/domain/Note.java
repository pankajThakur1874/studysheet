package net.tridha.studysheet.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    // Not @Lob: keeping it a plain String (TEXT column) lets us use lower()/LIKE
    // in the search query. TEXT is large enough for notes in both H2 and PostgreSQL.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentMd = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    // Tags are persisted separately (see TagService) before the note is saved, so
    // they arrive as existing rows — MERGE re-attaches them without trying to re-INSERT.
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinTable(
            name = "note_tags",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean pinned = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean bookmarked = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) DEFAULT 'TO_STUDY'")
    private StudyStatus status = StudyStatus.TO_STUDY;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentMd() {
        return contentMd;
    }

    public void setContentMd(String contentMd) {
        this.contentMd = contentMd;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public StudyStatus getStatus() {
        return status == null ? StudyStatus.TO_STUDY : status;
    }

    public void setStatus(StudyStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());

    @Transient
    public String getUpdatedAtDisplay() {
        return updatedAt == null ? "" : DISPLAY_FMT.format(updatedAt);
    }

    @Transient
    public String getCreatedAtDisplay() {
        return createdAt == null ? "" : DISPLAY_FMT.format(createdAt);
    }

    @Transient
    public int getWordCount() {
        if (contentMd == null || contentMd.isBlank()) {
            return 0;
        }
        String[] words = contentMd.trim().split("\\s+");
        return words.length;
    }

    @Transient
    public int getEstimatedReadingTimeMinutes() {
        int words = getWordCount();
        if (words == 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil((double) words / 200));
    }

    @Transient
    public String getEstimatedReadingTime() {
        int minutes = getEstimatedReadingTimeMinutes();
        return minutes <= 1 ? "1 min" : minutes + " min";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return id != null && id.equals(note.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
