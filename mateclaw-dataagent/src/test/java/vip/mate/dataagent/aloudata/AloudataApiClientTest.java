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
    void requestLogShowsMethodUrlAndParamsWithoutHeaders() {
        AloudataApiClient.PreparedRequest request = new AloudataApiClient.PreparedRequest(
                "metrics_query", HttpMethod.POST, "/semantic/api/v1.1/metrics/query",
                "https://semantic.example:8085/semantic/api/v1.1/metrics/query?period=2026-09",
                Map.of("period", "2026-09"), Map.of("metrics", List.of("revenue"), "limit", 100,
                        "auth-value", "body-secret"),
                new org.springframework.http.HttpHeaders());
        request.headers().set("tenant-id", "tn-test");
        request.headers().set("auth-type", "UID");
        request.headers().set("auth-value", "secret-value");

        String log = AloudataApiClient.formatRequestLog(request);

        org.junit.jupiter.api.Assertions.assertTrue(log.contains("POST"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains(
                "url=https://semantic.example:8085/semantic/api/v1.1/metrics/query?period=2026-09"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("period=2026-09"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("metrics"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("revenue"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("limit=100"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("***"));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("headers="));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("tenant-id"));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("auth-type"));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("secret-value"));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("body-secret"));
    }

    @Test
    void requestLogShowsGetQueryInFullUrlWithoutHeaders() {
        AloudataApiClient.PreparedRequest request = new AloudataApiClient.PreparedRequest(
                "dimension_detail", HttpMethod.GET, "/dimension/detail",
                "https://anymetrics.example:8083/dimension/detail?dimName=region&includeInactive=false",
                Map.of("dimName", "region", "includeInactive", false), null,
                new org.springframework.http.HttpHeaders());

        String log = AloudataApiClient.formatRequestLog(request);

        org.junit.jupiter.api.Assertions.assertTrue(log.contains("method=GET"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains(
                "url=https://anymetrics.example:8083/dimension/detail?dimName=region&includeInactive=false"));
        org.junit.jupiter.api.Assertions.assertTrue(log.contains("dimName=region"));
        org.junit.jupiter.api.Assertions.assertFalse(log.contains("headers="));
    }

    @Test
    void callPreservesNonMapRequestBody() {
        AloudataEndpointService endpoints = mock(AloudataEndpointService.class);
        when(endpoints.getEndpoint("batch")).thenReturn(new AloudataApiProperties.ApiEndpoint(
                "semantic", "/batch", "POST", "batch", List.of(), List.of()));
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
