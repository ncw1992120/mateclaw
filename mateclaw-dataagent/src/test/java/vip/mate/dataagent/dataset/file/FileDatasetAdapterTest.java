package vip.mate.dataagent.dataset.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.objectref.DatasetBatchCodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileDatasetAdapterTest {
    @Test
    void streamsCsvWithProjectionAndFilters() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class); ObjectRefService refs = mock(ObjectRefService.class);
        DatasetEntity d = new DatasetEntity(); d.setId(9L); d.setName("orders"); d.setSourceType("FILE");
        ObjectRef ref = new ObjectRef("task-1/file", 1L, "task-1", "csv", "sha256:x", System.currentTimeMillis()+60_000);
        d.setSourceConfig(new ObjectMapper().writeValueAsString(new FileDatasetDefinition(ref, "orders.csv", "csv", 1)));
        when(datasets.selectById(9L)).thenReturn(d); when(refs.open(any(), eq(ref))).thenReturn(new ByteArrayInputStream("id,status\n1,PAID\n2,CANCELLED\n".getBytes(StandardCharsets.UTF_8)));
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(9L)), new DatasetReadRequest(9L,"orders",List.of("id"),List.of(new DatasetFilter("status","dimension","eq","PAID")),10,0,Map.of()));
        assertEquals(List.of(Map.of("id", "1")), batch.rows());
        assertEquals(1, batch.pushdownReport().residualFilters().size());
    }

    @Test
    void appliesOffsetAndLimitAfterFilteringWithoutReadingPastRequestedPage() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class); ObjectRefService refs = mock(ObjectRefService.class);
        DatasetEntity d = dataset(10L, "orders.csv", "csv", 1);
        when(datasets.selectById(10L)).thenReturn(d);
        when(refs.open(any(), any())).thenReturn(new ByteArrayInputStream(("id,status\n1,PAID\n2,PAID\n3,CANCELLED\n4,PAID\n").getBytes(StandardCharsets.UTF_8)));
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());

        DatasetBatch batch = adapter.read(context(10L), new DatasetReadRequest(10L, "orders", List.of("id"),
                List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 2, 1, Map.of()));

        assertEquals(List.of(Map.of("id", "2"), Map.of("id", "4")), batch.rows());
        assertEquals(2, batch.rowCount());
        assertTrue(batch.last());
    }

    @Test
    void supportsCollectionAndRangeFiltersForStreamingFormats() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class); ObjectRefService refs = mock(ObjectRefService.class);
        DatasetEntity d = dataset(11L, "orders.json", "json", 1);
        when(datasets.selectById(11L)).thenReturn(d);
        when(refs.open(any(), any())).thenReturn(new ByteArrayInputStream(("[{\"id\":1,\"amount\":10},{\"id\":2,\"amount\":25},{\"id\":3,\"amount\":40}]").getBytes(StandardCharsets.UTF_8)));
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());

        DatasetBatch batch = adapter.read(context(11L), new DatasetReadRequest(11L, "orders", List.of("id"),
                List.of(new DatasetFilter("id", "dimension", "in", List.of(1, 3)),
                        new DatasetFilter("amount", "measure", "between", List.of(10, 40))), 10, 0, Map.of()));

        assertEquals(List.of(Map.of("id", 1), Map.of("id", 3)), batch.rows());
    }

    @Test
    void rejectsSchemaVersionMismatchBeforeOpeningObject() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class); ObjectRefService refs = mock(ObjectRefService.class);
        DatasetEntity d = dataset(12L, "orders.csv", "csv", 1);
        d.setSchemaVersion(2);
        when(datasets.selectById(12L)).thenReturn(d);
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());

        assertThrows(DatasetReadException.class, () -> adapter.read(context(12L), new DatasetReadRequest(12L, "orders", List.of(), List.of(), 10, 0, Map.of())));
        verifyNoInteractions(refs);
    }

    @Test
    void closesObjectStreamWhenScanningFails() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class); ObjectRefService refs = mock(ObjectRefService.class);
        DatasetEntity d = dataset(13L, "orders.csv", "csv", 1);
        when(datasets.selectById(13L)).thenReturn(d);
        TrackingInputStream input = new TrackingInputStream("id,status\n1,PAID\n2\n".getBytes(StandardCharsets.UTF_8));
        when(refs.open(any(), any())).thenReturn(input);
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());

        assertThrows(DatasetReadException.class, () -> adapter.read(context(13L), new DatasetReadRequest(13L, "orders", List.of(), List.of(), 10, 0, Map.of())));
        assertTrue(input.closed);
    }

    @Test
    void pushesParquetProjectionAndPredicateToReader() throws Exception {
        DatasetMapper datasets = mock(DatasetMapper.class); DatasetFieldMapper fields = mock(DatasetFieldMapper.class);
        ParquetRefs refs = new ParquetRefs();
        DatasetAccessContext context = context(14L);
        DatasetBatchCodec.writeParquet(context, new DatasetBatch(List.of(
                Map.of("id", 1L, "status", "PAID", "amount", 10L),
                Map.of("id", 2L, "status", "CANCELLED", "amount", 20L)), null, 2, true), refs);
        DatasetEntity d = dataset(14L, "orders.parquet", "parquet", 1);
        when(datasets.selectById(14L)).thenReturn(d);
        FileDatasetAdapter adapter = new FileDatasetAdapter(datasets, fields, refs, new ObjectMapper());

        DatasetBatch batch = adapter.read(context, new DatasetReadRequest(14L, "orders",
                List.of("id"), List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 10, 0, Map.of()));

        assertEquals(List.of(Map.of("id", 1L)), batch.rows());
        assertEquals(1, batch.pushdownReport().pushedFilters().size());
        assertTrue(batch.pushdownReport().projectionPushed());
        assertFalse(batch.pushdownReport().pushedFilters().isEmpty());
    }

    private DatasetEntity dataset(long id, String fileName, String format, int schemaVersion) throws Exception {
        DatasetEntity d = new DatasetEntity(); d.setId(id); d.setName("orders"); d.setSourceType("FILE"); d.setSchemaVersion(schemaVersion);
        ObjectRef ref = new ObjectRef("task-1/file-" + id, 1L, "task-1", format, "sha256:x", System.currentTimeMillis() + 60_000);
        d.setSourceConfig(new ObjectMapper().writeValueAsString(new FileDatasetDefinition(ref, fileName, format, schemaVersion)));
        return d;
    }

    private DatasetAccessContext context(long datasetId) {
        return new DatasetAccessContext(1L, 2L, "task-1", Set.of(datasetId));
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] data) { super(data); }

        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private static final class ParquetRefs implements ObjectRefService {
        private byte[] bytes;
        @Override public ObjectRef put(DatasetAccessContext context, String format, java.io.InputStream input) {
            try { bytes = input.readAllBytes(); } catch (IOException e) { throw new RuntimeException(e); }
            return new ObjectRef("task-1/parquet", context.workspaceId(), context.taskId(), format, "sha256:x", System.currentTimeMillis() + 60_000);
        }
        @Override public InputStream open(DatasetAccessContext context, ObjectRef reference) { return new ByteArrayInputStream(bytes); }
        @Override public void expire(ObjectRef reference) { }
    }
}
