package vip.mate.dataagent.controller.internal;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.code.ScriptDatasetReadTokenService;
import vip.mate.dataagent.service.code.ScriptTaskInputRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ScriptDatasetReadControllerTest {
    @Test
    void resolvesDatasetIdOnlyFromTaskAliasAndForwardsReadRequest() {
        var registry = new ScriptTaskInputRegistry();
        var tokens = new ScriptDatasetReadTokenService("unit-secret");
        var adapter = mock(DatasetSourceAdapter.class);
        var refs = mock(ObjectRefService.class);
        var descriptor = new DatasetInputDescriptor(3L, "orders", DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null);
        registry.register("task-1", Map.of("orders", descriptor));
        when(adapter.supports(DatasetSourceType.JDBC_TABLE)).thenReturn(true);
        when(adapter.read(any(), any())).thenReturn(new DatasetBatch(List.of(Map.of("id", 1)), null, 1, true));
        var controller = new ScriptDatasetReadController(registry, tokens, List.of(adapter), refs);

        var response = controller.read("task-1", "Bearer " + tokens.issue("task-1", 7L, 60),
                new ScriptDatasetReadController.ReadBody("orders", List.of("id"), List.of(), 10, 0, Map.of()));

        assertEquals(1, response.getData().rows().size());
        verify(adapter).read(any(DatasetAccessContext.class), argThat(request -> request.datasetId() == 3L
                && request.columns().equals(List.of("id"))));
    }

    @Test
    void rejectsTokenIssuedForAnotherTaskBeforeLookingUpAlias() {
        var registry = new ScriptTaskInputRegistry();
        var tokens = new ScriptDatasetReadTokenService("unit-secret");
        var controller = new ScriptDatasetReadController(registry, tokens, List.of(), mock(ObjectRefService.class));

        assertThrows(IllegalArgumentException.class, () -> controller.read("task-1",
                "Bearer " + tokens.issue("task-2", 7L, 60),
                new ScriptDatasetReadController.ReadBody("orders", List.of(), List.of(), 10, 0, Map.of())));
    }
}
