package vip.mate.sdk.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MateClaw 嵌入式运行时配置属性
 * <p>
 * 通过 {@code mateclaw.runtime.*} 前缀配置嵌入式运行时的行为，
 * 例如是否启用 Flyway 数据库迁移等。
 */
@ConfigurationProperties(prefix = "mateclaw.runtime")
public class MateClawRuntimeProperties {

    /**
     * 嵌入式启动时是否启用 Flyway 数据库迁移
     */
    private boolean flywayEnabled = true;

    public boolean isFlywayEnabled() {
        return flywayEnabled;
    }

    public void setFlywayEnabled(boolean flywayEnabled) {
        this.flywayEnabled = flywayEnabled;
    }
}
