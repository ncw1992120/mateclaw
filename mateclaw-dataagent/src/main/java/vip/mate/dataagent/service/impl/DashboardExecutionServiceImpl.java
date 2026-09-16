package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dto.DashboardExecutionRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.model.DashboardExecutionEntity;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.ObjectRef;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.DashboardExecutionService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.code.ScriptTaskPreparationService;
import vip.mate.dataagent.service.code.PythonExecutionService;

import java.util.*;

/**
 * 仪表盘 Python 执行编排。
 * <p>
 * execution 元数据和最近一次 Runner 状态持久化到 DataAgent，Runner 重启或 DataAgent 重启后
 * 仍可查询最后已知状态；运行中的实时状态继续向 Runner 查询并回写。
 */
@Service
public class DashboardExecutionServiceImpl implements DashboardExecutionService {
    private static final String DEFAULT_TIMEOUT_SECONDS = "60";
    private static final String DEFAULT_MAX_OUTPUT_BYTES = "50000";
    private static final int RESULT_PREVIEW_MAX_ROWS = 10;
    private final InsightDashboardService dashboards;
    private final ScriptTaskPreparationService preparation;
    private final PythonExecutionService runner;
    private final WorkspaceGuard workspaceGuard;
    private final ObjectMapper mapper;
    private final DashboardExecutionMapper executionMapper;
    private final ObjectRefService objectRefs;
    private final String datasetReadBaseUrl;

    public DashboardExecutionServiceImpl(
            InsightDashboardService dashboards,
            ScriptTaskPreparationService preparation,
            PythonExecutionService runner,
            WorkspaceGuard workspaceGuard,
            ObjectMapper mapper,
            DashboardExecutionMapper executionMapper,
            ObjectRefService objectRefs,
            @Value("${mateclaw.runner.dataset-read-base-url:http://mateclaw-dataagent:18089/dataagent/api}") String datasetReadBaseUrl) {
        this.dashboards = dashboards;
        this.preparation = preparation;
        this.runner = runner;
        this.workspaceGuard = workspaceGuard;
        this.mapper = mapper;
        this.executionMapper = executionMapper;
        this.objectRefs = objectRefs;
        this.datasetReadBaseUrl = datasetReadBaseUrl.replaceAll("/$", "");
    }

    @Override
    public Map<String, Object> submit(long dashboardId, DashboardExecutionRequest request) {
        InsightDashboardVO dashboard = dashboards.getDashboard(dashboardId);
        JsonNode schema = parseSchema(dashboard.getSchemaJson());
        JsonNode pipeline = request == null ? null : findComponentPipeline(schema, request.componentId());
        JsonNode executionSchema = pipeline == null ? schema : pipeline;
        String script = text(executionSchema, "script");
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("dashboard script is required");
        }
        Map<String, Long> inputs = parseInputs(executionSchema.path("datasetInputs"));
        validateFilterBindings(executionSchema.path("scriptFilterBindings"), inputs.keySet());
        Map<String, Object> parameters = resolveParameters(
                executionSchema.path("parameters"), request == null ? Map.of() : request.parameters());
        String taskId = "dashboard-" + dashboardId + "-" + UUID.randomUUID();
        ScriptTaskPreparationService.PreparedTask prepared = preparation.prepare(
                taskId, workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(),
                inputs, script, parameters);

        JsonNode policy = schema.path("executionPolicy");
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("timeout_seconds", boundedInt(policy, "timeoutSeconds", Integer.parseInt(DEFAULT_TIMEOUT_SECONDS), 1, 900));
        limits.put("max_stdout_bytes", boundedInt(policy, "maxOutputBytes", Integer.parseInt(DEFAULT_MAX_OUTPUT_BYTES), 1, 5_000_000));
        Map<String, Object> runnerRequest = new LinkedHashMap<>();
        runnerRequest.put("taskId", taskId);
        runnerRequest.put("script", prepared.script());
        runnerRequest.put("inputCatalog", prepared.inputCatalog());
        runnerRequest.put("parameters", prepared.parameters());
        runnerRequest.put("limits", limits);
        runnerRequest.put("datasetReadEndpoint", datasetReadBaseUrl + "/internal/v1/script-tasks/" + taskId + "/datasets/read");
        runnerRequest.put("resultUploadEndpoint", datasetReadBaseUrl + "/internal/v1/script-tasks/" + taskId + "/result");
        runnerRequest.put("readToken", prepared.readToken());
        DashboardExecutionEntity execution = new DashboardExecutionEntity();
        execution.setExecutionId(taskId);
        execution.setDashboardId(dashboardId);
        execution.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        execution.setUserId(workspaceGuard.currentUserId());
        execution.setStatus("SUBMITTING");
        execution.setParametersJson(writeJson(prepared.parameters()));
        executionMapper.insert(execution);
        Map<String, Object> result;
        try {
            result = runner.submit(runnerRequest);
            updateFromRunner(execution, result);
        } catch (RuntimeException e) {
            execution.setStatus("FAILED");
            execution.setErrorMessage(e.getMessage());
            executionMapper.updateById(execution);
            throw e;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", taskId);
        response.put("dashboardId", dashboardId);
        response.put("status", result.getOrDefault("status", "RUNNING"));
        return response;
    }

    /** 组件级数据集编排优先，旧仪表盘继续从根级 Schema 执行。 */
    private JsonNode findComponentPipeline(JsonNode schema, String componentId) {
        if (componentId == null || componentId.isBlank()) return null;
        JsonNode pages = schema.path("pages");
        if (!pages.isArray()) return null;
        for (JsonNode page : pages) {
            JsonNode components = page.path("components");
            if (!components.isArray()) continue;
            for (JsonNode component : components) {
                if (componentId.equals(text(component, "id"))) {
                    JsonNode pipeline = component.path("config").path("datasetPipeline");
                    return pipeline.isObject() ? pipeline : null;
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> status(String executionId) {
        DashboardExecutionEntity execution = requireKnown(executionId);
        try {
            Map<String, Object> result = runner.getStatus(executionId);
            updateFromRunner(execution, result);
            return response(execution, result);
        } catch (RuntimeException e) {
            return response(execution, persistedResult(execution));
        }
    }

    @Override
    public Map<String, Object> cancel(String executionId) {
        DashboardExecutionEntity execution = requireKnown(executionId);
        Map<String, Object> result = runner.cancel(executionId);
        updateFromRunner(execution, result);
        return response(execution, result);
    }

    @Override
    public Map<String, Object> logs(String executionId) {
        DashboardExecutionEntity execution = requireKnown(executionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getExecutionId());
        response.put("status", execution.getStatus());
        response.put("output", execution.getLogs());
        response.put("error", execution.getErrorMessage());
        return response;
    }

    @Override
    public Map<String, Object> result(String executionId) {
        DashboardExecutionEntity execution = requireKnown(executionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getExecutionId());
        response.put("status", execution.getStatus());
        try {
            if (execution.getOutputJson() != null && !execution.getOutputJson().isBlank()) {
                response.put("rows", mapper.readValue(execution.getOutputJson(), Object.class));
                response.put("inline", true);
                return response;
            }
            if (execution.getOutputRefJson() == null || "null".equals(execution.getOutputRefJson())) {
                response.put("rows", List.of());
                response.put("inline", true);
                return response;
            }
            ObjectRef reference = mapper.readValue(execution.getOutputRefJson(), ObjectRef.class);
            DatasetAccessContext context = new DatasetAccessContext(execution.getWorkspaceId(), 0L, execution.getExecutionId(), Set.of());
            response.put("rows", DatasetBatchCodec.readRows(context, reference, objectRefs, RESULT_PREVIEW_MAX_ROWS));
            response.put("inline", false);
            response.put("outputRef", reference);
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("dashboard execution result unavailable", e);
        }
    }

    private DashboardExecutionEntity requireKnown(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("unknown dashboard execution");
        }
        DashboardExecutionEntity execution = executionMapper.selectOne(new LambdaQueryWrapper<DashboardExecutionEntity>()
                .eq(DashboardExecutionEntity::getExecutionId, executionId)
                .eq(DashboardExecutionEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId()));
        if (execution == null) throw new IllegalArgumentException("unknown dashboard execution");
        return execution;
    }

    private void updateFromRunner(DashboardExecutionEntity execution, Map<String, Object> result) {
        if (result == null) return;
        Object status = result.get("status");
        if (status != null) execution.setStatus(String.valueOf(status));
        execution.setLogs(stringValue(result.get("output")));
        execution.setOutputJson(stringValue(result.get("result")));
        execution.setOutputRefJson(writeJson(result.get("outputRef")));
        execution.setErrorMessage(stringValue(result.get("error")));
        Object returnCode = result.get("stats") instanceof Map<?, ?> stats ? stats.get("returncode") : result.get("returncode");
        if (returnCode instanceof Number number) execution.setReturnCode(number.intValue());
        executionMapper.updateById(execution);
    }

    private Map<String, Object> response(DashboardExecutionEntity execution, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getExecutionId());
        response.put("dashboardId", execution.getDashboardId());
        response.put("status", execution.getStatus());
        response.put("result", execution.getOutputJson());
        if (execution.getOutputRefJson() != null && !"null".equals(execution.getOutputRefJson())) {
            try { response.put("outputRef", mapper.readValue(execution.getOutputRefJson(), Object.class)); }
            catch (Exception ignored) { response.put("outputRef", execution.getOutputRefJson()); }
        }
        response.put("output", execution.getLogs());
        response.put("error", execution.getErrorMessage());
        response.put("returnCode", execution.getReturnCode());
        if (result != null && !result.isEmpty()) response.put("runner", result);
        return response;
    }

    private Map<String, Object> persistedResult(DashboardExecutionEntity execution) {
        return Map.of("status", execution.getStatus(), "result", execution.getOutputJson() == null ? "" : execution.getOutputJson());
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid execution parameters", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private JsonNode parseSchema(String schemaJson) {
        try {
            return mapper.readTree(schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid dashboard schema", e);
        }
    }

    private Map<String, Long> parseInputs(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("dashboard datasetInputs are required");
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (JsonNode input : node) {
            String alias = text(input, "inputName");
            if (alias == null || !alias.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || result.containsKey(alias)) {
                throw new IllegalArgumentException("invalid or duplicate dataset input alias");
            }
            JsonNode id = input.get("datasetId");
            Long datasetId = parsePositiveLong(id);
            if (datasetId == null) {
                throw new IllegalArgumentException("dataset input datasetId is required");
            }
            result.put(alias, datasetId);
        }
        return result;
    }

    private void validateFilterBindings(JsonNode node, Set<String> inputNames) {
        if (node == null || !node.isArray()) return;
        for (JsonNode binding : node) {
            JsonNode names = binding.path("inputNames");
            if (!names.isArray()) throw new IllegalArgumentException("script filter binding inputNames are required");
            for (JsonNode name : names) {
                if (!name.isTextual() || !inputNames.contains(name.asText()))
                    throw new IllegalArgumentException("script filter binding references an unknown input");
            }
            JsonNode mappings = binding.path("fieldMappings");
            if (mappings.isObject()) {
                var fields = mappings.fieldNames();
                while (fields.hasNext()) {
                    if (!inputNames.contains(fields.next())) throw new IllegalArgumentException("script filter field mapping references an unknown input");
                }
            }
        }
    }

    /**
     * 当 Schema 声明了参数时，执行入口采用严格契约；旧 Schema 没有参数定义时保留透传兼容。
     */
    private Map<String, Object> resolveParameters(JsonNode definitions, Map<String, Object> provided) {
        Map<String, Object> values = provided == null ? Map.of() : provided;
        if (!definitions.isArray() || definitions.isEmpty()) {
            return new LinkedHashMap<>(values);
        }
        Map<String, JsonNode> declared = new LinkedHashMap<>();
        for (JsonNode definition : definitions) {
            String name = text(definition, "name");
            if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || declared.containsKey(name)) {
                throw new IllegalArgumentException("invalid or duplicate dashboard parameter name");
            }
            String type = text(definition, "type");
            if (!Set.of("string", "number", "boolean", "date", "datetime", "enum", "date_range", "string[]", "number[]").contains(type)) {
                throw new IllegalArgumentException("unsupported dashboard parameter type: " + type);
            }
            declared.put(name, definition);
        }
        for (String name : values.keySet()) {
            if (!declared.containsKey(name)) {
                throw new IllegalArgumentException("unknown dashboard execution parameter: " + name);
            }
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : declared.entrySet()) {
            String name = entry.getKey();
            JsonNode definition = entry.getValue();
            if (values.containsKey(name)) {
                Object value = values.get(name);
                if (!matchesParameterType(value, text(definition, "type"))) {
                    throw new IllegalArgumentException("invalid dashboard execution parameter type: " + name);
                }
                resolved.put(name, value);
            } else if (definition.has("defaultValue")) {
                resolved.put(name, mapper.convertValue(definition.get("defaultValue"), Object.class));
            } else if (definition.path("required").asBoolean(false)) {
                throw new IllegalArgumentException("missing required dashboard execution parameter: " + name);
            }
        }
        return resolved;
    }

    private boolean matchesParameterType(Object value, String type) {
        if (value == null) return false;
        return switch (type) {
            case "string", "date", "datetime", "enum" -> value instanceof CharSequence;
            case "date_range" -> (isListOf(value, CharSequence.class) && ((Collection<?>) value).size() == 2)
                    || isDateRangeObject(value);
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "string[]" -> isListOf(value, CharSequence.class);
            case "number[]" -> isListOf(value, Number.class);
            default -> false;
        };
    }

    private boolean isListOf(Object value, Class<?> elementType) {
        if (!(value instanceof Collection<?> collection)) return false;
        return collection.stream().allMatch(element -> element != null && elementType.isInstance(element));
    }

    private boolean isDateRangeObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) return false;
        Object preset = map.get("preset");
        if (!(preset instanceof CharSequence) || preset.toString().isBlank()) return false;
        Object start = map.get("start");
        Object end = map.get("end");
        return (start == null || start instanceof CharSequence)
                && (end == null || end instanceof CharSequence);
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Long parsePositiveLong(JsonNode value) {
        if (value == null) return null;
        try {
            long parsed = value.isTextual() ? Long.parseLong(value.asText()) : value.asLong();
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int boundedInt(JsonNode node, String name, int fallback, int min, int max) {
        JsonNode value = node.get(name);
        if (value == null || !value.canConvertToInt()) return fallback;
        return Math.max(min, Math.min(max, value.asInt()));
    }
}
