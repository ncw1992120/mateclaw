package vip.mate.dataagent.objectref;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Controlled filesystem storage for opaque, root-relative object keys. */
public final class LocalObjectFileStore {
    private final Path root;

    public LocalObjectFileStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public void write(String key, InputStream content) throws IOException {
        Objects.requireNonNull(content, "content");
        Path target = resolve(key);
        Path parent = target.getParent();
        createDirectoriesSafely(parent);
        rejectSymbolicLinks(target);
        Path temporary = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                content.transferTo(output);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic object replacement is not supported", e);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public InputStream open(String key) throws IOException {
        Path path = resolve(key);
        rejectSymbolicLinks(path);
        return Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    public void delete(String key) throws IOException {
        Path path = resolve(key);
        rejectSymbolicLinks(path);
        try {
            Files.deleteIfExists(path);
        } catch (NoSuchFileException ignored) {
            // Idempotent even when an intermediate key directory has already been removed.
        }
    }

    /** Deletes expired task-scoped objects stored as {taskId}/{objectId}; managed datasets use a deeper key. */
    public int deleteTaskObjectsOlderThan(Instant cutoff) throws IOException {
        Objects.requireNonNull(cutoff, "cutoff");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) return 0;

        List<Path> expired;
        try (Stream<Path> paths = Files.walk(root, 2)) {
            expired = paths
                    .filter(path -> !path.equals(root))
                    .filter(path -> root.relativize(path).getNameCount() == 2)
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> {
                        try {
                            return !Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
                                    .toInstant().isAfter(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
        }

        int deleted = 0;
        for (Path path : expired) {
            rejectSymbolicLinks(path);
            if (Files.deleteIfExists(path)) deleted++;
            try {
                Files.deleteIfExists(path.getParent());
            } catch (DirectoryNotEmptyException ignored) {
                // Other unexpired task objects may still use this directory.
            }
        }
        return deleted;
    }

    public Path resolve(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("object key is required");
        Path relative = Path.of(key);
        if (relative.isAbsolute()) throw new IllegalArgumentException("object key must be relative");
        for (Path part : relative) {
            if (part.toString().equals("..")) throw new IllegalArgumentException("object key must not contain traversal segments");
        }
        if (key.indexOf('\\') >= 0) throw new IllegalArgumentException("object key must use portable slash separators");
        Path resolved = root.resolve(relative).normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("object key escapes storage root");
        }
        rejectSymbolicLinks(resolved);
        return resolved;
    }

    private void createDirectoriesSafely(Path directory) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("storage root must not be a symbolic link");
        Path relative = root.relativize(directory);
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("storage path component is not a safe directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private void rejectSymbolicLinks(Path path) {
        Path current = root;
        if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("storage root must not be a symbolic link");
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("symbolic links are not allowed in object paths");
        }
    }
}
