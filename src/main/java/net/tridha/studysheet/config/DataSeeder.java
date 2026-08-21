package net.tridha.studysheet.config;

import net.tridha.studysheet.service.MarkdownImporterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = Logger.getLogger(DataSeeder.class.getName());

    private final MarkdownImporterService markdownImporterService;

    @Value("${studysheet.import.dir:./files}")
    private String importDir;

    public DataSeeder(MarkdownImporterService markdownImporterService) {
        this.markdownImporterService = markdownImporterService;
    }

    @Override
    public void run(String... args) {
        log.info("Checking for local Markdown study guides in: " + importDir);
        int importedCount = markdownImporterService.importMarkdownFiles(importDir);
        log.info("DataSeeder completed. Total study guides imported/synced: " + importedCount);
    }
}
