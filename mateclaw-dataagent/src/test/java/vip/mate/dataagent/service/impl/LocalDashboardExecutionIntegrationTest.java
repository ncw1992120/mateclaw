package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import vip.mate.dataagent.model.DashboardExecutionEntity;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.DashboardExecutionService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.code.PythonExecutionService;
import vip.mate.dataagent.service.code.ScriptResultContractService;
import vip.mate.dataagent.service.code.ScriptTaskPreparationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class LocalDashboardExecutionIntegrationTest {
    @Mock DashboardExecutionMapper executionMapper;

    @Test
    void test_startup_marks_unowned_running_executions_failed() throws Exception {
        DashboardExecutionEntity running = execution("running-id", "RUNNING");
        DashboardExecutionEntity submitting = execution("submitting-id", "SUBMITTING");
        DashboardExecutionEntity succeeded = execution("succeeded-id", "SUCCEEDED");
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(running, submitting));

        new DashboardExecutionStartupRecovery(executionMapper).run(new DefaultApplicationArguments());

        assertEquals("FAILED", running.getStatus());
        assertEquals("DataAgent restarted before Python execution completed", running.getErrorMessage());
        assertEquals(1, running.getReturnCode());
        assertEquals("FAILED", submitting.getStatus());
        assertEquals("SUCCEEDED", succeeded.getStatus());
        ArgumentCaptor<DashboardExecutionEntity> updated = ArgumentCaptor.forClass(DashboardExecutionEntity.class);
        verify(executionMapper, org.mockito.Mockito.times(2)).updateById(updated.capture());
        assertEquals(List.of("running-id", "submitting-id"), updated.getAllValues().stream()
                .map(DashboardExecutionEntity::getExecutionId).toList());

        WorkspaceGuard workspaceGuard = mock(WorkspaceGuard.class);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(7L);
        when(executionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(running);
        PythonExecutionService localExecution = mock(PythonExecutionService.class);
        doThrow(new IllegalArgumentException("task not found")).when(localExecution).getStatus("running-id");
        DashboardExecutionService dashboardExecution = new DashboardExecutionServiceImpl(
                mock(InsightDashboardService.class), mock(ScriptTaskPreparationService.class), localExecution,
                workspaceGuard, new ObjectMapper(), executionMapper, mock(ObjectRefService.class),
                new ScriptResultContractService(), "http://localhost:18089/dataagent/api");
        assertEquals("FAILED", dashboardExecution.status("running-id").get("status"));
    }

    private DashboardExecutionEntity execution(String id, String status) {
        DashboardExecutionEntity entity = new DashboardExecutionEntity();
        entity.setExecutionId(id);
        entity.setStatus(status);
        return entity;
    }
}
