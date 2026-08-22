package net.tridha.studysheet.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseMigrationConfig implements CommandLineRunner {

    private static final Logger log = Logger.getLogger(DatabaseMigrationConfig.class.getName());

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Executing database migration check for 'bookmarked' column...");
            jdbcTemplate.execute("ALTER TABLE notes ADD COLUMN IF NOT EXISTS bookmarked BOOLEAN DEFAULT FALSE;");
            log.info("Database migration check completed successfully.");
        } catch (Exception e) {
            log.warning("Database migration note: " + e.getMessage());
        }
    }
}
