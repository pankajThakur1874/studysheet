package net.tridha.studysheet.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Default {@link ObjectStorage} backed by the local filesystem. Active unless a different
 * storage backend is selected via {@code studysheet.storage.backend} (e.g. "r2").
 * <p>
 * Files are written under {@code studysheet.storage.local.base-dir} (default {@code ./data/objectstore}).
 */
@Component
@ConditionalOnProperty(name = "studysheet.storage.backend", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalObjectStorage.class);

    private final Path baseDir;

    public LocalObjectStorage(
            @Value("${studysheet.storage.local.base-dir:./data/objectstore}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        log.info("LocalObjectStorage active — base dir: {}", this.baseDir);
    }

    private Path resolve(String key) {
        Path p = baseDir.resolve(key).normalize();
        if (!p.startsWith(baseDir)) {
            throw new IllegalArgumentException("Key escapes storage root: " + key);
        }
        return p;
    }

    @Override
    public void putText(String key, String content) {
        putBytes(key, content.getBytes(StandardCharsets.UTF_8), "text/markdown");
    }

    @Override
    public void putBytes(String key, byte[] content, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write object: " + key, e);
        }
    }

    @Override
    public Optional<String> getText(String key) {
        try {
            Path target = resolve(key);
            if (!Files.exists(target)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read object: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete object: " + key, e);
        }
    }

    @Override
    public String locationOf(String key) {
        return resolve(key).toString();
    }
}
