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
    private final MarkdownService markdownService;

    public MarkdownImporterService(NoteRepository noteRepository,
                                   TopicRepository topicRepository,
                                   NoteService noteService,
                                   TagService tagService,
                                   MarkdownService markdownService) {
        this.noteRepository = noteRepository;
        this.topicRepository = topicRepository;
        this.noteService = noteService;
        this.tagService = tagService;
        this.markdownService = markdownService;
    }

    @Transactional
    public int cleanAndImportMarkdownFiles(String baseDirPath) {
        log.info("Performing clean sync — purging existing notes before re-importing from " + baseDirPath);
        noteRepository.deleteAll();
        return importMarkdownFiles(baseDirPath);
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
                if (importSingleFile(path, baseDirPath)) {
                    count++;
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error reading markdown files from " + baseDirPath, e);
        }
        log.info("Successfully imported/updated " + count + " markdown study guides from " + baseDirPath);
        markdownService.clearCaches(); // note IDs/titles may have changed — drop stale rendered HTML & link map
        return count;
    }

    private boolean importSingleFile(Path path, String baseDirPath) {
        try {
            String content = Files.readString(path);
            if (content.isBlank()) return false;

            String fileName = path.getFileName().toString();
            String pathStr = path.toString();

            // Extract Title
            String title = extractTitle(content, fileName, pathStr);

            // Determine Topic dynamically from parent folder
            Topic topic = resolveTopic(path, baseDirPath, fileName);

            // Determine Tags
            String tagsCsv = resolveTagsCsv(fileName, pathStr);

            // Check existing note by title and topic to prevent cross-folder note overwrites
            Optional<Note> existingOpt = noteRepository.findByTitleIgnoreCaseAndTopicId(title.trim(), topic.getId());
            if (existingOpt.isEmpty()) {
                existingOpt = noteRepository.findByTitleIgnoreCase(title.trim())
                        .filter(n -> n.getTopic() != null && n.getTopic().getId().equals(topic.getId()));
            }

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
        String h1Title = null;
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                h1Title = trimmed.substring(2).trim();
                break;
            }
        }

        if (h1Title == null) {
            h1Title = fileName.replace(".md", "").replace("-", " ").replace("_", " ");
        }

        // Extract numeric prefix from fileName if present (e.g., "00-", "01-", "05_", "0.0_")
        String numPrefix = extractNumericPrefixFromFileName(fileName);
        if (numPrefix != null && !hasNumericPrefix(h1Title)) {
            return numPrefix + " - " + h1Title;
        }

        return h1Title;
    }

    private String extractNumericPrefixFromFileName(String fileName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)[\\-_]").matcher(fileName);
        if (m.find()) {
            String prefix = m.group(1);
            if (prefix.length() == 1) {
                return "0" + prefix;
            }
            return prefix;
        }
        return null;
    }

    private boolean hasNumericPrefix(String title) {
        return title.matches("^\\d+(?:\\.\\d+)?\\s*[-–—:].*") || title.matches("^\\d+\\s+.*");
    }

    private Topic resolveTopic(Path path, String baseDirPath, String fileName) {
        Path baseDir = Paths.get(baseDirPath).toAbsolutePath().normalize();
        Path absoluteFilePath = path.toAbsolutePath().normalize();
        Path relativePath = baseDir.relativize(absoluteFilePath);

        String topicName;
        String description;

        if (relativePath.getNameCount() > 1) {
            String folderName = relativePath.getName(0).toString();
            if ("00-start-here".equalsIgnoreCase(folderName)) {
                topicName = "00 Start Here";
                description = "Reading order, how the parts relate, and what to skip on a second pass";
            } else if ("01-foundations".equalsIgnoreCase(folderName)) {
                topicName = "01 Foundations";
                description = "DDIA Part I: Reliability, scalability, relational vs document, storage engines";
            } else if ("02-distributed-data".equalsIgnoreCase(folderName)) {
                topicName = "02 Distributed Data";
                description = "DDIA Part II: Replication, partitioning, transactions, consistency and consensus";
            } else if ("03-derived-data".equalsIgnoreCase(folderName)) {
                topicName = "03 Derived Data";
                description = "DDIA Part III: Batch & stream processing, CDC, event sourcing, data integration";
            } else if ("04-book-vol-1".equalsIgnoreCase(folderName)) {
                topicName = "04 Book Vol. 1";
                description = "Alex Xu Vol. 1: Worked system design guides (Rate limiters, Key-Value stores)";
            } else if ("05-book-vol-2".equalsIgnoreCase(folderName)) {
                topicName = "05 Book Vol. 2";
                description = "Alex Xu Vol. 2: Enterprise worked systems (Payment systems, Stock Exchange)";
            } else if ("06-concepts-bytebytego".equalsIgnoreCase(folderName)) {
                topicName = "06 Core Concepts";
                description = "Networking, caching, security, and cloud primitives reference";
            } else if ("07-design-questions".equalsIgnoreCase(folderName)) {
                topicName = "07 Design Questions";
                description = "Interview drills: Worked end-to-end system design questions";
            } else if ("08-edge-cases".equalsIgnoreCase(folderName)) {
                topicName = "08 Edge Cases";
                description = "Production failure modes, Kafka internals, distributed transaction corners";
            } else if ("09-agentic-ai".equalsIgnoreCase(folderName)) {
                topicName = "09 Agentic AI";
                description = "LLM agent loops, tool use, RAG, evals, guardrails, and deployment";
            } else {
                topicName = formatTopicTitle(folderName);
                description = "Study guides and system design documentation in " + topicName;
            }
        } else {
            topicName = "00 Start Here";
            description = "Designing Data-Intensive Applications Learning Roadmap & Course Outline";
        }

        String finalTopicName = topicName;
        String finalDescription = description;
        return topicRepository.findAllByOrderByNameAsc().stream()
                .filter(t -> t.getName().equalsIgnoreCase(finalTopicName))
                .findFirst()
                .orElseGet(() -> topicRepository.save(new Topic(finalTopicName, finalDescription)));
    }

    private String formatTopicTitle(String folderName) {
        String[] words = folderName.split("[-_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            if (w.matches("^\\d+$")) {
                sb.append(w.length() == 1 ? "0" + w : w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
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
