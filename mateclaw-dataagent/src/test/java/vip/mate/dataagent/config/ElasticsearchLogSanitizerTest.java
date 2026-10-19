package vip.mate.dataagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ElasticsearchLogSanitizerTest {

    @Test
    void removesCredentialsAndQueryParametersFromConfiguredEndpoints() {
        String sanitized = ElasticsearchLogSanitizer.sanitizeUris(
                "https://elastic:secret@es.internal:9243/search?token=private,http://es-backup:9200");

        assertEquals("https://es.internal:9243/search,http://es-backup:9200", sanitized);
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("private"));
    }

    @Test
    void reportsMalformedEndpointWithoutEchoingItsValue() {
        String sanitized = ElasticsearchLogSanitizer.sanitizeUris("https://user:secret@bad host:9200");

        assertEquals("<invalid-endpoint>", sanitized);
        assertFalse(sanitized.contains("secret"));
    }

    @Test
    void redactsCredentialsFromExceptionMessages() {
        String sanitized = ElasticsearchLogSanitizer.sanitizeMessage(
                "Connect to https://elastic:secret@es.internal:9200 failed; token=private");

        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("private"));
    }

    @Test
    void preservesUsefulDiagnosticDetailsWhileRedactingSensitiveValues() {
        assertEquals("ConnectException: connection refused; password=<redacted>",
                ElasticsearchLogSanitizer.sanitizeMessage(
                        "ConnectException: connection refused; password=secret"));
    }
}
