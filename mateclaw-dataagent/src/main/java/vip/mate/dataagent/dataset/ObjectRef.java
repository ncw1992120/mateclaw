package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 受控对象存储引用；不包含连接凭据。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObjectRef(
        String objectId,
        Long workspaceId,
        String taskId,
        String format,
        String digest,
        Long expiresAt) {
    public ObjectRef {
        if (objectId == null || objectId.isBlank() || workspaceId == null || taskId == null || taskId.isBlank()
                || format == null || format.isBlank() || digest == null || digest.isBlank()
                || expiresAt == null) {
            throw new IllegalArgumentException("incomplete object reference");
        }
    }
}
