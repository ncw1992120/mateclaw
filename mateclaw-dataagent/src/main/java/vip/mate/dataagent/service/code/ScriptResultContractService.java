package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dto.result.ScriptResultEnvelope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runner 结果的防御性校验与预览裁剪。
 *
 * 校验失败抛 {@link ScriptResultContractException}，错误对象固定包含
 * stage/path/expected/actual/suggestion，供前端展示精确诊断。
 * 内联结果与 ObjectRef 引用结果经过同一校验器，产出同一 envelope。
 */
@Service
public class ScriptResultContractService {

    private static final Set<String> KINDS = Set.of("table", "scalar", "message");
    private static final Set<String> DATA_TYPES = Set.of("string", "number", "boolean", "date", "datetime");
    private static final Set<String> MESSAGE_LEVELS = Set.of("info", "warning");

    /** 校验通过的信封：kind() + meta().rowCount() 直接可用，table 结果经 data() 得到强类型视图。 */
    public record ValidatedEnvelope(String schemaVersion, String kind, Object payload, Meta meta) {
        public record Meta(int rowCount, boolean truncated, List<String> sourceInputs) {}

        public record Column(String name, String title, String dataType, boolean nullable) {}

        public record TableData(List<Column> columns, List<Map<String, Object>> rows) {}

        /** table 结果的类型化视图；scalar/message 调用会抛 IllegalStateException。 */
        public TableData data() {
            if (payload instanceof TableData tableData) return tableData;
            throw new IllegalStateException("envelope data is not a table: " + kind);
        }

        public ScriptResultEnvelope toEnvelope() {
            return new ScriptResultEnvelope(schemaVersion, kind, payload, Map.of(
                    "rowCount", meta.rowCount(),
                    "truncated", meta.truncated(),
                    "sourceInputs", meta.sourceInputs()));
        }
    }

    public ValidatedEnvelope validate(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ScriptResultContractException("result", "object", typeName(raw), "结果必须是对象");
        }
        String schemaVersion = string(map.get("schemaVersion"));
        if (!"1.0".equals(schemaVersion)) {
            throw new ScriptResultContractException("result.schemaVersion", "\"1.0\"", stringOrRaw(map.get("schemaVersion")),
                    "结果 schemaVersion 必须是 1.0");
        }
        String kind = string(map.get("kind"));
        if (kind == null || !KINDS.contains(kind)) {
            throw new ScriptResultContractException("result.kind", "table|scalar|message",
                    kind == null ? "missing" : kind, "结果类型只允许 table、scalar 或 message");
        }
        return switch (kind) {
            case "table" -> validateTable(map);
            case "scalar" -> validateScalar(map);
            default -> validateMessage(map);
        };
    }

    /** 预览裁剪：只截断行，meta 保留真实 rowCount 并标记 truncated。 */
    public ValidatedEnvelope preview(ValidatedEnvelope envelope, int maxRows) {
        if (!"table".equals(envelope.kind())) return envelope;
        ValidatedEnvelope.TableData table = envelope.data();
        if (table.rows().size() <= maxRows) return envelope;
        List<Map<String, Object>> limited = new ArrayList<>(table.rows().subList(0, maxRows));
        ValidatedEnvelope.Meta meta = envelope.meta();
        ValidatedEnvelope.Meta limitedMeta = new ValidatedEnvelope.Meta(meta.rowCount(), true, meta.sourceInputs());
        return new ValidatedEnvelope(envelope.schemaVersion(), envelope.kind(),
                new ValidatedEnvelope.TableData(table.columns(), limited), limitedMeta);
    }

    /** 从 ObjectRef 元数据 + 读回行重建与内联结果一致的 table envelope。 */
    public ValidatedEnvelope rebuildTable(Map<String, Object> metadata, List<Map<String, Object>> rows) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("schemaVersion", metadata.getOrDefault("schemaVersion", "1.0"));
        raw.put("kind", "table");
        raw.put("data", Map.of("columns", metadata.getOrDefault("columns", List.of()), "rows", rows));
        raw.put("meta", Map.of("rowCount", metadata.getOrDefault("rowCount", rows.size()),
                "truncated", false, "sourceInputs", List.of()));
        ValidatedEnvelope validated = validate(raw);
        if (!"table".equals(validated.kind())) {
            throw new ScriptResultContractException("result.kind", "table", validated.kind(), "ObjectRef 引用只支持 table 结果");
        }
        return validated;
    }

    private ValidatedEnvelope validateTable(Map<?, ?> map) {
        Object dataRaw = map.get("data");
        if (!(dataRaw instanceof Map<?, ?> data)) {
            throw new ScriptResultContractException("result.data", "object", typeName(dataRaw), "table 结果的 data 必须是对象");
        }
        List<ValidatedEnvelope.Column> columns = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        Object columnsRaw = data.get("columns");
        if (!(columnsRaw instanceof List<?> columnList)) {
            throw new ScriptResultContractException("result.data.columns", "array", typeName(columnsRaw), "table 结果必须声明列");
        }
        List<String> nameList = new ArrayList<>();
        for (Object columnRaw : columnList) {
            if (!(columnRaw instanceof Map<?, ?> column)) {
                throw new ScriptResultContractException("result.data.columns", "object", typeName(columnRaw), "列必须是对象");
            }
            String name = string(column.get("name"));
            if (name == null || name.isBlank()) {
                throw new ScriptResultContractException("result.data.columns.name", "string", "missing", "列名不能为空");
            }
            if (!names.add(name)) {
                throw new ScriptResultContractException("result.data.columns", "唯一列名", "重复列 " + name, "列名必须唯一");
            }
            nameList.add(name);
            String dataType = string(column.get("dataType"));
            if (dataType == null || !DATA_TYPES.contains(dataType)) {
                throw new ScriptResultContractException("result.data.columns.dataType",
                        "string|number|boolean|date|datetime", stringOrRaw(column.get("dataType")),
                        "列 " + name + " 的 dataType 不受支持");
            }
            Object title = column.get("title");
            columns.add(new ValidatedEnvelope.Column(name,
                    title instanceof String titleText && !titleText.isBlank() ? titleText : name,
                    dataType, Boolean.TRUE.equals(column.get("nullable")) || column.get("nullable") == null));
        }

        Object rowsRaw = data.get("rows");
        if (!(rowsRaw instanceof List<?> rowList)) {
            throw new ScriptResultContractException("result.data.rows", "array", typeName(rowsRaw), "table 结果必须带行数据");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < rowList.size(); index++) {
            Object rowRaw = rowList.get(index);
            if (!(rowRaw instanceof Map<?, ?> row)) {
                throw new ScriptResultContractException("result.data.rows[" + index + "]", "object", typeName(rowRaw),
                        "每一行都必须是对象");
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (String name : nameList) {
                if (!row.containsKey(name)) {
                    throw new ScriptResultContractException("result.data.rows[" + index + "]." + name,
                            columnDataType(columns, name), "missing", "行缺少列 " + name + " 的值");
                }
                Object value = row.get(name);
                String expected = columnDataType(columns, name);
                if (value != null && !typeMatches(expected, value)) {
                    throw new ScriptResultContractException("result.data.rows[" + index + "]." + name,
                            expected, typeName(value), "统一列 " + name + " 的类型");
                }
                normalized.put(name, value);
            }
            rows.add(normalized);
        }

        ValidatedEnvelope.Meta meta = validateMeta(map, rowList.size());
        return new ValidatedEnvelope("1.0", "table",
                new ValidatedEnvelope.TableData(List.copyOf(columns), rows), meta);
    }

    private ValidatedEnvelope validateScalar(Map<?, ?> map) {
        Object dataRaw = map.get("data");
        if (!(dataRaw instanceof Map<?, ?> data)) {
            throw new ScriptResultContractException("result.data", "object", typeName(dataRaw), "scalar 结果的 data 必须是对象");
        }
        String dataType = string(data.get("dataType"));
        if (dataType == null || !DATA_TYPES.contains(dataType)) {
            throw new ScriptResultContractException("result.data.dataType",
                    "string|number|boolean|date|datetime", stringOrRaw(data.get("dataType")), "scalar dataType 不受支持");
        }
        Object value = data.get("value");
        if (value != null && !typeMatches(dataType, value)) {
            throw new ScriptResultContractException("result.data.value", dataType, typeName(value),
                    "scalar 值类型与 dataType 不一致");
        }
        return new ValidatedEnvelope("1.0", "scalar", Map.of("value", value, "dataType", dataType), validateMeta(map, 0));
    }

    private ValidatedEnvelope validateMessage(Map<?, ?> map) {
        Object dataRaw = map.get("data");
        if (!(dataRaw instanceof Map<?, ?> data)) {
            throw new ScriptResultContractException("result.data", "object", typeName(dataRaw), "message 结果的 data 必须是对象");
        }
        String level = string(data.get("level"));
        if (level == null || !MESSAGE_LEVELS.contains(level)) {
            throw new ScriptResultContractException("result.data.level", "info|warning", stringOrRaw(data.get("level")),
                    "消息级别只允许 info 或 warning");
        }
        String message = string(data.get("message"));
        if (message == null || message.isBlank()) {
            throw new ScriptResultContractException("result.data.message", "string", "missing", "消息内容不能为空");
        }
        return new ValidatedEnvelope("1.0", "message", Map.of("level", level, "message", message), validateMeta(map, 0));
    }

    private ValidatedEnvelope.Meta validateMeta(Map<?, ?> map, int rowCount) {
        Object metaRaw = map.get("meta");
        int declared = rowCount;
        boolean truncated = false;
        List<String> sourceInputs = List.of();
        if (metaRaw instanceof Map<?, ?> meta) {
            if (meta.get("rowCount") instanceof Number number) declared = number.intValue();
            truncated = Boolean.TRUE.equals(meta.get("truncated"));
            if (meta.get("sourceInputs") instanceof List<?> inputs) {
                sourceInputs = inputs.stream().map(String::valueOf).toList();
            }
        }
        return new ValidatedEnvelope.Meta(declared, truncated, sourceInputs);
    }

    private boolean typeMatches(String dataType, Object value) {
        return switch (dataType) {
            case "number" -> value instanceof Number && !(value instanceof Boolean);
            case "boolean" -> value instanceof Boolean;
            case "string", "date", "datetime" -> value instanceof String;
            default -> false;
        };
    }

    private String columnDataType(List<ValidatedEnvelope.Column> columns, String name) {
        return columns.stream().filter(column -> column.name().equals(name)).findFirst()
                .map(ValidatedEnvelope.Column::dataType).orElse("string");
    }

    private String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private String stringOrRaw(Object value) {
        return value == null ? "missing" : value.toString();
    }

    private String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
