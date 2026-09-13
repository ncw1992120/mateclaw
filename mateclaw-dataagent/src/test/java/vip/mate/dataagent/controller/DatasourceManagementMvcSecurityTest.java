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
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.interceptor.DataAgentWorkspaceInterceptor;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.service.AloudataAnalysisViewService;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasourceManagementMvcSecurityTest {

    @Mock DatasourceManageService datasourceService;
    @Mock AloudataEndpointService endpointService;
    @Mock AloudataSemanticSyncService syncService;
    @Mock AloudataService aloudataService;
    @Mock AloudataAnalysisViewService analysisViewService;
    @Mock WorkspaceGuard workspaceGuard;
    @Mock MateClawRuntime runtime;

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void everyDatasourceManagementRouteReachesItsHandlerAfterWorkspaceCheck() throws Exception {
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        UserContextHolder.set(new UserContext(99L, "admin", "Admin", "admin", 11L));

        String emptyJson = "{}";
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/v1/datasources"),
                get("/v1/datasources/9"),
                post("/v1/datasources").contentType(MediaType.APPLICATION_JSON).content(emptyJson),
                put("/v1/datasources/9").contentType(MediaType.APPLICATION_JSON).content(emptyJson),
                delete("/v1/datasources/9"),
                post("/v1/datasources/9/test"),
                post("/v1/datasources/test").contentType(MediaType.APPLICATION_JSON).content(emptyJson),
                put("/v1/datasources/9/toggle").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"),
                post("/v1/datasources/9/schema-discovery"),
                get("/v1/datasources/9/tables"),
                get("/v1/datasources/9/tables/3"),
                get("/v1/datasources/9/tables/3/columns"),
                post("/v1/datasources/9/tables/3/sync").contentType(MediaType.APPLICATION_JSON).content(emptyJson),
                get("/v1/datasources/9/tables/3/preview"),
                delete("/v1/datasources/9/tables/3"),
                post("/v1/datasources/9/aloudata/sync"),
                post("/v1/datasources/9/aloudata/rebuild-es"),
                get("/v1/datasources/9/aloudata/sync-status"),
                get("/v1/datasources/9/aloudata/synced-metrics"),
                get("/v1/datasources/9/aloudata/synced-dimensions"),
                get("/v1/datasources/9/aloudata/metrics/m1/dimensions"),
                get("/v1/datasources/9/aloudata/metrics/m1/dimension-details"),
                get("/v1/datasources/9/aloudata/metrics-dimension-details").param("metricNames", "m1"),
                get("/v1/datasources/9/aloudata/dimensions/d1/metric-details"),
                get("/v1/datasources/9/aloudata/dimensions/d1/values"),
                get("/v1/datasources/9/aloudata/synced-categories"),
                get("/v1/datasources/9/aloudata/metrics/page"),
                get("/v1/datasources/9/aloudata/dimensions/page"),
                get("/v1/datasources/9/aloudata/metrics/grouped"),
                get("/v1/datasources/9/aloudata/dimensions/grouped"),
                get("/v1/datasources/9/aloudata/categories/counts").param("categoryType", "CATEGORY_METRIC"),
                get("/v1/datasources/aloudata/api-specs"),
                post("/v1/datasources/9/aloudata/metrics/query")
                        .contentType(MediaType.APPLICATION_JSON).content(emptyJson),
                get("/v1/datasources/9/analysis-views"),
                get("/v1/datasources/9/analysis-views/sales")
        );

        MockMvc mvc = mvc();
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isOk());
        }
    }

    private MockMvc mvc() {
        DataAgentDatasourceController controller = new DataAgentDatasourceController(
                datasourceService, endpointService, syncService, aloudataService,
                analysisViewService, workspaceGuard);
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new DataAgentWorkspaceInterceptor(runtime))
                .build();
    }
}
