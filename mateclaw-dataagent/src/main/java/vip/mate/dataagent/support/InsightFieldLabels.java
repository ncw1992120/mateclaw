package vip.mate.dataagent.support;

import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves persisted dataset field mappings for user-facing dashboard text. */
public final class InsightFieldLabels {

    private InsightFieldLabels() {
    }

    public static Map<String, String> fromSchema(InsightDashboardSchemaDTO schema) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (schema == null) return labels;
        collectInputs(schema.getDatasetInputs(), labels);
        for (InsightDashboardSchemaDTO.Component component : schema.getAllComponents()) {
            Map<String, Object> config = component.getConfig();
            if (config == null || !(config.get("datasetPipeline") instanceof Map<?, ?> pipeline)) continue;
            Object inputs = pipeline.get("datasetInputs");
            if (inputs instanceof List<?> list) collectInputs(list, labels);
        }
        return labels;
    }

    public static Map<String, String> fromComponent(InsightDashboardSchemaDTO schema, String componentId) {
        if (schema != null && componentId != null) {
            for (InsightDashboardSchemaDTO.Component component : schema.getAllComponents()) {
                if (!componentId.equals(component.getId()) || component.getConfig() == null) continue;
                Object rawPipeline = component.getConfig().get("datasetPipeline");
                if (rawPipeline instanceof Map<?, ?> pipeline && pipeline.get("datasetInputs") instanceof List<?> inputs) {
                    Map<String, String> labels = new LinkedHashMap<>();
                    collectInputs(inputs, labels);
                    if (!labels.isEmpty()) return labels;
                }
            }
        }
        return fromSchema(schema);
    }

    private static void collectInputs(List<?> inputs, Map<String, String> labels) {
        if (inputs == null) return;
        for (Object rawInput : inputs) {
            if (!(rawInput instanceof Map<?, ?> input)) continue;
            Object rawMappings = input.get("fieldMappings");
            if (!(rawMappings instanceof List<?> mappings)) continue;
            for (Object rawMapping : mappings) {
                if (!(rawMapping instanceof Map<?, ?> mapping)) continue;
                Object rawSource = mapping.get("source");
                if (!(rawSource instanceof String source) || source.isBlank()) continue;
                Object rawTarget = mapping.get("target");
                String target = rawTarget instanceof String value ? value.trim() : "";
                String resolved = target.isEmpty() || target.equals(source) ? source : target;
                String existing = labels.putIfAbsent(source, resolved);
                if (existing != null && !existing.equals(resolved)) labels.put(source, source);
            }
        }
    }
}
