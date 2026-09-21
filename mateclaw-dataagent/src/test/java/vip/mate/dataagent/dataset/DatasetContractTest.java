package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatasetContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void inputDescriptorDoesNotSerializeCredentials() throws Exception {
        DatasetInputDescriptor descriptor = new DatasetInputDescriptor(
                42L,
                "orders",
                DatasetSourceType.JDBC_SQL,
                List.of(new DatasetColumn("id", "订单号", "BIGINT", false, null)),
                10L,
                Map.of("jdbcUrl", "jdbc:mysql://secret", "username", "root", "password", "top-secret"),
                null);

        String json = objectMapper.writeValueAsString(descriptor);

        assertFalse(json.contains("top-secret"));
        assertFalse(json.contains("jdbc:mysql://secret"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("jdbcUrl"));
    }

    @Test
    void batchCannotContainInlineRowsAndObjectRefTogether() {
        ObjectRef ref = new ObjectRef("obj-1", 1L, "task-2", "parquet", "sha256:x", 1000L);
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetBatch(List.of(Map.of("id", 1)), ref, 1L, true));
    }

    @Test
    void unknownFilterRoleAndOperatorAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetFilter("id", "unknown", "eq", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetFilter("id", "dimension", "contains-anything", 1));
    }

    @Test
    void jsonEntryAcceptsOpAliasSymbolOperatorAndDefaultRole() {
        DatasetFilter filter = objectMapper.convertValue(
                Map.of("field", "metric_time", "op", "=", "value", "2026-09-01"), DatasetFilter.class);
        assertEquals("metric_time", filter.field());
        assertEquals("dimension", filter.role());
        assertEquals("eq", filter.operator());
        assertEquals("2026-09-01", filter.value());

        DatasetFilter gte = objectMapper.convertValue(
                Map.of("field", "amount", "operator", ">=", "value", 10, "role", "measure"), DatasetFilter.class);
        assertEquals("gte", gte.operator());
        assertEquals("measure", gte.role());
    }

    @Test
    void jsonEntryRejectsUnknownOperatorWithReadableMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> objectMapper.convertValue(Map.of("field", "id", "op", "not-a-real-operator"), DatasetFilter.class));
        assertTrue(error.getMessage().contains("unsupported filter operator"), error.getMessage());
    }
}
