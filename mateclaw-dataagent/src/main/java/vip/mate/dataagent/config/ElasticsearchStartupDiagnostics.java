package vip.mate.dataagent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;

import java.util.concurrent.CompletableFuture;

/** Emits a non-blocking startup report for Elasticsearch connectivity and the indexes used by DataAgent. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "uris")
class ElasticsearchStartupDiagnostics implements ApplicationRunner {

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ES-诊断] 开始探测；endpoints={}, authConfigured={}, connectTimeoutMs={}, readTimeoutMs={}, indexes=[{}, {}]",
                ElasticsearchLogSanitizer.sanitizeUris(properties.getUris()),
                properties.getUsername() != null && !properties.getUsername().isBlank()
                        && properties.getPassword() != null && !properties.getPassword().isBlank(),
                properties.getConnectTimeout(), properties.getReadTimeout(),
                DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX,
                DataAgentConstants.BUSINESS_TERM_ES_INDEX);

        // Do not delay application startup when ES is slow or unavailable.
        CompletableFuture.runAsync(this::probe);
    }

    void probe() {
        long startedAt = System.nanoTime();
        try {
            var info = client.info();
            long infoCostMs = elapsedMs(startedAt);
            log.info("[ES-诊断] connectivity=AVAILABLE, cluster={}, version={}, infoCostMs={}",
                    info.clusterName(), info.version().number(), infoCostMs);

            probeClusterHealth();
            probeIndex(DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX);
            probeIndex(DataAgentConstants.BUSINESS_TERM_ES_INDEX);
        } catch (Exception exception) {
            log.error("[ES-诊断] connectivity=UNAVAILABLE, endpoint={}, elapsedMs={}, cause={}: {}",
                    ElasticsearchLogSanitizer.sanitizeUris(properties.getUris()), elapsedMs(startedAt),
                    rootCause(exception).getClass().getSimpleName(),
                    ElasticsearchLogSanitizer.sanitizeMessage(rootCause(exception).getMessage()));
        }
    }

    private void probeClusterHealth() {
        long startedAt = System.nanoTime();
        try {
            var health = client.cluster().health();
            HealthStatus status = health.status();
            String healthStatus = String.valueOf(status);
            if ("red".equalsIgnoreCase(healthStatus)) {
                log.error("[ES-诊断] clusterHealth={}, nodes={}, activeShards={}, relocatingShards={}, unassignedShards={}, costMs={}",
                        status, health.numberOfNodes(), health.activeShards(), health.relocatingShards(),
                        health.unassignedShards(), elapsedMs(startedAt));
            } else if (!"green".equalsIgnoreCase(healthStatus)) {
                log.warn("[ES-诊断] clusterHealth={}, nodes={}, activeShards={}, relocatingShards={}, unassignedShards={}, costMs={}",
                        status, health.numberOfNodes(), health.activeShards(), health.relocatingShards(),
                        health.unassignedShards(), elapsedMs(startedAt));
            } else {
                log.info("[ES-诊断] clusterHealth={}, nodes={}, activeShards={}, relocatingShards={}, unassignedShards={}, costMs={}",
                        status, health.numberOfNodes(), health.activeShards(), health.relocatingShards(),
                        health.unassignedShards(), elapsedMs(startedAt));
            }
        } catch (Exception exception) {
            log.warn("[ES-诊断] clusterHealth=UNKNOWN（连接可用但集群健康接口失败，可能是权限或集群状态问题）, cause={}: {}",
                    rootCause(exception).getClass().getSimpleName(),
                    ElasticsearchLogSanitizer.sanitizeMessage(rootCause(exception).getMessage()));
        }
    }

    private void probeIndex(String indexName) {
        long startedAt = System.nanoTime();
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(request -> request.index(indexName))).value();
            log.info("[ES-诊断] index={}, exists={}, costMs={}", indexName, exists, elapsedMs(startedAt));
        } catch (Exception exception) {
            log.warn("[ES-诊断] index={}, status=UNKNOWN（索引探测失败，可能是权限或请求问题）, cause={}: {}",
                    indexName, rootCause(exception).getClass().getSimpleName(),
                    ElasticsearchLogSanitizer.sanitizeMessage(rootCause(exception).getMessage()));
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
