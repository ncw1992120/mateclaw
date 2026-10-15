package vip.mate.dataagent.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.interceptor.DataAgentWorkspaceInterceptor;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.service.DatasetExecutionService;
import vip.mate.dataagent.service.DatasetManageService;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DatasetManagementMvcSecurityTest {

    @Mock DatasetManageService datasetService;
    @Mock DatasetExecutionService executionService;
    @Mock WorkspaceGuard workspaceGuard;
    @Mock MateClawRuntime runtime;

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void viewerCanReadDatasetListThroughWorkspaceInterceptor() throws Exception {
        when(runtime.hasWorkspacePermission(11L, 99L, "viewer")).thenReturn(true);
        when(datasetService.listDatasets()).thenReturn(List.of());
        MockMvc mvc = mvc();
        UserContextHolder.set(new UserContext(99L, "viewer", "Viewer", "user", 11L));

        mvc.perform(get("/v1/datasets")).andExpect(status().isOk());
        verify(datasetService).listDatasets();
    }

    @Test
    void viewerCannotCreateDataset() throws Exception {
        when(runtime.hasWorkspacePermission(11L, 99L, "member")).thenReturn(false);
        MockMvc mvc = mvc();
        UserContextHolder.set(new UserContext(99L, "viewer", "Viewer", "user", 11L));

        mvc.perform(post("/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"orders\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(datasetService);
    }

    @Test
    void memberCanReachCreateHandlerAfterWorkspaceCheck() throws Exception {
        when(runtime.hasWorkspacePermission(11L, 99L, "member")).thenReturn(true);
        MockMvc mvc = mvc();
        UserContextHolder.set(new UserContext(99L, "member", "Member", "user", 11L));

        mvc.perform(post("/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"orders\"}"))
                .andExpect(status().isOk());
        verify(datasetService).createDataset(any());
    }

    @Test
    void everyDatasetManagementRouteReachesItsHandlerAfterWorkspaceCheck() throws Exception {
        when(runtime.hasWorkspacePermission(11L, 99L, "viewer")).thenReturn(true);
        when(runtime.hasWorkspacePermission(11L, 99L, "member")).thenReturn(true);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        UserContextHolder.set(new UserContext(99L, "member", "Member", "user", 11L));

        String readBody = "{\"datasetId\":9,\"inputName\":\"orders\",\"columns\":[],\"filters\":[],\"limit\":10,\"offset\":0,\"parameters\":{}}";
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/v1/datasets"),
                get("/v1/datasets/9"),
                post("/v1/datasets").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"orders\"}"),
                put("/v1/datasets/9").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/v1/datasets/9"),
                get("/v1/datasets/9/descriptor"),
                post("/v1/datasets/preview").contentType(MediaType.APPLICATION_JSON).content(readBody),
                get("/v1/datasets/9/fields"),
                get("/v1/datasets/9/data"),
                put("/v1/datasets/9/rows").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/v1/datasets/9/rows").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/v1/datasets/9/rows").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/v1/datasets/fields/3/category").contentType(MediaType.APPLICATION_JSON).content("{\"fieldCategory\":\"DIMENSION\"}"),
                post("/v1/datasets/9/sync")
        );

        MockMvc mvc = mvc();
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isOk());
        }
    }

    private MockMvc mvc() {
        DataAgentDatasetController controller = new DataAgentDatasetController(datasetService, executionService, workspaceGuard, new vip.mate.dataagent.service.impl.QueryPlannerImpl(), new com.fasterxml.jackson.databind.ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new DataAgentWorkspaceInterceptor(runtime))
                .build();
    }
}
