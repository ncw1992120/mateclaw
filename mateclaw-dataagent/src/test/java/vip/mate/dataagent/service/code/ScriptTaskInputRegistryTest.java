package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
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
}
