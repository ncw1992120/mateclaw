package vip.mate.llm.failover;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RFC-009 P3.3: thresholds for the per-provider health tracker.
 *
 * <pre>
 * mateclaw:
 *   llm:
 *     failover:
 *       health:
 *         enabled: true
 *         failure-threshold: 3      # consecutive failures before cooldown
 *         cooldown-ms: 300000       # 5 minutes (auth/billing/server-error)
 *         rate-limit-cooldown-ms: 30000  # 30 seconds (rate limit)
 * </pre>
 */
@ConfigurationProperties(prefix = "mateclaw.llm.failover.health")
public class ProviderHealthProperties {

    /** Master switch. {@code false} disables tracking entirely; the chain walker tries every provider every time. */
    private boolean enabled = true;

    /** Consecutive failures (any kind) at which a provider enters cooldown. */
    private int failureThreshold = 3;

    /** Cooldown window in milliseconds for auth/billing/server-error failures. */
    private long cooldownMs = 300_000L;

    /** Cooldown window in milliseconds for rate-limit (429) failures.
     *  Rate limits are typically short-lived (seconds to minutes), so a shorter
     *  cooldown allows the provider to be retried sooner after the limit resets. */
    private long rateLimitCooldownMs = 30_000L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = Math.max(1000, cooldownMs);
    }

    public long getRateLimitCooldownMs() { return rateLimitCooldownMs; }
    public void setRateLimitCooldownMs(long rateLimitCooldownMs) {
        this.rateLimitCooldownMs = Math.max(1000, rateLimitCooldownMs);
    }
}
