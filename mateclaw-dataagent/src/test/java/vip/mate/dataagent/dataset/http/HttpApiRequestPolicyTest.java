package vip.mate.dataagent.dataset.http;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiRequestPolicyTest {
    private final HttpApiRequestPolicy policy = new HttpApiRequestPolicy();

    @Test
    void testEndpointOverrideIsDisabledByDefault() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> policy.validate(URI.create("http://e2e-http:8080/orders"), List.of("e2e-http")));
        assertEquals("HTTP API endpoint must use https", error.getMessage());
    }

    @Test
    void acceptsOnlyTheDedicatedE2eHostWhenExplicitlyEnabled() {
        HttpApiRequestPolicy e2ePolicy = new HttpApiRequestPolicy(true);
        assertDoesNotThrow(() -> e2ePolicy.validate(
                URI.create("http://e2e-http:8080/orders"), List.of("e2e-http")));
        assertThrows(IllegalArgumentException.class, () -> e2ePolicy.validate(
                URI.create("http://127.0.0.1:8080/orders"), List.of("127.0.0.1")));
    }

    @Test
    void acceptsOnlyTheDedicatedHttpsFixtureWhenTlsTestModeIsEnabled() {
        HttpApiRequestPolicy e2ePolicy = new HttpApiRequestPolicy(false, true);
        assertDoesNotThrow(() -> e2ePolicy.validate(
                URI.create("https://e2e-http:8443/orders"), List.of("e2e-http")));
        assertThrows(IllegalArgumentException.class, () -> e2ePolicy.validate(
                URI.create("http://e2e-http:8080/orders"), List.of("e2e-http")));
        assertThrows(IllegalArgumentException.class, () -> e2ePolicy.validate(
                URI.create("https://e2e-http:443/orders"), List.of("e2e-http")));
        assertThrows(IllegalArgumentException.class, () -> e2ePolicy.validate(
                URI.create("https://127.0.0.1:8443/orders"), List.of("127.0.0.1")));
    }

    @Test
    void allowsAllowlistedPublicHostAndMapsOnlyDeclaredParameters() {
        policy.validate(URI.create("https://example.com/v1/orders"), List.of("example.com"));
        HttpApiDatasetDefinition definition = new HttpApiDatasetDefinition(
                URI.create("https://example.com/v1/orders"), "GET", List.of("status"), List.of("tenant"),
                "$.data", "page", "page", "size", true, Map.of());
        assertEquals(Map.of("status", "PAID"), policy.mapFilters(definition,
                List.of(new DatasetFilter("status", "dimension", "eq", "PAID"))));
        assertThrows(IllegalArgumentException.class, () -> policy.mapFilters(definition,
                List.of(new DatasetFilter("password", "dimension", "eq", "x"))));
    }

    @Test
    void rejectsPrivateAddressHttpAndSensitiveHeaders() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(
                URI.create("http://127.0.0.1:8080/orders"), List.of("127.0.0.1")));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(
                URI.create("https://127.0.0.1/orders"), List.of("127.0.0.1")));
        assertThrows(IllegalArgumentException.class, () -> policy.validateHeaders(Map.of("Authorization", "secret")));
    }

    @Test
    void rejectsNonAllowlistedHostAndUrlCredentials() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(
                URI.create("https://api.example.com/orders"), List.of("example.com")));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(
                URI.create("https://user:pass@example.com/orders"), List.of("example.com")));
    }
}
