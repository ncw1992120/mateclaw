package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.service.DatasetManageService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptTaskPreparationServiceTest {
    @Test void registersAliasesBeforeIssuingToken() {
        DatasetManageService datasets=mock(DatasetManageService.class); var registry=new ScriptTaskInputRegistry(); var tokens=new ScriptDatasetReadTokenService("secret");
        when(datasets.getInputDescriptor(any(),eq(3L),eq("orders"))).thenReturn(new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,List.of(),null,Map.of(),null));
        var prepared=new ScriptTaskPreparationService(datasets,registry,tokens).prepare("task-1",7L,9L,Map.of("orders",3L),"print(1)",Map.of("region","east"));
        assertEquals(3,registry.require("task-1","orders").datasetId()); assertEquals("task-1",tokens.verify(prepared.readToken()).taskId());
    }
}
