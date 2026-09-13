package vip.mate.dataagent.objectref;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetBatch;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.ObjectRef;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ObjectRefServiceTest {
    private static final String ACCESS = "minioadmin";
    private static final String SECRET = "minioadmin";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-12-18T13-15-44Z")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET)
            .withCommand("server /data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void storesReadsScopesAndExpiresObject() throws Exception {
        S3ObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-1", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        assertEquals("task-1", ref.taskId());
        assertTrue(ref.digest().startsWith("sha256:"));
        try (var input = service.open(context, ref)) {
            assertEquals("hello", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThrows(DatasetReadException.class, () -> service.open(context("task-2", 10L), ref));
        assertThrows(DatasetReadException.class, () -> service.open(context("task-1", 11L), ref));

        ObjectRef expired = new ObjectRef(ref.objectId(), ref.workspaceId(), ref.taskId(), ref.format(), ref.digest(),
                Instant.now().minusSeconds(1).toEpochMilli());
        assertThrows(DatasetReadException.class, () -> service.open(context, expired));
        service.expire(ref);
        service.expire(ref);
    }

    @Test
    void detectsTamperedObjectDigest() throws Exception {
        S3ObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-tamper", 10L);
        ObjectRef ref = service.put(context, "json", new ByteArrayInputStream("original".getBytes(StandardCharsets.UTF_8)));
        MinioClient raw = MinioClient.builder().endpoint(endpoint()).credentials(ACCESS, SECRET).build();
        raw.putObject(PutObjectArgs.builder().bucket("mateclaw-test").object(ref.objectId())
                .stream(new ByteArrayInputStream("changed".getBytes(StandardCharsets.UTF_8)), 7, -1)
                .build());
        try (var input = service.open(context, ref)) {
            assertThrows(IOException.class, input::readAllBytes);
        }
    }

    @Test
    void roundTripsParquetBatchThroughRealMinioObjectRef() {
        S3ObjectRefService service = service(3600);
        DatasetAccessContext context = context("task-parquet", 10L);
        DatasetBatch batch = new DatasetBatch(List.of(
                row(1L, "paid", new BigDecimal("12.30"), LocalDate.of(2026, 9, 12)),
                row(2L, "refunded", new BigDecimal("0.1234"), LocalDate.of(2026, 9, 13))),
                null, 2, true);

        ObjectRef reference = DatasetBatchCodec.writeParquet(context, batch, service);

        assertEquals("parquet", reference.format());
        assertNotNull(DatasetBatchCodec.readSchema(context, reference, service));
        List<Map<String, Object>> rows = DatasetBatchCodec.readRows(context, reference, service, 10);
        assertEquals(batch.rows().size(), rows.size());
        assertEquals(batch.rows().get(0).get("id"), rows.get(0).get("id"));
        assertEquals(batch.rows().get(0).get("status"), rows.get(0).get("status"));
        assertEquals(0, ((BigDecimal) batch.rows().get(0).get("amount"))
                .compareTo((BigDecimal) rows.get(0).get("amount")));
        assertEquals(batch.rows().get(0).get("day"), rows.get(0).get("day"));
        assertEquals(0, ((BigDecimal) batch.rows().get(1).get("amount"))
                .compareTo((BigDecimal) rows.get(1).get("amount")));
    }

    private Map<String, Object> row(long id, String status, BigDecimal amount, LocalDate day) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("status", status);
        row.put("amount", amount);
        row.put("day", day);
        return row;
    }

    private S3ObjectRefService service(long ttl) {
        return new S3ObjectRefService(endpoint(), ACCESS, SECRET, "mateclaw-test", ttl);
    }

    private String endpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }

    private DatasetAccessContext context(String task, Long workspace) {
        return new DatasetAccessContext(workspace, 20L, task, Set.of(1L));
    }
}
