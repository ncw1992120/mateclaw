package vip.mate.dataagent.aloudata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.system.service.SystemSettingService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AloudataEndpointServiceTest {

    @Test
    void malformedDatabaseConfigurationFallsBackToCoreDatasetEndpoints() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("{malformed");

        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));

        AloudataApiProperties.ApiEndpoint detail = service.getEndpoint("analysis_view_query_by_name");
        AloudataApiProperties.ApiEndpoint metrics = service.getEndpoint("metrics_query");

        assertNotNull(detail);
        assertEquals("/anymetrics/api/v1/analysisview/queryByName", detail.getPath());
        assertNotNull(metrics);
        assertEquals("/semantic/api/v1.1/metrics/query", metrics.getPath());
        assertTrue(metrics.getRequestParams().stream().anyMatch(p -> "filters".equals(p.getName())));
    }
}
