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
}
