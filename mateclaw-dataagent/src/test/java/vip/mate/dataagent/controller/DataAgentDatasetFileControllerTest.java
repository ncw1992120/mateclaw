package vip.mate.dataagent.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.interceptor.DataAgentWorkspaceInterceptor;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.service.DatasetFileStorageService;
import vip.mate.sdk.service.MateClawRuntime;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DataAgentDatasetFileControllerTest {

    @Mock DatasetFileStorageService storageService;
    @Mock WorkspaceGuard workspaceGuard;
    @Mock MateClawRuntime runtime;

    @org.junit.jupiter.api.AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void uploadCreatesWorkspaceScopedContextAndNeverAcceptsPath() throws Exception {
        DataAgentDatasetFileController controller = new DataAgentDatasetFileController(storageService, workspaceGuard);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8));
        StoredFileRef ref = new StoredFileRef("datasets/11/object-1", 11L, 99L, "orders.csv", "csv", file.getSize(), "sha256:test");
        when(storageService.put(any(), eq(99L), eq("orders.csv"), any(), eq(file.getSize()))).thenReturn(ref);

        assertNotNull(controller.upload(file));
        verify(storageService).put(argThat(context -> context.workspaceId().equals(11L)
                        && context.userId().equals(99L)
                        && context.taskId().equals("upload-99")),
                eq(99L), eq("orders.csv"), any(), eq(file.getSize()));
    }

    @Test
    void uploadRouteReachesHandlerAfterWorkspaceCheck() throws Exception {
        when(runtime.hasWorkspacePermission(11L, 99L, "member")).thenReturn(true);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        UserContextHolder.set(new UserContext(99L, "member", "Member", "user", 11L));
        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new DataAgentDatasetFileController(storageService, workspaceGuard))
                .addInterceptors(new DataAgentWorkspaceInterceptor(runtime))
                .build();

        mvc.perform(multipart("/v1/dataset-files").file(file))
                .andExpect(status().isOk());
        verify(storageService).put(any(), eq(99L), eq("orders.csv"), any(), eq(file.getSize()));
    }
}
