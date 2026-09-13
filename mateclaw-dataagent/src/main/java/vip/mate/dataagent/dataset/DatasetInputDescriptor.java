package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

/** Python 可见的数据集输入描述；sourceConfig 仅供 DataAgent 内部使用。 */
public record DatasetInputDescriptor(
        long datasetId,
        String inputName,
        DatasetSourceType sourceType,
        List<DatasetColumn> schema,
        Long rowCount,
        @JsonIgnore Map<String, Object> sourceConfig,
        ObjectRef dataRef) {
    public DatasetInputDescriptor {
        if (datasetId <= 0 || inputName == null || inputName.isBlank() || sourceType == null) {
            throw new IllegalArgumentException("datasetId, inputName and sourceType are required");
        }
        schema = schema == null ? List.of() : List.copyOf(schema);
        sourceConfig = sourceConfig == null ? Map.of() : Map.copyOf(sourceConfig);
    }
}
