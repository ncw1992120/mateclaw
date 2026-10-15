package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.DashboardFilterContextDTO;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.InsightDashboardService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class InsightDataBindFilterScopeTest {

    @Test
    void dashboardTimeFilterUsesPartitionExpressionInFilters() {
        InsightDashboardService dashboardService = mock(InsightDashboardService.class);
        AloudataService aloudataService = mock(AloudataService.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setSchemaJson("""
                {"version":"1.0","pages":[{"id":"page-1","components":[
                  {"id":"table-1","type":"table","boundFilterIds":["filter-time"],"dataSource":{"datasourceId":"3","metrics":["revenue"],"dimensions":["metric_time"]}}
                ]}]}
                """);
        when(dashboardService.getDashboard(1L)).thenReturn(dashboard);
        InsightDataBindServiceImpl service = new InsightDataBindServiceImpl(
                dashboardService, aloudataService, null, null, null);
        DashboardFilterContextDTO filterContext = new DashboardFilterContextDTO();
        filterContext.setSourceFilterId("filter-time");
        DashboardFilterContextDTO.TimeRangeValue range = new DashboardFilterContextDTO.TimeRangeValue();
        range.setPreset("custom");
        range.setStart("2026-09-01");
        range.setEnd("2026-09-17");
        filterContext.setTimeRange(range);
        filterContext.setTimeGranularity("MONTH");

        service.previewData(1L, filterContext);

        ArgumentCaptor<AloudataMetricQueryRequest> request = ArgumentCaptor.forClass(AloudataMetricQueryRequest.class);
        org.mockito.Mockito.verify(aloudataService).queryMetrics(org.mockito.ArgumentMatchers.eq(3L), request.capture());
        assertEquals(List.of("(DateTrunc(['metric_time'], \"MONTH\") >= (DateTrunc(Cast(\"2026-09-01 00:00:00\", \"TIMESTAMP\"), \"MONTH\")))"
                        + " AND (DateTrunc(['metric_time'], \"MONTH\") < (DateTrunc(Cast(\"2026-09-18 00:00:00\", \"TIMESTAMP\"), \"MONTH\")))"),
                request.getValue().getFilters());
        assertNull(request.getValue().getTimeConstraint());
    }

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
