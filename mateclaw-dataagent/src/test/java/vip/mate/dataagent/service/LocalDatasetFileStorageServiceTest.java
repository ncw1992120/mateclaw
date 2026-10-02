package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.service.impl.LocalDatasetFileStorageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetFileStorageServiceTest {
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsCsvJsonXlsxAndParquetWithStableWorkspaceKeys() throws Exception {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);
        DatasetAccessContext context = context(7L);
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("csv", "id\n1\n".getBytes(StandardCharsets.UTF_8));
        payloads.put("json", "[{\"id\":1}]".getBytes(StandardCharsets.UTF_8));
        payloads.put("xlsx", xlsx());
        payloads.put("parquet", "PAR1parquet-body".getBytes(StandardCharsets.UTF_8));

        for (Map.Entry<String, byte[]> sample : payloads.entrySet()) {
            StoredFileRef ref = service.put(context, 22L, "sample." + sample.getKey(),
                    new ByteArrayInputStream(sample.getValue()), sample.getValue().length);

            assertTrue(ref.objectId().matches("datasets/7/[0-9a-f-]{36}"));
            assertEquals(7L, ref.workspaceId());
            assertEquals(sample.getKey(), ref.format());
            assertEquals(sample.getValue().length, ref.size());
            assertTrue(ref.digest().startsWith("sha256:"));
            try (InputStream input = service.open(context, ref)) {
                assertArrayEquals(sample.getValue(), input.readAllBytes());
            }
        }
    }

    @Test
    void scopesKeysToWorkspaceAndAllowsReopenFromAnotherInstanceUsingSameRoot() throws Exception {
        DatasetAccessContext owner = context(7L);
        LocalDatasetFileStorageService first = new LocalDatasetFileStorageService(tempDir);
        byte[] bytes = "id\n1\n".getBytes(StandardCharsets.UTF_8);
        StoredFileRef ref = first.put(owner, 22L, "orders.csv", new ByteArrayInputStream(bytes), bytes.length);
        LocalDatasetFileStorageService reopened = new LocalDatasetFileStorageService(tempDir);

        try (InputStream input = reopened.open(owner, ref)) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        DatasetAccessContext otherWorkspace = context(8L);
        DatasetReadException openError = assertThrows(DatasetReadException.class,
                () -> reopened.open(otherWorkspace, ref));
        assertEquals(DatasetReadErrorCode.ACCESS_DENIED, openError.code());
        assertThrows(DatasetReadException.class, () -> reopened.delete(otherWorkspace, ref));
        assertTrue(Files.exists(tempDir.resolve(ref.objectId())));
    }

    @Test
    void rejectsDeclaredSizeMismatch() {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> service.put(context(7L), 22L,
                "orders.csv", new ByteArrayInputStream(new byte[]{1, 2, 3}), 4));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
    }

    @Test
    void rejectsOversizedDeclarationUnsupportedExtensionAndInvalidSignatures() {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "large.csv", InputStream.nullInputStream(), MAX_FILE_BYTES + 1))
                .code());
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "file.exe", InputStream.nullInputStream(), 0)).code());
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "invalid.json",
                        new ByteArrayInputStream("not-json".getBytes(StandardCharsets.UTF_8)), 8)).code());
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "invalid.parquet",
                        new ByteArrayInputStream("NOPE".getBytes(StandardCharsets.UTF_8)), 4)).code());
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "invalid.xlsx",
                        new ByteArrayInputStream("NO".getBytes(StandardCharsets.UTF_8)), 2)).code());
    }

    @Test
    void rejectsXlsxArchiveWithExcessiveCompressionRatio() throws Exception {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);
        byte[] repeated = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(repeated, (byte) 'a');
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(repeated);
            zip.closeEntry();
        }
        byte[] payload = archive.toByteArray();

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, assertThrows(DatasetReadException.class,
                () -> service.put(context(7L), 22L, "bomb.xlsx", new ByteArrayInputStream(payload), payload.length))
                .code());
    }

    @Test
    void classifiesMalformedStoredObjectKeysAsInvalidRequest() {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);
        StoredFileRef malformed = new StoredFileRef("datasets/7/" + (char) 0 + "bad", 7L, 22L,
                "orders.csv", "csv", 1L, "sha256:placeholder");

        DatasetReadException openError = assertThrows(DatasetReadException.class,
                () -> service.open(context(7L), malformed));
        DatasetReadException deleteError = assertThrows(DatasetReadException.class,
                () -> service.delete(context(7L), malformed));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, openError.code());
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, deleteError.code());
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        LocalDatasetFileStorageService service = new LocalDatasetFileStorageService(tempDir);
        byte[] bytes = "id\n1\n".getBytes(StandardCharsets.UTF_8);
        StoredFileRef ref = service.put(context(7L), 22L, "orders.csv", new ByteArrayInputStream(bytes), bytes.length);

        service.delete(context(7L), ref);
        service.delete(context(7L), ref);

        assertFalse(Files.exists(tempDir.resolve(ref.objectId())));
    }

    private static DatasetAccessContext context(Long workspaceId) {
        return new DatasetAccessContext(workspaceId, 11L, "upload-task", Set.of());
    }

    private static byte[] xlsx() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write("<worksheet/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
