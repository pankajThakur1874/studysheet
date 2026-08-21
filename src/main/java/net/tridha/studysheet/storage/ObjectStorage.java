package net.tridha.studysheet.storage;

import java.util.Optional;

/**
 * Abstraction over an object store (local filesystem now, Cloudflare R2 / AWS S3 later).
 * <p>
 * The rest of the app talks only to this interface, so switching to R2 is a matter of
 * adding an {@code R2ObjectStorage implements ObjectStorage} bean and selecting it via
 * configuration — no changes to note/attachment logic required.
 * <p>
 * Keys are POSIX-style paths, e.g. {@code "notes/42.md"} or {@code "attachments/42/diagram.png"}.
 */
public interface ObjectStorage {

    /** Create or overwrite the object at {@code key} with UTF-8 text content. */
    void putText(String key, String content);

    /** Create or overwrite the object at {@code key} with raw bytes (attachments). */
    void putBytes(String key, byte[] content, String contentType);

    /** Read text content back, or empty if the key does not exist. */
    Optional<String> getText(String key);

    /** Delete the object if it exists (no error if it does not). */
    void delete(String key);

    /**
     * A stable, human-facing URL/location for the object. For local storage this is a
     * filesystem path; for R2 it will be the public (or signed) object URL.
     */
    String locationOf(String key);
}
