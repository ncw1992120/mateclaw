package vip.mate.dataagent.service.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Emitted synchronously after a local Python Worker reaches a terminal state. */
public record PythonWorkerCompletedEvent(Map<String, Object> taskSnapshot) {
    public PythonWorkerCompletedEvent {
        Objects.requireNonNull(taskSnapshot, "taskSnapshot");
        taskSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(taskSnapshot));
    }
}
