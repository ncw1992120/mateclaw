package vip.mate.dataagent.dto.result;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Python Runner 与 DataAgent 之间的版本化结果信封。
 * <p>
 * 组件只消费 table、scalar 或 message 三种结果，不直接消费 Python 原始对象。
 */
public record ScriptResultEnvelope(
        String schemaVersion,
        String kind,
        Object data,
        Map<String, Object> meta) {

    private static final Set<String> SUPPORTED_KINDS = Set.of("table", "scalar", "message");

    public ScriptResultEnvelope {
        if (!"1.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported result schemaVersion");
        }
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw new IllegalArgumentException("unsupported result kind");
        }
        Objects.requireNonNull(data, "result data must not be null");
        Objects.requireNonNull(meta, "result meta must not be null");
    }
}
