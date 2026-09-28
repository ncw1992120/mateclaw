package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.DashboardFilterContextDTO;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.InsightDashboardService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightDataBindFilterScopeTest {

    @Test
    void scopedFilterQueriesOnlyComponentsBoundToThatFilter() {
        InsightDashboardService dashboardService = mock(InsightDashboardService.class);
        AloudataService aloudataService = mock(AloudataService.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setSchemaJson("""
                {"version":"1.0","pages":[{"id":"page-1","components":[
                  {"id":"bound","type":"table","boundFilterIds":["filter-region"],
                   "dataSource":{"datasourceId":"11","metrics":["sales"],"dimensions":[]}},
                  {"id":"unbound","type":"table","boundFilterIds":[],
                   "dataSource":{"datasourceId":"22","metrics":["sales"],"dimensions":[]}}
                ]}]}
                """);
        when(dashboardService.getDashboard(1L)).thenReturn(dashboard);
        when(aloudataService.queryMetrics(any(), any())).thenReturn(null);
        InsightDataBindServiceImpl service = new InsightDataBindServiceImpl(
                dashboardService, aloudataService, null, null, null);
        DashboardFilterContextDTO filterContext = new DashboardFilterContextDTO();
        filterContext.setSourceFilterId("filter-region");

        List<?> result = service.previewData(1L, filterContext);

        assertEquals(1, result.size());
        verify(aloudataService, times(1)).queryMetrics(any(), any());
        verify(aloudataService).queryMetrics(org.mockito.ArgumentMatchers.eq(11L), any());
    }
}
