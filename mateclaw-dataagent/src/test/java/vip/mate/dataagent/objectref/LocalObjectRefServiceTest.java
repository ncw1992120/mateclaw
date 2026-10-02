package vip.mate.dataagent.objectref;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetBatch;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.ObjectRef;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocalObjectRefServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void localServiceIsTheOnlyScannedObjectRefBeanAndReadsManagedDatasetObjects() throws Exception {
        try (AnnotationConfigApplicationContext application = new AnnotationConfigApplicationContext()) {
            application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "mateclaw.storage.root", tempDir.toString(),
                    "mateclaw.storage.object-ref.ttl-seconds", "3600")));
            application.register(ObjectRefConfiguration.class);
            application.refresh();

            Map<String, ObjectRefService> services = application.getBeansOfType(ObjectRefService.class);
            assertEquals(1, services.size());
            ObjectRefService service = services.values().iterator().next();
            DatasetAccessContext context = context("task-managed", 10L);
            String key = "datasets/10/managed-object";
            byte[] payload = "managed file dataset".getBytes(StandardCharsets.UTF_8);
            new LocalObjectFileStore(tempDir).write(key, new ByteArrayInputStream(payload));
            ObjectRef managed = new ObjectRef(key, 10L, "upload-task", "csv", "sha256:managed", 0L);

            try (var input = service.open(context, managed)) {
                assertArrayEquals(payload, input.readAllBytes());
            }
        }
    }

    @Test
    void classifiesInvalidManagedObjectKeyAsInvalidRequest() {
        LocalObjectRefService service = service(3600);
        ObjectRef invalidKey = new ObjectRef("datasets/10/../outside", 10L, "upload-task", "csv", "sha256:managed", 0L);

        DatasetReadException error = assertThrows(DatasetReadException.class,
                () -> service.open(context("task-managed", 10L), invalidKey));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
    }

    @Test
    void scopesReferencesToWorkspaceAndTaskAndVerifiesSha256() throws Exception {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-a", 10L);
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream(payload));

        assertTrue(ref.objectId().matches("task-a/[0-9a-f-]{36}"));
        assertEquals(10L, ref.workspaceId());
        assertEquals("sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", ref.digest());
        try (var input = service.open(context, ref)) {
            assertArrayEquals(payload, input.readAllBytes());
        }
        assertEquals(DatasetReadErrorCode.ACCESS_DENIED,
                assertThrows(DatasetReadException.class, () -> service.open(context("task-b", 10L), ref)).code());
        assertEquals(DatasetReadErrorCode.ACCESS_DENIED,
                assertThrows(DatasetReadException.class, () -> service.open(context("task-a", 11L), ref)).code());

        LocalObjectFileStore files = new LocalObjectFileStore(tempDir);
        files.write(ref.objectId(), new ByteArrayInputStream("tampered".getBytes(StandardCharsets.UTF_8)));
        try (var input = service.open(context, ref)) {
            assertThrows(IOException.class, input::readAllBytes);
        }
    }

    @Test
    void closingValidPartiallyReadObjectDrainsAndVerifiesRemainingBytes() throws Exception {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-partial-valid", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        var input = service.open(context, ref);
        assertEquals('h', input.read());
        assertDoesNotThrow(input::close);
    }

    @Test
    void closingPartiallyReadObjectDetectsTamperingInUnreadTail() throws Exception {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-partial-tampered", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream("h".getBytes(StandardCharsets.UTF_8)));
        new LocalObjectFileStore(tempDir).write(ref.objectId(),
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        var input = service.open(context, ref);
        assertEquals('h', input.read());
        IOException error = assertThrows(IOException.class, input::close);
        assertEquals("object digest mismatch", error.getMessage());
    }

    @Test
    void rejectsExpiredReferenceAndBestEffortDeletesItsFile() throws Exception {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-expired", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream(new byte[]{1}));
        ObjectRef expired = new ObjectRef(ref.objectId(), ref.workspaceId(), ref.taskId(), ref.format(), ref.digest(),
                Instant.now().minusSeconds(1).toEpochMilli());

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> service.open(context, expired));

        assertEquals(DatasetReadErrorCode.ACCESS_DENIED, error.code());
        assertFalse(Files.exists(tempDir.resolve(ref.objectId())));
    }

    @Test
    void expireIsIdempotent() {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-expire", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream(new byte[]{1}));

        assertDoesNotThrow(() -> {
            service.expire(ref);
            service.expire(ref);
        });
    }

    @Test
    void roundTripsParquetDatasetBatch() {
        LocalObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-parquet", 10L);
        DatasetBatch batch = new DatasetBatch(List.of(
                Map.of("id", 1L, "status", "paid", "amount", 12.5d),
                Map.of("id", 2L, "status", "refunded", "amount", 0.125d)), null, 2, true);

        ObjectRef ref = DatasetBatchCodec.writeParquet(context, batch, service);

        assertEquals("parquet", ref.format());
        assertNotNull(DatasetBatchCodec.readSchema(context, ref, service));
        List<Map<String, Object>> rows = DatasetBatchCodec.readRows(context, ref, service, 10);
        assertEquals(2, rows.size());
        assertEquals(1L, rows.get(0).get("id"));
        assertEquals("paid", rows.get(0).get("status"));
        assertEquals(12.5d, ((Number) rows.get(0).get("amount")).doubleValue());
        assertEquals("refunded", rows.get(1).get("status"));
    }

    private LocalObjectRefService service(long ttlSeconds) {
        return new LocalObjectRefService(tempDir, ttlSeconds);
    }

    private DatasetAccessContext context(String taskId, Long workspaceId) {
        return new DatasetAccessContext(workspaceId, 20L, taskId, Set.of(1L));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = LocalObjectRefService.class)
    static class ObjectRefConfiguration { }

}
