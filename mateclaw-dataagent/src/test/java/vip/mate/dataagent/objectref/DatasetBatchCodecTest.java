package vip.mate.dataagent.objectref;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;

import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DatasetBatchCodecTest {
    @Test void writesParquetWithCommonTypesAndReadsSchema() {
        var context = new DatasetAccessContext(1L, 2L, "task-codec", Set.of());
        var batch = new DatasetBatch(List.of(Map.of("id", 1L, "amount", 2.5d, "ok", true, "note", "x")), null, 1, true);
        CapturingRefs refs = new CapturingRefs(); ObjectRef reference = DatasetBatchCodec.writeParquet(context, batch, refs);
        assertEquals("parquet", reference.format()); assertTrue(reference.digest().startsWith("sha256:"));
        assertEquals(Set.of("id", "amount", "ok", "note"), new HashSet<>(DatasetBatchCodec.readSchema(context, reference, refs).getFields().stream().map(f -> f.name()).toList()));
        List<Map<String, Object>> rows = DatasetBatchCodec.readRows(context, reference, refs, 10);
        assertEquals(1, rows.size()); assertEquals(1L, rows.getFirst().get("id")); assertEquals("x", rows.getFirst().get("note"));
    }

    @Test void preservesDateTimeAndDecimalTypes() {
        var context = new DatasetAccessContext(1L, 2L, "task-typed", Set.of());
        var batch = new DatasetBatch(List.of(Map.of(
                "day", LocalDate.of(2026, 9, 12),
                "createdAt", Instant.parse("2026-09-12T01:02:03Z"),
                "amount", new BigDecimal("123.4500"))), null, 1, true);
        CapturingRefs refs = new CapturingRefs();

        ObjectRef reference = DatasetBatchCodec.writeParquet(context, batch, refs);
        Map<String, Object> row = DatasetBatchCodec.readRows(context, reference, refs, 10).getFirst();

        assertEquals(LocalDate.of(2026, 9, 12), row.get("day"));
        assertEquals(Instant.parse("2026-09-12T01:02:03Z"), row.get("createdAt"));
        assertEquals(new BigDecimal("123.4500"), row.get("amount"));
    }

    @Test void preservesNullValuesAcrossMultipleRows() {
        var context = new DatasetAccessContext(1L, 2L, "task-null", Set.of());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", 1L);
        first.put("label", null);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", 2L);
        second.put("label", "ready");

        CapturingRefs refs = new CapturingRefs();
        ObjectRef reference = DatasetBatchCodec.writeParquet(context,
                new DatasetBatch(List.of(first, second), null, 2, true), refs);

        List<Map<String, Object>> rows = DatasetBatchCodec.readRows(context, reference, refs, 10);
        assertEquals(2, rows.size());
        assertNull(rows.getFirst().get("label"));
        assertEquals("ready", rows.get(1).get("label"));
    }

    @Test void rejectsRowsWithMismatchedSchemaInsteadOfDroppingColumns() {
        var context = new DatasetAccessContext(1L, 2L, "task-schema", Set.of());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", 1L);
        first.put("label", "ready");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", 2L);
        second.put("extra", "unexpected");

        DatasetReadException error = assertThrows(DatasetReadException.class,
                () -> DatasetBatchCodec.writeParquet(context,
                        new DatasetBatch(List.of(first, second), null, 2, true), new CapturingRefs()));
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
    }

    @Test void preservesDecimalsWhenRowsUseDifferentScales() {
        var context = new DatasetAccessContext(1L, 2L, "task-decimal-scales", Set.of());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("amount", new BigDecimal("999.9"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("amount", new BigDecimal("0.1234"));
        CapturingRefs refs = new CapturingRefs();

        ObjectRef reference = DatasetBatchCodec.writeParquet(context,
                new DatasetBatch(List.of(first, second), null, 2, true), refs);
        List<Map<String, Object>> rows = DatasetBatchCodec.readRows(context, reference, refs, 10);

        assertEquals(new BigDecimal("999.9000"), rows.getFirst().get("amount"));
        assertEquals(new BigDecimal("0.1234"), rows.get(1).get("amount"));
    }

    @Test void stopsReadingAtTheRequestedPreviewLimit() {
        var context = new DatasetAccessContext(1L, 2L, "task-preview-limit", Set.of());
        List<Map<String, Object>> sourceRows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            sourceRows.add(Map.of("id", (long) i));
        }
        CapturingRefs refs = new CapturingRefs();

        ObjectRef reference = DatasetBatchCodec.writeParquet(context,
                new DatasetBatch(sourceRows, null, sourceRows.size(), true), refs);

        List<Map<String, Object>> previewRows = DatasetBatchCodec.readRows(context, reference, refs, 10);

        assertEquals(10, previewRows.size());
        assertEquals(0L, previewRows.getFirst().get("id"));
        assertEquals(9L, previewRows.getLast().get("id"));
    }

    private static final class CapturingRefs implements ObjectRefService {
        private byte[] bytes;
        @Override public ObjectRef put(DatasetAccessContext c, String format, InputStream in) { try { bytes=in.readAllBytes(); } catch(IOException e){throw new RuntimeException(e);} return new ObjectRef("batch",c.workspaceId(),c.taskId(),format,"sha256:test",System.currentTimeMillis()+60000); }
        @Override public InputStream open(DatasetAccessContext c, ObjectRef r) { return new ByteArrayInputStream(bytes); }
        @Override public void expire(ObjectRef r) {}
    }
}
