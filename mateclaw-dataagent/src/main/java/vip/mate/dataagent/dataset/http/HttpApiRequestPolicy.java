package vip.mate.dataagent.dataset.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.net.*;
import java.util.*;

/** HTTP 数据源的 fail-closed URL、参数和 Header 策略。 */
@Component
public final class HttpApiRequestPolicy {
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "cookie", "set-cookie", "proxy-authorization");
    @Value("${mateclaw.dataset.http.allow-insecure-test-endpoint:false}")
    private boolean allowInsecureTestEndpoint;
    @Value("${mateclaw.dataset.http.allow-tls-test-endpoint:false}")
    private boolean allowTlsTestEndpoint;

    public HttpApiRequestPolicy() {
        this(false, false);
    }

    public HttpApiRequestPolicy(boolean allowInsecureTestEndpoint) {
        this(allowInsecureTestEndpoint, false);
    }

    public HttpApiRequestPolicy(boolean allowInsecureTestEndpoint, boolean allowTlsTestEndpoint) {
        this.allowInsecureTestEndpoint = allowInsecureTestEndpoint;
        this.allowTlsTestEndpoint = allowTlsTestEndpoint;
    }

    public void validate(URI endpoint, List<String> allowedHosts) {
        if (endpoint == null || (!"https".equalsIgnoreCase(endpoint.getScheme()) && !isTestEndpoint(endpoint))) {
            throw new IllegalArgumentException("HTTP API endpoint must use https");
        }
        String host = endpoint.getHost();
        if (host == null || host.isBlank() || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("endpoint host is invalid");
        }
        if (allowTlsTestEndpoint && "e2e-http".equalsIgnoreCase(host)
                && "https".equalsIgnoreCase(endpoint.getScheme()) && endpoint.getPort() != 8443) {
            throw new IllegalArgumentException("TLS test endpoint must use port 8443");
        }
        List<String> hosts = allowedHosts == null ? List.of() : allowedHosts.stream().map(String::toLowerCase).toList();
        if (!hosts.contains(host.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("endpoint host is not allowlisted");
        try {
            if (isTestEndpoint(endpoint)) return;
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || isCloudMetadata(address)) {
                    throw new IllegalArgumentException("endpoint resolves to private or metadata address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("endpoint host cannot be resolved", e);
        }
    }

    private boolean isTestEndpoint(URI endpoint) {
        if (!"e2e-http".equalsIgnoreCase(endpoint.getHost())) return false;
        if ("http".equalsIgnoreCase(endpoint.getScheme())) return allowInsecureTestEndpoint;
        return "https".equalsIgnoreCase(endpoint.getScheme())
                && endpoint.getPort() == 8443 && allowTlsTestEndpoint;
    }

    public Map<String, Object> mapFilters(HttpApiDatasetDefinition definition, List<DatasetFilter> filters) {
        Set<String> allowed = new HashSet<>(definition.allowedQueryParams());
        allowed.addAll(definition.allowedBodyParams());
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (DatasetFilter filter : filters == null ? List.<DatasetFilter>of() : filters) {
            if (!allowed.contains(filter.field())) throw new IllegalArgumentException("undeclared API parameter: " + filter.field());
            if (!"eq".equalsIgnoreCase(filter.operator())) throw new IllegalArgumentException("HTTP API only supports equality parameter mapping");
            mapped.put(filter.field(), filter.value());
        }
        return Map.copyOf(mapped);
    }

    public void validateHeaders(Map<String, String> headers) {
        for (String name : headers == null ? Set.<String>of() : headers.keySet()) {
            if (SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("sensitive headers are not script-controlled");
        }
    }

    private boolean isCloudMetadata(InetAddress address) {
        return address.getHostAddress().equals("169.254.169.254") || address.getHostAddress().equals("100.100.100.200");
    }
}
