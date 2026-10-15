package vip.mate.dataagent.dataset;

import java.util.Set;

/** 数据集 Adapter 执行时的最小授权上下文。 */
public record DatasetAccessContext(
        Long workspaceId,
        Long userId,
        String taskId,
        Set<Long> allowedDatasetIds) {
    public DatasetAccessContext {
        if (workspaceId == null || userId == null || taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("workspaceId, userId and taskId are required");
        }
        allowedDatasetIds = allowedDatasetIds == null ? Set.of() : Set.copyOf(allowedDatasetIds);
    }

    public boolean canRead(long datasetId) {
        return allowedDatasetIds.contains(datasetId);
    }
}
