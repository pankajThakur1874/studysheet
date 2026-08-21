package net.tridha.studysheet.service;

import net.tridha.studysheet.domain.Tag;
import net.tridha.studysheet.repo.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Parses a comma-separated tag string ("java, spring, jpa") into a set of Tag
     * entities, reusing existing tags (case-insensitive) or creating new ones.
     */
    @Transactional
    public Set<Tag> resolveTags(String csv) {
        Set<Tag> result = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String raw : csv.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            Tag tag = tagRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> tagRepository.save(new Tag(name)));
            result.add(tag);
        }
        return result;
    }
}
