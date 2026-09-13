package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.service.code.*;
import vip.mate.dataagent.service.impl.DatasetExecutionServiceImpl;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetExecutionServiceTest {
    @Test void selectsAdapterFromCatalogDescriptor() {
        DatasetManageService catalog=mock(DatasetManageService.class); DatasetSourceAdapter adapter=mock(DatasetSourceAdapter.class);
        var descriptor=new DatasetInputDescriptor(9,"orders",DatasetSourceType.JDBC_TABLE,List.of(),null,Map.of(),null);
        when(catalog.getInputDescriptor(any(),eq(9L),eq("orders"))).thenReturn(descriptor); when(adapter.supports(DatasetSourceType.JDBC_TABLE)).thenReturn(true); when(adapter.describe(any(),eq(9L))).thenReturn(descriptor);
        var service=new DatasetExecutionServiceImpl(List.of(adapter),mock(ScriptTaskPreparationService.class),catalog);
        var context=new DatasetAccessContext(1L,2L,"preview",Set.of(9L));
        assertEquals(DatasetSourceType.JDBC_TABLE,service.descriptor(context,9L,"orders").sourceType()); verify(adapter).describe(context,9L);
    }

    @ParameterizedTest(name = "统一契约选择 {0} Adapter")
    @EnumSource(DatasetSourceType.class)
    void selectsEveryFirstPhaseSourceThroughTheSameAdapterContract(DatasetSourceType sourceType) {
        DatasetManageService catalog = mock(DatasetManageService.class);
        DatasetSourceAdapter adapter = mock(DatasetSourceAdapter.class);
        DatasetInputDescriptor descriptor = new DatasetInputDescriptor(
                9, "orders", sourceType, List.of(), null, Map.of(), null);
        DatasetAccessContext context = new DatasetAccessContext(1L, 2L, "preview", Set.of(9L));

        when(catalog.getInputDescriptor(context, 9L, "orders")).thenReturn(descriptor);
        when(adapter.supports(sourceType)).thenReturn(true);
        when(adapter.describe(context, 9L)).thenReturn(descriptor);

        DatasetExecutionService service = new DatasetExecutionServiceImpl(
                List.of(adapter), mock(ScriptTaskPreparationService.class), catalog);

        assertSame(descriptor, service.descriptor(context, 9L, "orders"));
        verify(adapter).describe(context, 9L);
        verify(adapter, never()).read(any(), any());
    }

    @ParameterizedTest(name = "统一契约读取 {0} Adapter")
    @EnumSource(DatasetSourceType.class)
    @DisplayName("CAT-U06 统一契约读取五种来源")
    void readsEveryFirstPhaseSourceThroughTheSameAdapterContract(DatasetSourceType sourceType) {
        DatasetManageService catalog = mock(DatasetManageService.class);
        DatasetSourceAdapter adapter = mock(DatasetSourceAdapter.class);
        DatasetInputDescriptor descriptor = new DatasetInputDescriptor(
                9, "orders", sourceType, List.of(), null, Map.of(), null);
        DatasetAccessContext context = new DatasetAccessContext(1L, 2L, "preview", Set.of(9L));
        DatasetReadRequest request = new DatasetReadRequest(
                9L, "orders", List.of(), List.of(), 10, 0, Map.of());
        DatasetBatch batch = new DatasetBatch(List.of(Map.of("id", 1)), null, 1, true,
                new PushdownReport(List.of(), List.of(), true, true, null));

        when(catalog.getInputDescriptor(context, 9L, "orders")).thenReturn(descriptor);
        when(adapter.supports(sourceType)).thenReturn(true);
        when(adapter.read(context, request)).thenReturn(batch);

        DatasetExecutionService service = new DatasetExecutionServiceImpl(
                List.of(adapter), mock(ScriptTaskPreparationService.class), catalog);

        assertSame(batch, service.preview(context, request));
        verify(adapter).read(context, request);
    }

    @Test
    @DisplayName("CAT-U07 未注册 Adapter 明确拒绝")
    void rejectsCatalogSourceWithoutRegisteredAdapter() {
        DatasetManageService catalog = mock(DatasetManageService.class);
        DatasetInputDescriptor descriptor = new DatasetInputDescriptor(
                9, "orders", DatasetSourceType.FILE, List.of(), null, Map.of(), null);
        DatasetAccessContext context = new DatasetAccessContext(1L, 2L, "preview", Set.of(9L));
        when(catalog.getInputDescriptor(context, 9L, "orders")).thenReturn(descriptor);

        DatasetExecutionService service = new DatasetExecutionServiceImpl(
                List.of(), mock(ScriptTaskPreparationService.class), catalog);

        DatasetReadException error = assertThrows(DatasetReadException.class,
                () -> service.descriptor(context, 9L, "orders"));
        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        assertEquals("未找到数据集 Adapter", error.getMessage());
    }
}
