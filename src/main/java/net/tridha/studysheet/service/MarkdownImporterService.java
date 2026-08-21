package net.tridha.studysheet.service;

import net.tridha.studysheet.domain.Note;
import net.tridha.studysheet.domain.StudyStatus;
import net.tridha.studysheet.domain.Topic;
import net.tridha.studysheet.repo.NoteRepository;
import net.tridha.studysheet.repo.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class MarkdownImporterService {

    private static final Logger log = Logger.getLogger(MarkdownImporterService.class.getName());

    private final NoteRepository noteRepository;
    private final TopicRepository topicRepository;
    private final NoteService noteService;
    private final TagService tagService;

    public MarkdownImporterService(NoteRepository noteRepository,
                                   TopicRepository topicRepository,
                                   NoteService noteService,
                                   TagService tagService) {
        this.noteRepository = noteRepository;
        this.topicRepository = topicRepository;
        this.noteService = noteService;
        this.tagService = tagService;
    }

    @Transactional
    public int importMarkdownFiles(String baseDirPath) {
        Path baseDir = Paths.get(baseDirPath);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            log.warning("Markdown import directory does not exist: " + baseDirPath);
            return 0;
        }

        int count = 0;
        try (Stream<Path> stream = Files.walk(baseDir)) {
            List<Path> mdFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();

            for (Path path : mdFiles) {
                if (importSingleFile(path)) {
                    count++;
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error reading markdown files from " + baseDirPath, e);
        }
        log.info("Successfully imported/updated " + count + " markdown study guides from " + baseDirPath);
        return count;
    }

    private boolean importSingleFile(Path path) {
        try {
            String content = Files.readString(path);
            if (content.isBlank()) return false;

            String fileName = path.getFileName().toString();
            String pathStr = path.toString();

            // Extract Title
            String title = extractTitle(content, fileName, pathStr);

            // Determine Topic
            Topic topic = resolveTopic(pathStr, fileName);

            // Determine Tags
            String tagsCsv = resolveTagsCsv(fileName, pathStr);

            // Check existing by title
            Optional<Note> existingOpt = noteRepository.findAll().stream()
                    .filter(n -> n.getTitle().equalsIgnoreCase(title.trim()))
                    .findFirst();

            Note note;
            if (existingOpt.isPresent()) {
                note = existingOpt.get();
            } else {
                note = new Note();
                note.setStatus(StudyStatus.TO_STUDY);
            }

            note.setTitle(title);
            note.setContentMd(content);
            note.setTopic(topic);
            note.setTags(tagService.resolveTags(tagsCsv));

            noteService.save(note);
            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to import file: " + path, e);
            return false;
        }
    }

    private String extractTitle(String content, String fileName, String pathStr) {
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }

        // Fallback to formatted filename
        return fileName.replace(".md", "").replace("-", " ");
    }

    private Topic resolveTopic(String pathStr, String fileName) {
        String topicName;
        String description;

        if (pathStr.contains("02-distributed-data") || fileName.contains("replication")) {
            topicName = "DDIA - Distributed Data";
            description = "Part II: Distributed Data Systems (Replication, Partitioning, Consistency & Consensus)";
        } else if (fileName.equalsIgnoreCase("README.md") && !pathStr.contains("foundations") && !pathStr.contains("distributed")) {
            topicName = "DDIA - Master Guide";
            description = "Designing Data-Intensive Applications Learning Roadmap & Course Outline";
        } else {
            topicName = "DDIA - Foundations";
            description = "Part I: Foundations of Data Systems (Reliability, Scalability, Storage Engines & Data Models)";
        }

        String finalTopicName = topicName;
        String finalDescription = description;
        return topicRepository.findAllByOrderByNameAsc().stream()
                .filter(t -> t.getName().equalsIgnoreCase(finalTopicName))
                .findFirst()
                .orElseGet(() -> topicRepository.save(new Topic(finalTopicName, finalDescription)));
    }

    private String resolveTagsCsv(String fileName, String pathStr) {
        StringBuilder tags = new StringBuilder("ddia, system-design, databases");

        if (fileName.contains("replication")) {
            tags.append(", replication, distributed-systems");
        }
        if (fileName.contains("storage") || fileName.contains("b-trees") || fileName.contains("oltp")) {
            tags.append(", storage-engines, indexing");
        }
        if (fileName.contains("reliability") || fileName.contains("scalability") || fileName.contains("maintainability")) {
            tags.append(", fault-tolerance, architecture");
        }
        if (fileName.contains("relational") || fileName.contains("graph")) {
            tags.append(", data-modeling");
        }
        if (fileName.contains("encoding")) {
            tags.append(", serialization, schemas");
        }

        return tags.toString();
    }
}
