package vip.mate.dataagent.config;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Prevents credentials and query parameters in Elasticsearch endpoints from entering logs. */
public final class ElasticsearchLogSanitizer {

    private ElasticsearchLogSanitizer() {
    }

    static String sanitizeUris(String uris) {
        if (uris == null || uris.isBlank()) {
            return "<empty>";
        }
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .map(ElasticsearchLogSanitizer::sanitizeUri)
                .collect(Collectors.joining(","));
    }

    public static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "<no-detail>";
        }
        return message
                .replaceAll("(?i)(https?://)[^/@\\s]+:[^/@\\s]+@", "$1<redacted>@")
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)(authorization\\s*[:=]\\s*basic\\s+)[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)(password|passwd|token|api[_-]?key)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2<redacted>");
    }

    private static String sanitizeUri(String rawUri) {
        try {
            URI uri = URI.create(rawUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "<invalid-endpoint>";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (IllegalArgumentException | java.net.URISyntaxException ignored) {
            return "<invalid-endpoint>";
        }
    }
}
