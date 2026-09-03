package net.tridha.studysheet.repo;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    Optional<Note> findByTitleIgnoreCase(String title);

    Optional<Note> findByTitleIgnoreCaseAndTopicId(String title, Long topicId);

    List<Note> findAllByOrderByTitleAsc();

    List<Note> findAllByOrderByPinnedDescUpdatedAtDesc();

    List<Note> findTop6ByOrderByUpdatedAtDesc();

    List<Note> findByPinnedTrueOrderByUpdatedAtDesc();

    List<Note> findByBookmarkedTrueOrderByUpdatedAtDesc();

    List<Note> findByTopicIdOrderByTitleAsc(Long topicId);

    List<Note> findByTopicIdOrderByUpdatedAtDesc(Long topicId);

    List<Note> findByStatusOrderByUpdatedAtDesc(StudyStatus status);

    long countByStatus(StudyStatus status);

    long countByTopicId(Long topicId);

    @Query("""
            select distinct n from Note n
            left join n.tags t
            where lower(n.title) like lower(concat('%', :q, '%'))
               or lower(n.contentMd) like lower(concat('%', :q, '%'))
               or lower(t.name) like lower(concat('%', :q, '%'))
            order by n.updatedAt desc
            """)
    List<Note> search(@Param("q") String q);

    @Query("select distinct n from Note n join n.tags t where t.id = :tagId order by n.updatedAt desc")
    List<Note> findByTagId(@Param("tagId") Long tagId);

    /** id + title only — used to build the markdown cross-link map without loading note content. */
    @Query("select n.id as id, n.title as title from Note n")
    List<TitleView> findAllTitles();

    /** Lightweight rows for the navigation sidebar — avoids loading contentMd for all notes per page view. */
    @Query("select n.id as id, n.title as title, n.status as status, n.topic.id as topicId from Note n")
    List<NoteSummary> findAllSummaries();

    interface TitleView {
        Long getId();
        String getTitle();
    }

    interface NoteSummary {
        Long getId();
        String getTitle();
        StudyStatus getStatus();
        Long getTopicId();
    }
}
