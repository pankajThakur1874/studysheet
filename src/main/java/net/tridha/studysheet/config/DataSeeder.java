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

    @Value("${studysheet.clean-on-startup:true}")
    private boolean cleanOnStartup;

    public DataSeeder(MarkdownImporterService markdownImporterService) {
        this.markdownImporterService = markdownImporterService;
    }

    @Override
    public void run(String... args) {
        log.info("Checking for local Markdown study guides in: " + importDir);
        if (cleanOnStartup) {
            log.info("Clean-on-startup enabled for deployment. Purging current database data and reloading fresh markdown files from: " + importDir);
            int count = markdownImporterService.cleanAndImportMarkdownFiles(importDir);
            log.info("DataSeeder deployment reload completed. Total study guides reloaded: " + count);
        } else {
            int importedCount = markdownImporterService.importMarkdownFiles(importDir);
            log.info("DataSeeder incremental sync completed. Total study guides imported/synced: " + importedCount);
        }
    }
}
