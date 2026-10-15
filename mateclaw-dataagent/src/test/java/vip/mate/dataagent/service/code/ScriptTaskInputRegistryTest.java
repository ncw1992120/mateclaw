package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptTaskInputRegistryTest {
    @Test void aliasesAreImmutableAndTaskScoped() {
        var registry = new ScriptTaskInputRegistry();
        var descriptor = new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,java.util.List.of(),null,Map.of(),null);
        registry.register("task-1", Map.of("orders", descriptor));
        assertEquals(3, registry.require("task-1","orders").datasetId());
        assertThrows(IllegalArgumentException.class, () -> registry.require("task-2","orders"));
        assertThrows(IllegalStateException.class, () -> registry.register("task-1", Map.of("other", descriptor)));
    }

    @Test void rejectsAliasesThatCannotBeUsedAsDatasetInputNames() {
        var registry = new ScriptTaskInputRegistry();
        var descriptor = new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,java.util.List.of(),null,Map.of(),null);
        assertThrows(IllegalArgumentException.class, () -> registry.register("task-2", Map.of("orders-all", descriptor)));
        assertThrows(IllegalArgumentException.class, () -> registry.register("task-3", Map.of("a".repeat(65), descriptor)));
    }

    @Test void preparedRowsPreserveNullCellValuesAndRemainImmutable() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer_id", 42L);
        row.put("optional_dimension", null);

        var prepared = assertDoesNotThrow(() -> new ScriptTaskInputRegistry.PreparedInput(
                "orders", List.of(), List.of(row), null, 1, 0));

        assertNull(prepared.inlineRows().get(0).get("optional_dimension"));
        assertThrows(UnsupportedOperationException.class,
                () -> prepared.inlineRows().get(0).put("customer_id", 99L));
    }
}
