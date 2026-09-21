package vip.mate.dataagent.aloudata;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import vip.mate.dataagent.dto.AloudataConfigDTO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AloudataApiClientTest {

    @Test
    void callPreservesNonMapRequestBody() {
        AloudataEndpointService endpoints = mock(AloudataEndpointService.class);
        when(endpoints.getEndpoint("batch")).thenReturn(new AloudataApiProperties.ApiEndpoint(
                "batch", "/batch", "POST", "test", List.of(), List.of()));
        RestTemplate restTemplate = new RestTemplate();
        AloudataApiClient client = new AloudataApiClient(endpoints);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://semantic.example:8085/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0]").value("metric-a"))
                .andRespond(withSuccess("{\"code\":\"200\",\"success\":true}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        AloudataConfigDTO config = new AloudataConfigDTO();
        config.setSemanticHost("https://semantic.example");
        config.setTenantId("tn-test");
        config.setAuthType("UID");
        config.setAuthValue("uid-test");

        client.call("batch", config, Map.of(), List.of("metric-a"));
        server.verify();
    }

    @Test
    void callWithParamsInjectsConnectionAuthenticationBeforeValidation() {
        AloudataEndpointService endpoints = mock(AloudataEndpointService.class);
        AloudataApiProperties.ApiEndpoint endpoint = new AloudataApiProperties.ApiEndpoint(
                "semantic", "/semantic/api/v1.1/metrics/query", "POST", "test",
                List.of(
                        new ApiParam("tenant-id", "String", true, null, "tenant", "HEADER"),
                        new ApiParam("auth-type", "String", true, null, "auth type", "HEADER"),
                        new ApiParam("auth-value", "String", true, null, "auth value", "HEADER"),
                        new ApiParam("metrics", "Array", true, null, "metrics", "BODY")),
                List.of());
        when(endpoints.getEndpoint("metrics_query")).thenReturn(endpoint);
        RestTemplate restTemplate = new RestTemplate();
        AloudataApiClient client = new AloudataApiClient(endpoints);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://semantic.example:8085/semantic/api/v1.1/metrics/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("tenant-id", "tn-test"))
                .andExpect(header("auth-type", "UID"))
                .andExpect(header("auth-value", "uid-test"))
                .andExpect(jsonPath("$.metrics[0]").value("revenue"))
                .andRespond(withSuccess("{\"code\":\"200\",\"success\":true}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        AloudataConfigDTO config = new AloudataConfigDTO();
        config.setSemanticHost("https://semantic.example");
        config.setTenantId("tn-test");
        config.setAuthType("UID");
        config.setAuthValue("uid-test");

        assertNotNull(client.callWithParams("metrics_query", config,
                Map.of("metrics", List.of("revenue"))));
        server.verify();
    }
}
