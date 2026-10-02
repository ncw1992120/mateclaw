package vip.mate.dataagent.objectref;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalObjectFileStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsOpaqueRelativeKey() throws Exception {
        LocalObjectFileStore store = new LocalObjectFileStore(tempDir.resolve("objects"));
        String key = "task-opaque/1a2b3c4d-uuid";
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);

        store.write(key, new ByteArrayInputStream(content));

        try (var input = store.open(key)) {
            assertArrayEquals(content, input.readAllBytes());
        }
        assertEquals(tempDir.resolve("objects").resolve(key).toAbsolutePath().normalize(), store.resolve(key));
    }

    @Test
    void openingMissingKeyReportsIoFailure() throws Exception {
        LocalObjectFileStore store = new LocalObjectFileStore(tempDir.resolve("objects"));

        assertThrows(IOException.class, () -> store.open("task/missing"));
    }

    @Test
    void rejectsAbsoluteAndTraversalKeys() {
        LocalObjectFileStore store = new LocalObjectFileStore(tempDir.resolve("objects"));

        assertThrows(IllegalArgumentException.class, () -> store.resolve("/tmp/outside"));
        assertThrows(IllegalArgumentException.class, () -> store.resolve("task/../../outside"));
        assertThrows(IllegalArgumentException.class, () -> store.resolve("task/../inside"));
    }

    @Test
    void rejectsSymlinkEscapeAndKeepsResolvedPathWithinRoot() throws Exception {
        Path root = tempDir.resolve("objects");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(root.resolve("escape"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("symbolic links unavailable: " + e.getMessage());
        }
        LocalObjectFileStore store = new LocalObjectFileStore(root);

        assertThrows(IllegalArgumentException.class, () -> store.resolve("escape/file"));
        assertTrue(store.resolve("task/file").startsWith(root.toAbsolutePath().normalize()));
    }

    @Test
    void failedWriteRemovesTemporarySiblingAndDoesNotPublishPartialFile() throws Exception {
        Path root = tempDir.resolve("objects");
        LocalObjectFileStore store = new LocalObjectFileStore(root);
        InputStream broken = new InputStream() {
            private boolean emitted;
            @Override public int read() throws IOException {
                if (!emitted) { emitted = true; return 'x'; }
                throw new IOException("source failed");
            }
        };

        assertThrows(IOException.class, () -> store.write("task/object", broken));
        assertFalse(Files.exists(root.resolve("task/object")));
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
        }
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        LocalObjectFileStore store = new LocalObjectFileStore(tempDir.resolve("objects"));
        store.write("task/object", new ByteArrayInputStream(new byte[]{1}));

        store.delete("task/object");
        store.delete("task/object");
        store.delete("missing/parent/object");

        assertFalse(Files.exists(tempDir.resolve("objects/task/object")));
    }
}
