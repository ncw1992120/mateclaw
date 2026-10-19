package vip.mate.dataagent.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/** Logs ES connectivity transitions once instead of flooding production logs on every fallback request. */
@Slf4j
public final class ElasticsearchAvailabilityLog {

    private static final AtomicBoolean UNAVAILABLE = new AtomicBoolean();

    private ElasticsearchAvailabilityLog() {
    }

    public static void unavailable(String detectedBy, Throwable exception) {
        if (!UNAVAILABLE.compareAndSet(false, true)) {
            return;
        }
        Throwable cause = rootCause(exception);
        log.error("[ES-诊断] connectivity=UNAVAILABLE, detectedBy={}, cause={}: {}",
                detectedBy, cause.getClass().getSimpleName(),
                ElasticsearchLogSanitizer.sanitizeMessage(cause.getMessage()));
    }

    public static void available(String detectedBy) {
        if (UNAVAILABLE.compareAndSet(true, false)) {
            log.info("[ES-诊断] connectivity=AVAILABLE（连接恢复）, detectedBy={}", detectedBy);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
