package vip.mate.dataagent.aloudata.local;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import vip.mate.dataagent.aloudata.AloudataApiClient.PreparedRequest;
import vip.mate.dataagent.aloudata.AloudataEndpointService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class LocalAloudataApiClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void embedModeReturnsFixtureInsteadOfCallingConfiguredAloudataHost() {
        LocalAloudataApiClient client = new LocalAloudataApiClient(
                mock(AloudataEndpointService.class), new LocalAloudataFixtures(), "");
        PreparedRequest request = new PreparedRequest(
                "category_list", HttpMethod.GET, "/anymetrics/api/v1/category/list",
                "https://aloudata.invalid:8083/anymetrics/api/v1/category/list",
                Map.of(), Map.of(), new HttpHeaders());

        Map<String, Object> body = client.send(request).getBody();

        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("success"));
    }
}
