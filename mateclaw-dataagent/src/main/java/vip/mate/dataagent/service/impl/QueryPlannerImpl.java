package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.service.QueryPlanErrorCodes;
import vip.mate.dataagent.service.QueryPlanException;
import vip.mate.dataagent.service.QueryPlanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认查询计划器实现。
 * <p>
 * 权威配置来源：{@code input.queryConfig.parameterBindings}；旧 Schema 无 queryConfig 时才归一化
 * {@code component.config.datasetPipeline.scriptFilterBindings}，两份规则绝不同时生效。
 * 字段名一律是数据源技术字段名（queryableFields[].name / displayFields[].field），展示名不进下推链路。
 */
@Service
public class QueryPlannerImpl implements QueryPlanner {

    /** Python 脚本可能改变行数/顺序时，源端读取的默认行数上限（有界残余读取）。 */
    static final int DEFAULT_SCRIPT_READ_LIMIT = 100_000;
    private static final int MAX_FILTER_VALUE_ITEMS = 10_000;

    @Override
    public DatasetQueryPlanDTO plan(JsonNode component, JsonNode input, QueryContextDTO runtime, boolean hasPython) {
        if (component == null || !component.isObject()) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "component context is required");
        }
        if (input == null || !input.isObject()) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "dataset input context is required");
        }
        if (runtime == null) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, "query context is required");
        }
        String alias = requiredText(input, "inputName", "dataset input alias");
        JsonNode queryConfig = input.path("queryConfig");
        boolean authoritative = queryConfig.isObject() && queryConfig.size() > 0;

        // ---- 完整可查询字段注册表（用于筛选校验）；展示字段仅定义输出投影 ----
        Map<String, String> fieldRoles = new LinkedHashMap<>(); // field -> role
        List<String> columns = new ArrayList<>();
        if (authoritative) {
            JsonNode queryableFields = queryConfig.path("queryableFields");
            // 兼容没有完整目录的旧配置：旧版只能对展示字段筛选。
            JsonNode registry = queryableFields.isArray() ? queryableFields : queryConfig.path("displayFields");
            String fieldProperty = queryableFields.isArray() ? "name" : "field";
            for (JsonNode registeredField : registry) {
                String field = requiredText(registeredField, fieldProperty, "queryable field");
                String role = registeredField.path("role").asText("dimension");
                if (fieldRoles.putIfAbsent(field, role) != null) {
                    throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED, "duplicate queryable field: " + field);
                }
            }
            for (JsonNode displayField : queryConfig.path("displayFields")) {
                String field = requiredText(displayField, "field", "display field");
                if (!fieldRoles.containsKey(field)) {
                    // 旧配置若存在 queryableFields，应保证展示列也在真实字段目录中。
                    throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED,
                            "display field is not registered as queryable: " + field);
                }
                if (columns.contains(field)) {
                    throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED, "duplicate display field: " + field);
                }
                columns.add(field);
            }
        }

        // ---- 绑定归一化（二选一，不同时生效） ----
        List<Binding> bindings = authoritative
                ? readAuthoritativeBindings(queryConfig, alias, fieldRoles.keySet())
                : readLegacyBindings(component, alias);

        // ---- 绑定的筛选器组件必须仍然存在（被删除的映射在 Planner 阶段失败） ----
        Set<String> boundFilterIds = readBoundFilterComponentIds(component);
        if (!boundFilterIds.isEmpty()) {
            for (Binding binding : bindings) {
                if (binding.filterComponentId == null || !boundFilterIds.contains(binding.filterComponentId)) {
                    throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED,
                            "binding references a deleted filter component: " + binding.filterComponentId);
                }
            }
        }

        // ---- 参数白名单：runtime.parameters 的键必须来自组件内已声明的参数定义 ----
        Set<String> declaredParameters = collectDeclaredParameters(component);
        for (String key : runtime.parameters().keySet()) {
            if (!declaredParameters.contains(key)) {
                throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                        "unknown query parameter: " + key);
            }
        }

        // ---- 筛选：空选/全部 = 无过滤；显式空集合 = 短路空结果 ----
        boolean shortCircuitEmpty = false;
        List<DatasetQueryPlanDTO.FilterSpec> filters = new ArrayList<>();
        for (Binding binding : bindings) {
            if (!runtime.parameters().containsKey(binding.parameterName)) {
                continue; // 参数未提供 = 不过滤
            }
            Object value = runtime.parameters().get(binding.parameterName);
            if (isEmptySelection(value)) {
                continue; // 清空 / null / 空串 = 不过滤
            }
            if (value instanceof Collection<?> collection && collection.isEmpty()) {
                shortCircuitEmpty = true; // 业务上明确的空集合：短路，不访问数据源
                continue;
            }
            if (value instanceof Collection<?> collection && collection.size() > MAX_FILTER_VALUE_ITEMS) {
                throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                        "filter value exceeds " + MAX_FILTER_VALUE_ITEMS + " items: " + binding.parameterName);
            }
            // 复用 DatasetFilter 校验运算符与值合法性（is_null/is_not_null 允许 null）
            Object filterValue = normalizeFilterValue(binding.operator, value);
            new DatasetFilter(binding.field, fieldRoles.getOrDefault(binding.field, "dimension"), binding.operator, filterValue);
            filters.add(new DatasetQueryPlanDTO.FilterSpec(binding.field, binding.operator, filterValue));
        }

        // ---- 排序：仅 allowedSortFields 且非脚本计算字段才可下推 ----
        QueryContextDTO.SortSpec effectiveSort = runtime.sort();
        List<DatasetQueryPlanDTO.OrderSpec> orders = new ArrayList<>();
        boolean sortPushed = false;
        List<String> residual = new ArrayList<>();
        if (effectiveSort == null && authoritative) {
            effectiveSort = readDefaultSort(queryConfig);
        }
        if (effectiveSort != null) {
            JsonNode sortPolicy = authoritative ? queryConfig.path("sortPolicy") : null;
            boolean enabled = sortPolicy != null && sortPolicy.path("enabled").asBoolean(false);
            if (!enabled) {
                throw QueryPlanException.of(QueryPlanErrorCodes.SORT_NOT_ALLOWED,
                        "sorting is not enabled for this input");
            }
            Set<String> allowedFields = new LinkedHashSet<>();
            for (JsonNode field : sortPolicy.path("allowedFields")) {
                if (field.isTextual()) allowedFields.add(field.asText());
            }
            if (!allowedFields.contains(effectiveSort.field())) {
                throw QueryPlanException.of(QueryPlanErrorCodes.SORT_NOT_ALLOWED,
                        "sort field is not allowed: " + effectiveSort.field());
            }
            orders.add(new DatasetQueryPlanDTO.OrderSpec(effectiveSort.field(), effectiveSort.direction()));
            if (hasPython) {
                // 脚本可能改变行数/粒度/顺序：最终排序放在结果集阶段
                residual.add("SORT_AT_RESULT_STAGE");
            } else {
                sortPushed = true;
            }
        }

        // ---- 分页：单数据集且无改变行数的脚本时才可下推最终分页 ----
        QueryContextDTO.PaginationSpec pagination = runtime.pagination();
        boolean paginationPushed = false;
        Integer readLimit = null;
        if (pagination != null) {
            JsonNode paginationPolicy = authoritative ? queryConfig.path("paginationPolicy") : null;
            boolean enabled = paginationPolicy != null && paginationPolicy.path("enabled").asBoolean(false);
            if (!enabled) {
                throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                        "pagination is not enabled for this input");
            }
            int maxPageSize = QueryContextDTO.MAX_PAGE_SIZE;
            JsonNode configuredMax = paginationPolicy.path("maxPageSize");
            if (configuredMax.canConvertToInt() && configuredMax.asInt() > 0) {
                maxPageSize = Math.min(maxPageSize, configuredMax.asInt());
            }
            if (pagination.pageSize() > maxPageSize) {
                throw QueryPlanException.of(QueryPlanErrorCodes.PAGE_SIZE_EXCEEDED,
                        "pageSize exceeds the configured maximum " + maxPageSize);
            }
            if (hasPython) {
                // 结果集阶段分页；源端读取必须有明确行数上限，不能先截断再聚合
                residual.add("PAGINATION_AT_RESULT_STAGE");
                readLimit = DEFAULT_SCRIPT_READ_LIMIT;
            } else {
                paginationPushed = true; // Adapter 转 limit=pageSize, offset=(page-1)*pageSize
            }
        } else if (hasPython && !orders.isEmpty()) {
            readLimit = DEFAULT_SCRIPT_READ_LIMIT;
        }

        // 显式空集合短路：AND 语义下结果恒为空，清空条件且不访问数据源
        if (shortCircuitEmpty) {
            filters.clear();
        }

        String datasetId = requiredDatasetId(input, runtime);
        return new DatasetQueryPlanDTO(datasetId, alias, columns, filters, orders, pagination,
                new DatasetQueryPlanDTO.PushdownSpec(!shortCircuitEmpty, sortPushed, paginationPushed),
                residual, shortCircuitEmpty, readLimit);
    }

    private String requiredDatasetId(JsonNode input, QueryContextDTO runtime) {
        JsonNode datasetIdNode = input.path("datasetId");
        String datasetId = datasetIdNode.isMissingNode() || datasetIdNode.isNull()
                || datasetIdNode.asText().isBlank() ? null : datasetIdNode.asText();
        if (datasetId == null) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    "dataset input datasetId is required");
        }
        if (runtime.datasetId() != null && !runtime.datasetId().equals(datasetId)) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    "query context datasetId does not match the input binding");
        }
        return datasetId;
    }

    // ==================== 绑定读取 ====================

    private record Binding(String filterComponentId, String parameterName, String field, String operator) {
    }

    private List<Binding> readAuthoritativeBindings(JsonNode queryConfig, String alias, Set<String> registry) {
        List<Binding> bindings = new ArrayList<>();
        for (JsonNode node : queryConfig.path("parameterBindings")) {
            String filterComponentId = requiredText(node, "filterComponentId", "parameter binding filterComponentId");
            if (!filterComponentId.matches("[A-Za-z0-9_\\-]{1,64}")) {
                throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED,
                        "invalid filter component id: " + filterComponentId);
            }
            String parameterName = requiredText(node, "parameterName", "parameter binding parameterName");
            String field = requiredText(node, "field", "parameter binding field");
            if (!registry.contains(field)) {
                throw QueryPlanException.of(QueryPlanErrorCodes.FIELD_NOT_ALLOWED,
                        "binding references a field that is not registered: " + field);
            }
            String operator = requiredText(node, "operator", "parameter binding operator");
            bindings.add(new Binding(filterComponentId, parameterName, field, operator));
        }
        return bindings;
    }

    /**
     * 旧 Schema 归一化：scriptFilterBindings.conditions[] 携带 inputName/field/operator/parameterNames，
     * 只处理 {@code inputNames} 包含当前输入别名的绑定；仅在没有 queryConfig 的旧 Schema 上使用。
     */
    private List<Binding> readLegacyBindings(JsonNode component, String alias) {
        List<Binding> bindings = new ArrayList<>();
        JsonNode pipeline = component.path("config").path("datasetPipeline");
        JsonNode legacy = pipeline.path("scriptFilterBindings");
        if (!legacy.isArray()) {
            legacy = component.path("scriptFilterBindings");
        }
        if (!legacy.isArray()) {
            return bindings;
        }
        for (JsonNode binding : legacy) {
            boolean appliesToInput = false;
            for (JsonNode name : binding.path("inputNames")) {
                if (name.isTextual() && alias.equals(name.asText())) {
                    appliesToInput = true;
                    break;
                }
            }
            if (!appliesToInput) continue;
            JsonNode conditions = binding.path("conditions");
            if (conditions.isArray() && !conditions.isEmpty()) {
                for (JsonNode condition : conditions) {
                    if (!alias.equals(condition.path("inputName").asText())) continue;
                    JsonNode parameterNames = condition.path("parameterNames");
                    String parameterName = parameterNames.isArray() && !parameterNames.isEmpty()
                            ? parameterNames.get(0).asText() : binding.path("filterComponentId").asText();
                    bindings.add(new Binding(
                            binding.path("filterComponentId").asText(null),
                            parameterName,
                            requiredText(condition, "field", "legacy filter condition field"),
                            requiredText(condition, "operator", "legacy filter condition operator")));
                }
            } else {
                // 更旧的形态：filterComponentId 即参数名，字段来自 fieldMappings[alias]
                JsonNode mappings = binding.path("fieldMappings");
                String field = mappings.isObject() && mappings.has(alias) ? mappings.path(alias).asText() : null;
                if (field != null && !field.isBlank()) {
                    bindings.add(new Binding(
                            binding.path("filterComponentId").asText(null),
                            binding.path("filterComponentId").asText(),
                            field, "eq"));
                }
            }
        }
        return bindings;
    }

    /** 组件声明的筛选器组件 id 列表（前端保存查询配置时写入）；缺失时跳过归属校验。 */
    private Set<String> readBoundFilterComponentIds(JsonNode component) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode pipeline = component.path("config").path("datasetPipeline");
        JsonNode bound = pipeline.path("boundFilterComponentIds");
        if (!bound.isArray()) {
            bound = pipeline.path("filters");
        }
        if (bound.isArray()) {
            for (JsonNode id : bound) {
                String value = id.isTextual() ? id.asText() : id.path("id").asText(null);
                if (value != null && !value.isBlank()) ids.add(value);
            }
        }
        return ids;
    }

    /** 跨输入收集全部已声明的参数名，用于拒绝客户端伪造的 parameters 键。 */
    private Set<String> collectDeclaredParameters(JsonNode component) {
        Set<String> declared = new LinkedHashSet<>();
        JsonNode inputs = component.path("config").path("datasetPipeline").path("datasetInputs");
        if (inputs.isArray()) {
            for (JsonNode input : inputs) {
                JsonNode queryConfig = input.path("queryConfig");
                if (queryConfig.isObject()) {
                    for (JsonNode binding : queryConfig.path("parameterBindings")) {
                        if (binding.hasNonNull("parameterName")) {
                            declared.add(binding.path("parameterName").asText());
                        }
                    }
                }
            }
        }
        JsonNode pipeline = component.path("config").path("datasetPipeline");
        JsonNode legacy = pipeline.path("scriptFilterBindings");
        if (!legacy.isArray()) {
            legacy = component.path("scriptFilterBindings");
        }
        if (legacy.isArray()) {
            for (JsonNode binding : legacy) {
                JsonNode conditions = binding.path("conditions");
                if (conditions.isArray()) {
                    for (JsonNode condition : conditions) {
                        for (JsonNode name : condition.path("parameterNames")) {
                            if (name.isTextual()) declared.add(name.asText());
                        }
                    }
                } else if (binding.hasNonNull("filterComponentId")) {
                    declared.add(binding.path("filterComponentId").asText());
                }
            }
        }
        return declared;
    }

    private QueryContextDTO.SortSpec readDefaultSort(JsonNode queryConfig) {
        JsonNode defaultSort = queryConfig.path("sortPolicy").path("defaultSort");
        if (!defaultSort.isObject() || !defaultSort.hasNonNull("field")) {
            return null;
        }
        return new QueryContextDTO.SortSpec(
                defaultSort.path("field").asText(),
                defaultSort.path("direction").asText("asc"));
    }

    // ==================== 值语义 ====================

    /** 空选 / 「全部」/ null / 空串 = 无过滤（区别于显式空集合短路）。 */
    private boolean isEmptySelection(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence text) return text.toString().isBlank();
        return false;
    }

    private Object normalizeFilterValue(String operator, Object value) {
        String normalized = operator.toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("is_null") || normalized.equals("is_not_null")) {
            return true;
        }
        return value;
    }

    private String requiredText(JsonNode node, String name, String what) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw QueryPlanException.of(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID,
                    what + " is required");
        }
        return value.asText();
    }
}
