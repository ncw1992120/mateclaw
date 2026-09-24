package vip.mate.dataagent.aloudata.local;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本地 mock 的 Aloudata 报文仓库。
 * <p>
 * 只服务本地联调：从 classpath {@code mock/aloudata/} 读取夹具，按端点名与请求参数返回
 * **与真实 Aloudata 响应同构**的报文（顶层 {@code data/success/code/errorMsg/detailErrorMsg/traceId}，
 * {@code code} 为字符串）。夹具由
 * {@code dev-support/local-simulation/scripts/generate-aloudata-fixtures.py} 从
 * {@code generated_cljd_zcl.sql} 生成。
 * <p>
 * 与真实环境的差异只在数据内容，不在结构：
 * <ul>
 *   <li>{@code basicAttributes.owner} 在夹具里写作占位符 {@code __OWNER__}，装载时替换为
 *       当前数据源的认证值，使「只看我的」恒等价于「全部 mock 视图」；</li>
 *   <li>未知 viewName 返回真实的 {@code SM_02_0038}（视图无权限）报文，便于验证降级路径；</li>
 *   <li>{@code metrics_query} 没有 viewName 入参，按「请求的指标+维度落在哪个视图上」反查后
 *       做列投影、等值筛选与分页。</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("local-mock")
public class LocalAloudataFixtures {

    /** 夹具目录（classpath）。 */
    private static final String BASE = "mock/aloudata/";

    /** 夹具中 owner 占位符。 */
    private static final String OWNER_PLACEHOLDER = "__OWNER__";

    /** 拿不到数据源认证值时的兜底 owner。 */
    private static final String DEFAULT_OWNER = "mock-uid-001";

    /** 视图无权限业务码（与真实 Aloudata 一致）。 */
    private static final String CODE_VIEW_ACCESS_DENIED = "SM_02_0038";

    /** 系统异常业务码（真实服务对非法请求形状的返回）。 */
    private static final String CODE_SYSTEM_ERROR = "SM99002";

    /** 筛选表达式：{@code [字段] 运算符 值}，字段可用 {@code ['字段']} 形式。 */
    private static final Pattern CONDITION = Pattern.compile(
            "\\['?([^'\\]]+)'?\\]\\s*(<>|>=|<=|=|>|<|IN|NotIn)\\s*(.+)", Pattern.CASE_INSENSITIVE);

    /** 视图名 → 结果集夹具文件内的键；顺序即 metrics_query 反查的优先级。 */
    private static final List<String> VIEW_ORDER = List.of("cljd_zcl_zb_view", "cljd_zcl_wd_view");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 原始夹具缓存（只读，禁止就地修改；每次返回前深拷贝）。 */
    private final Map<String, Map<String, Object>> rawCache = new HashMap<>();

    /** 按端点名分发，返回可直接作为响应体的可变 Map。 */
    public Map<String, Object> payload(String endpointName, Map<String, Object> params, String authValue) {
        String owner = (authValue == null || authValue.isBlank()) ? DEFAULT_OWNER : authValue;
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        return switch (endpointName == null ? "" : endpointName) {
            case "analysis_view_tree" -> treeList(owner);
            case "metric_tree" -> copy(load("metric_tree.json"));
            case "category_list" -> categoryList(safeParams);
            case "metric_list" -> metricList(safeParams);
            case "analysis_view_list" -> listViews(owner, safeParams);
            case "analysis_view_query_by_name" -> viewByName(owner, safeParams);
            case "metric_batch_detail" -> metricBatchDetail(owner, safeParams);
            case "metric_all_dimensions" -> metricAllDimensions(safeParams);
            case "dimension_list" -> dimensionList(owner, safeParams);
            case "dimension_detail" -> dimensionDetail(safeParams);
            case "dimension_values" -> dimensionValues(safeParams);
            case "analysis_view_query_data" -> queryData(owner, safeParams);
            case "metrics_query" -> metricsQuery(owner, safeParams);
            default -> unknownEndpoint(endpointName);
        };
    }

    // ------------------------------------------------------------------ 端点实现

    /** 视图目录树：与真实一致替换 owner 占位符（「只看我的」依赖 owner）。 */
    private Map<String, Object> treeList(String owner) {
        Map<String, Object> envelope = copy(load("analysis_view_tree.json"));
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> categoryList(Map<String, Object> params) {
        Map<String, Object> envelope = copy(load("category_list.json"));
        String categoryType = firstString(params.get("categoryType"));
        List<Map<String, Object>> categories = asMapList(envelope.get("data"));
        if (categoryType != null) {
            categories.removeIf(category -> !categoryType.equals(firstString(category.get("categoryType"))));
        }
        envelope.put("data", categories);
        return envelope;
    }

    /** 指标选择器列表：按展示名/字段名、类目和页码筛选现有视图指标。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> metricList(Map<String, Object> params) {
        Map<String, Object> envelope = copy(load("metric_batch_detail.json"));
        List<Map<String, Object>> items = asMapList(envelope.get("data"));
        String keyword = firstString(params.get("keyword"));
        String categoryId = firstString(params.get("metricCategoryId"));
        Set<String> categoryIds = categoryId == null ? Set.of()
                : categoryIdsFor(categoryId, "CATEGORY_METRIC");
        String needle = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String name = firstString(item.get("metricName"));
            String displayName = firstString(item.get("metricDisplayName"));
            String itemCategory = firstString(item.get("metricCategoryId"));
            if (needle != null && !containsIgnoreCase(name, needle) && !containsIgnoreCase(displayName, needle)) {
                continue;
            }
            if (categoryId != null && !categoryIds.contains(itemCategory)) {
                continue;
            }
            matched.add(item);
        }
        int pageSize = Math.max(0, intValue(params.get("pageSize"), matched.size()));
        int pageNumber = Math.max(1, intValue(params.get("pageNumber"), 1));
        int from = Math.min(matched.size(), (pageNumber - 1) * pageSize);
        int to = Math.min(matched.size(), from + pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", matched.size());
        data.put("pageNumber", pageNumber);
        data.put("pageSize", pageSize);
        data.put("hasNext", to < matched.size());
        data.put("data", pageSize == 0 ? List.of() : new ArrayList<>(matched.subList(from, to)));
        envelope.put("data", data);
        return envelope;
    }

    /** 指标视图平铺列表：按 keyword 模糊匹配 viewName / displayName，"_" 视为全量；支持 pageNumber/pageSize。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> listViews(String owner, Map<String, Object> params) {
        Map<String, Object> envelope = copy(load("analysis_view_list.json"));
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        Map<String, Object> data = asMap(envelope.get("data"));
        List<Map<String, Object>> items = asMapList(data.get("data"));

        String keyword = firstString(params.get("keyword"));
        boolean all = keyword == null || keyword.isBlank() || "_".equals(keyword);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (all) {
                matched.add(item);
                continue;
            }
            String viewName = firstString(item.get("viewName"));
            String displayName = firstString(item.get("displayName"));
            String needle = keyword.toLowerCase(Locale.ROOT);
            if ((viewName != null && viewName.toLowerCase(Locale.ROOT).contains(needle))
                    || (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(needle))) {
                matched.add(item);
            }
        }
        int pageSize = intValue(params.get("pageSize"), matched.size());
        int pageNumber = Math.max(1, intValue(params.get("pageNumber"), 1));
        int from = Math.min(matched.size(), (pageNumber - 1) * Math.max(pageSize, 0));
        int to = Math.min(matched.size(), from + Math.max(pageSize, 0));
        data.put("data", pageSize <= 0 ? List.of() : new ArrayList<>(matched.subList(from, to)));
        data.put("pageNumber", pageNumber);
        data.put("pageSize", pageSize);
        data.put("totalPageSize", matched.size());
        return envelope;
    }

    /** 维度列表：按 body 里的 {@code pager{pageNumber,pageSize}} 分页（真实接口同样是 POST + pager）。 */
    private Map<String, Object> dimensionList(String owner, Map<String, Object> params) {
        Map<String, Object> envelope = copy(load("dimension_list.json"));
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        Map<String, Object> data = asMap(envelope.get("data"));
        List<Map<String, Object>> all = asMapList(data.get("data"));
        String keyword = firstString(params.get("keyword"));
        String categoryId = firstString(params.get("categoryId"));
        Set<String> categoryIds = categoryId == null ? Set.of()
                : categoryIdsFor(categoryId, "CATEGORY_DIMENSION");
        String needle = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : all) {
            String name = firstString(item.get("dimName"));
            String displayName = firstString(item.get("dimDisplayName"));
            if (needle != null && !containsIgnoreCase(name, needle) && !containsIgnoreCase(displayName, needle)) {
                continue;
            }
            if (categoryId != null && !categoryIds.contains(firstString(item.get("dimCategoryId")))) {
                continue;
            }
            matched.add(item);
        }
        Map<String, Object> pager = asMap(params.get("pager"));
        int pageSize = intValue(pager.get("pageSize"), matched.size());
        int pageNumber = Math.max(1, intValue(pager.get("pageNumber"), 1));
        int from = Math.min(matched.size(), (pageNumber - 1) * Math.max(pageSize, 0));
        int to = Math.min(matched.size(), from + Math.max(pageSize, 0));
        data.put("data", pageSize <= 0 ? List.of() : new ArrayList<>(matched.subList(from, to)));
        data.put("pageNumber", pageNumber);
        data.put("pageSize", pageSize);
        data.put("total", matched.size());
        data.put("hasNext", to < matched.size());
        return envelope;
    }

    /** 指标可用维度关系直接由两个策略视图的 metrics/dimensions 字段成员关系派生。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> metricAllDimensions(Map<String, Object> params) {
        Set<String> requested = nameSet(params.get("metricNames"));
        Map<String, Object> views = copy(load("analysis_view_query_by_name.json"));
        Map<String, Object> relations = new LinkedHashMap<>();
        for (Object rawEnvelope : views.values()) {
            Map<String, Object> view = asMap(asMap(rawEnvelope).get("data"));
            List<String> dimensions = stringList(view.get("dimensions"));
            for (String metricName : stringList(view.get("metrics"))) {
                if (requested.isEmpty() || requested.contains(metricName)) {
                    relations.put(metricName, dimensions);
                }
            }
        }
        return envelope(relations, "mock-trace-metric-dimensions");
    }

    /** 维度悬浮详情复用维度视图同一条目录记录。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> dimensionDetail(Map<String, Object> params) {
        String dimName = firstString(params.get("dimName"));
        Map<String, Object> container = copy(load("dimension_list.json"));
        Map<String, Object> data = asMap(container.get("data"));
        for (Map<String, Object> item : asMapList(data.get("data"))) {
            if (Objects.equals(dimName, firstString(item.get("dimName")))) {
                return envelope(item, "mock-trace-dimension-detail");
            }
        }
        return envelope(null, "mock-trace-dimension-detail");
    }

    @SuppressWarnings("unchecked")
    private Set<String> categoryIdsFor(String requestedId, String categoryType) {
        Map<String, Object> categoriesEnvelope = copy(load("category_list.json"));
        List<Map<String, Object>> categories = asMapList(categoriesEnvelope.get("data"));
        Set<String> ids = new LinkedHashSet<>();
        ids.add(requestedId);
        boolean changed;
        do {
            changed = false;
            for (Map<String, Object> category : categories) {
                String id = firstString(category.get("id"));
                String parentId = firstString(category.get("parentId"));
                if (categoryType.equals(firstString(category.get("categoryType")))
                        && parentId != null && ids.contains(parentId) && ids.add(id)) {
                    changed = true;
                }
            }
        } while (changed);
        return ids;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
    }

    /** 维度值预览：从策略解读-子策略-维度视图读取去重后的维值，支持关键词与分页。 */
    private Map<String, Object> dimensionValues(Map<String, Object> params) {
        Map<String, Object> container = copy(load("analysis_view_query_data.json"));
        Map<String, Object> view = asMap(container.get("cljd_zcl_wd_view"));
        Map<String, Object> data = asMap(view.get("data"));
        Map<String, Object> table = asMap(data.get("table"));
        Map<String, Object> columns = asMap(table.get("columns"));
        String dimName = firstString(params.get("dimName"));

        LinkedHashSet<String> uniqueValues = new LinkedHashSet<>();
        Object rawCells = dimName == null ? null : columns.get(dimName);
        if (rawCells instanceof List<?> cells) {
            for (Object cell : cells) {
                Object value = cell instanceof Map<?, ?> map ? map.get("value") : cell;
                String text = firstString(value);
                if (text != null) uniqueValues.add(text);
            }
        }

        String keyword = firstString(params.get("dimValueKeyword"));
        List<String> matched = uniqueValues.stream()
                .filter(value -> keyword == null
                        || value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .toList();
        int pageSize = Math.max(0, intValue(params.get("pageSize"), 200));
        int pageNumber = Math.max(1, intValue(params.get("pageNumber"), 1));
        int from = Math.min(matched.size(), (pageNumber - 1) * pageSize);
        int to = Math.min(matched.size(), from + pageSize);
        List<String> page = pageSize == 0 ? List.of() : new ArrayList<>(matched.subList(from, to));
        return envelope(page, "mock-trace-dimension-values");
    }

    /** 视图详情：按 viewName 命中；未知视图返回 SM_02_0038，模拟「无该视图权限」。 */
    private Map<String, Object> viewByName(String owner, Map<String, Object> params) {
        String viewName = firstString(params.get("viewName"));
        Map<String, Object> container = copy(load("analysis_view_query_by_name.json"));
        Map<String, Object> envelope = asMap(container.get(viewName));
        if (viewName == null || envelope.isEmpty()) {
            log.warn("[local-mock] queryByName 未命中视图 '{}'，返回 SM_02_0038", viewName);
            return accessDenied(envelope.isEmpty() ? container : envelope);
        }
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        return envelope;
    }

    /** 指标批量详情：返回全量目录按 metricNames 过滤（真实接口也是按名批量查）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> metricBatchDetail(String owner, Map<String, Object> params) {
        Map<String, Object> envelope = copy(load("metric_batch_detail.json"));
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        Set<String> requested = nameSet(params.get("metricNames"));
        if (requested.isEmpty()) {
            return envelope;
        }
        List<Map<String, Object>> all = asMapList(envelope.get("data"));
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> item : all) {
            String name = firstString(item.get("metricName"));
            if (name != null && requested.contains(name)) {
                kept.add(item);
            }
        }
        envelope.put("data", kept);
        return envelope;
    }

    /** 视图结果查询：按 viewName 命中结果集，支持 pageSize / pageIndex 分页（零基页号）。
     *  真实 analysisView/query 响应为 data.table.columns + data.metas + data.queryId/warning。 */
    private Map<String, Object> queryData(String owner, Map<String, Object> params) {
        String viewName = firstString(params.get("viewName"));
        Map<String, Object> container = copy(load("analysis_view_query_data.json"));
        Map<String, Object> envelope = asMap(container.get(viewName));
        if (viewName == null || envelope.isEmpty()) {
            log.warn("[local-mock] analysisView/query 未命中视图 '{}'，返回 SM_02_0038", viewName);
            return accessDenied(envelope.isEmpty() ? container : envelope);
        }
        replacePlaceholder(envelope, OWNER_PLACEHOLDER, owner);
        Map<String, Object> data = asMap(envelope.get("data"));
        Map<String, Object> table = asMap(data.get("table"));
        Map<String, Object> columns = asMap(table.get("columns"));
        long total = longValue(data.get("total"), columnRowCount(columns));
        int pageSize = intValue(params.get("pageSize"), (int) total);
        int pageIndex = intValue(params.get("pageIndex"), 0);
        int from = Math.max(0, pageIndex * pageSize);
        int to = (int) Math.min(total, (long) from + pageSize);
        if (from > 0 || to < total) {
            table.put("columns", sliceColumns(columns, from, to));
        }
        table.put("total", total);
        table.put("pageSize", pageSize);
        table.put("pageIndex", pageIndex);
        data.put("total", total);
        return envelope;
    }

    /**
     * 指标数据查询：入参没有 viewName，按「请求的指标 + 维度」落在哪个视图上反查，
     * 再做列投影、筛选、limit/offset。与真实响应一致使用 {@code data.table.columns}。
     * <p>
     * 与正式契约一致：{@code filters} 必须是**表达式字符串数组**（如 {@code [region] = "华东"}）；
     * 传结构化 {@code {field,operator,value}} 时真实服务返回 {@code SM99002}，这里同样返回该
     * 业务码，避免本地假绿掩盖请求形状错误。
     */
    private Map<String, Object> metricsQuery(String owner, Map<String, Object> params) {
        List<String> expressions = filterExpressions(params.get("filters"));
        if (expressions == null) {
            log.warn("[local-mock] metrics_query 收到非表达式形态的 filters={}，按真实服务行为返回 SM99002",
                    params.get("filters"));
            return systemError("SM99002", "系统异常: filters 仅支持表达式字符串数组");
        }
        Map<String, Object> container = copy(load("analysis_view_query_data.json"));
        Map<String, Object> source = resolveViewForMetrics(container, params);
        replacePlaceholder(source, OWNER_PLACEHOLDER, owner);

        Map<String, Object> sourceData = asMap(source.get("data"));
        Map<String, Object> sourceTable = asMap(sourceData.get("table"));
        Map<String, Object> sourceColumns = asMap(sourceTable.get("columns"));

        Set<String> requested = new LinkedHashSet<>();
        requested.addAll(nameSet(params.get("dimensions")));
        requested.addAll(nameSet(params.get("metrics")));
        Map<String, Object> projected = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sourceColumns.entrySet()) {
            if (requested.isEmpty() || requested.contains(entry.getKey())) {
                projected.put(entry.getKey(), entry.getValue());
            }
        }

        List<Map<String, Object>> rows = toRows(projected);
        try {
            rows = filterRows(rows, expressions);
            String timeConstraint = firstString(params.get("timeConstraint"));
            if (timeConstraint != null) {
                rows = filterRows(rows, List.of(timeConstraint));
            }
            List<String> resultExpressions = filterExpressions(params.get("resultFilters"));
            if (resultExpressions == null) {
                return systemError(CODE_SYSTEM_ERROR, "系统异常: resultFilters 仅支持表达式字符串数组");
            }
            rows = filterRows(rows, resultExpressions);
        } catch (InvalidFilterExpressionException e) {
            log.warn("[local-mock] metrics_query 收到无法解析的筛选条件，按真实服务行为返回 SM99002");
            return systemError("SM99002", "系统异常: filters 表达式无法解析");
        }
        sortRows(rows, params.get("orders"));
        int totalBeforePaging = rows.size();
        int offset = intValue(params.get("offset"), 0);
        int limit = intValue(params.get("limit"), rows.size());
        if (offset > 0 || limit < rows.size()) {
            int end = Math.min(rows.size(), offset + Math.max(limit, 0));
            rows = offset >= rows.size() ? List.of() : rows.subList(offset, end);
        }

        Map<String, Object> columns = toColumns(rows, projected.keySet());
        List<Map<String, Object>> metas = new ArrayList<>();
        for (Map<String, Object> meta : asMapList(sourceData.get("metas"))) {
            if (requested.isEmpty() || requested.contains(firstString(meta.get("name")))) {
                metas.add(meta);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("table", Map.of("columns", columns, "total", rows.size()));
        data.put("metas", metas);
        data.put("total", Boolean.TRUE.equals(params.get("isQueryTotalCount")) ? totalBeforePaging : rows.size());
        String queryResultType = firstString(params.get("queryResultType"));
        data.put("queryResultType", queryResultType == null ? "DATA" : queryResultType);
        String sourceId = firstString(params.get("source"));
        if (sourceId != null) data.put("source", sourceId);
        if ("SQL".equalsIgnoreCase(queryResultType) || "SQL_AND_DATA".equalsIgnoreCase(queryResultType)) {
            data.put("sql", buildMockSql(requested, expressions, params));
        }
        return envelope(data, source.get("traceId"));
    }

    private void sortRows(List<Map<String, Object>> rows, Object rawOrders) {
        if (!(rawOrders instanceof Collection<?> orders) || orders.isEmpty()) return;
        List<OrderSpec> specs = new ArrayList<>();
        for (Object item : orders) {
            if (!(item instanceof Map<?, ?> map)) continue;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String field = firstString(entry.getKey());
                if (field != null) specs.add(new OrderSpec(field, "desc".equalsIgnoreCase(firstString(entry.getValue()))));
            }
        }
        Comparator<Map<String, Object>> comparator = null;
        for (OrderSpec spec : specs) {
            Comparator<Map<String, Object>> current = (left, right) -> {
                int result = compareValues(left.get(spec.field()), right.get(spec.field()));
                return spec.descending() ? -result : result;
            };
            comparator = comparator == null ? current : comparator.thenComparing(current);
        }
        if (comparator != null) rows.sort(comparator);
    }

    private int compareValues(Object left, Object right) {
        String leftText = left == null ? "" : String.valueOf(left);
        String rightText = right == null ? "" : String.valueOf(right);
        try {
            return Double.compare(Double.parseDouble(leftText), Double.parseDouble(rightText));
        } catch (NumberFormatException ignored) {
            return leftText.compareTo(rightText);
        }
    }

    private String buildMockSql(Set<String> requested, List<String> filters, Map<String, Object> params) {
        String select = requested.isEmpty() ? "*" : String.join(", ", requested);
        StringBuilder sql = new StringBuilder("SELECT ").append(select).append(" FROM mock_metrics");
        if (!filters.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", filters));
        if (params.get("orders") != null) sql.append(" ORDER BY ").append(params.get("orders"));
        return sql.toString();
    }

    private record OrderSpec(String field, boolean descending) {}

    // ------------------------------------------------------------------ 内部工具

    /** 反查：首个「列集合覆盖请求的指标 + 维度」的视图；都不覆盖时退回第一个视图。 */
    private Map<String, Object> resolveViewForMetrics(Map<String, Object> container, Map<String, Object> params) {
        Set<String> requested = new LinkedHashSet<>();
        requested.addAll(nameSet(params.get("dimensions")));
        requested.addAll(nameSet(params.get("metrics")));
        Map<String, Object> fallback = asMap(container.get(VIEW_ORDER.get(0)));
        if (requested.isEmpty()) {
            return fallback;
        }
        for (String view : VIEW_ORDER) {
            Map<String, Object> candidate = asMap(container.get(view));
            if (candidate.isEmpty()) continue;
            Set<String> available = asMap(asMap(asMap(candidate.get("data")).get("table")).get("columns")).keySet();
            if (available.containsAll(requested)) {
                return candidate;
            }
        }
        log.warn("[local-mock] metrics_query 请求字段 {} 未落在任一 mock 视图上，退回 {}", requested, VIEW_ORDER.get(0));
        return fallback;
    }

    /**
     * filters → 表达式字符串数组；元素不是字符串（结构化形态）时返回 null，
     * 由调用方按真实服务行为返回 {@code SM99002}。
     */
    private List<String> filterExpressions(Object filters) {
        List<String> expressions = new ArrayList<>();
        if (filters == null) return expressions;
        if (filters instanceof String single) {
            if (!single.isBlank()) expressions.add(single);
            return expressions;
        }
        if (!(filters instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (item == null) continue;
            if (!(item instanceof String expression)) return null;
            if (!expression.isBlank()) expressions.add(expression);
        }
        return expressions;
    }

    /** 按真实 Aloudata 筛选表达式过滤行：{@code [dim] = "值"} / {@code [dim] IN ("a","b")}，支持 AND。 */
    private List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, List<String> expressions) {
        for (String expression : expressions) {
            List<Condition> conditions = parseConditions(expression);
            if (conditions.isEmpty()) {
                throw new InvalidFilterExpressionException(expression);
            }
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                boolean hit = true;
                for (Condition condition : conditions) {
                    if (!condition.matches(row.get(condition.field()))) {
                        hit = false;
                        break;
                    }
                }
                if (hit) kept.add(row);
            }
            rows = kept;
        }
        return rows;
    }

    /**
     * 解析筛选表达式。仅覆盖本仓会生成的语法：
     * {@code [字段] 运算符 值}，多个条件用 {@code AND} 连接，允许整体与条件各自带括号。
     */
    private List<Condition> parseConditions(String expression) {
        List<Condition> conditions = new ArrayList<>();
        String normalized = expression.trim();
        while (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        for (String part : normalized.split("(?i)\\s+AND\\s+")) {
            String clause = part.trim();
            while (clause.startsWith("(") && clause.endsWith(")")) {
                clause = clause.substring(1, clause.length() - 1).trim();
            }
            if (clause.isBlank()) continue;
            if (clause.matches(".*(?i)\\s+OR\\s+.*")) {
                log.warn("[local-mock] 筛选表达式不支持 OR: {}", clause);
                throw new InvalidFilterExpressionException(expression);
            }
            Matcher matcher = CONDITION.matcher(clause);
            if (!matcher.matches()) {
                log.warn("[local-mock] 筛选表达式片段无法解析: {}", clause);
                throw new InvalidFilterExpressionException(expression);
            }
            conditions.add(new Condition(matcher.group(1), matcher.group(2).toUpperCase(Locale.ROOT),
                    unquote(matcher.group(3))));
        }
        return conditions;
    }

    private static final class InvalidFilterExpressionException extends RuntimeException {
        private InvalidFilterExpressionException(String expression) {
            super(expression);
        }
    }

    private List<String> unquote(String raw) {
        String text = raw.trim();
        if (text.startsWith("(") && text.endsWith(")")) {
            text = text.substring(1, text.length() - 1);
        }
        List<String> values = new ArrayList<>();
        for (String part : text.split(",")) {
            String value = part.trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isEmpty()) values.add(value.replace("\\\"", "\""));
        }
        return values;
    }

    /** 单个筛选条件：字段 + 运算符 + 取值列表（IN/NotIn 多个，其余取首个）。 */
    private record Condition(String field, String operator, List<String> values) {
        private boolean matches(Object actual) {
            String text = actual == null ? "" : String.valueOf(actual);
            return switch (operator) {
                case "=" -> values.size() == 1 && text.equals(values.get(0));
                case "<>" -> values.size() == 1 && !text.equals(values.get(0));
                case ">" -> compare(text, values) > 0;
                case ">=" -> compare(text, values) >= 0;
                case "<" -> compare(text, values) < 0;
                case "<=" -> compare(text, values) <= 0;
                case "IN" -> values.contains(text);
                case "NOTIN" -> !values.contains(text);
                default -> true;
            };
        }

        private int compare(String left, List<String> right) {
            if (right.isEmpty()) return 0;
            String other = right.get(0);
            try {
                return Double.compare(Double.parseDouble(left), Double.parseDouble(other));
            } catch (NumberFormatException e) {
                return left.compareTo(other);
            }
        }
    }

    /** 列式 → 行式（cell 为 {@code {value, flag, count}} 时取 value）。 */
    private List<Map<String, Object>> toRows(Map<String, Object> columns) {
        int size = (int) columnRowCount(columns);
        List<Map<String, Object>> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new LinkedHashMap<>());
        }
        for (Map.Entry<String, Object> entry : columns.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) continue;
            for (int i = 0; i < list.size(); i++) {
                Object cell = list.get(i);
                Object value = cell instanceof Map<?, ?> map && map.containsKey("value") ? map.get("value") : cell;
                rows.get(i).put(entry.getKey(), value);
            }
        }
        return rows;
    }

    /** 行式 → 列式（cell 为 {@code {value, flag, count}}）。 */
    private Map<String, Object> toColumns(List<Map<String, Object>> rows, Collection<String> columnNames) {
        Map<String, Object> columns = new LinkedHashMap<>();
        for (String name : columnNames) {
            List<Object> cells = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                cells.add(Map.of("value", row.get(name) == null ? "" : row.get(name), "flag", 0, "count", 1));
            }
            columns.put(name, cells);
        }
        return columns;
    }

    private Map<String, Object> sliceColumns(Map<String, Object> columns, int from, int to) {
        Map<String, Object> sliced = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : columns.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) continue;
            int end = Math.min(to, list.size());
            int start = Math.min(from, end);
            sliced.put(entry.getKey(), new ArrayList<>(list.subList(start, end)));
        }
        return sliced;
    }

    private long columnRowCount(Map<String, Object> columns) {
        long max = 0;
        for (Object column : columns.values()) {
            if (column instanceof List<?> list) max = Math.max(max, list.size());
        }
        return max;
    }

    /** 递归替换夹具里的 owner 占位符。 */
    private void replacePlaceholder(Object node, String placeholder, String value) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object child = entry.getValue();
                if (placeholder.equals(child)) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> mutable = (Map<Object, Object>) map;
                    mutable.put(entry.getKey(), value);
                } else {
                    replacePlaceholder(child, placeholder, value);
                }
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                replacePlaceholder(child, placeholder, value);
            }
        }
    }

    private Map<String, Object> unknownEndpoint(String endpointName) {
        // 真实环境打到未注册端点会 404；这里返回失败包络而不是空成功，避免把「端点写错」
        // 伪装成「查询无数据」——本地必须能暴露真机必炸的问题。
        log.warn("[local-mock] 未提供端点 {} 的 mock 报文，按真实行为返回失败包络", endpointName);
        return systemError("SM_04_0004", "未 mock 的端点: " + endpointName);
    }

    private Map<String, Object> systemError(String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", null);
        envelope.put("success", false);
        envelope.put("code", code);
        envelope.put("errorMsg", message);
        envelope.put("detailErrorMsg", message);
        envelope.put("traceId", "mock-trace-" + code);
        return envelope;
    }

    private Map<String, Object> accessDenied(Map<String, Object> template) {
        Object traceId = template.get("traceId");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", null);
        data.put("success", false);
        data.put("code", CODE_VIEW_ACCESS_DENIED);
        data.put("errorMsg", "用户&资源没有权限");
        data.put("detailErrorMsg", "用户&资源没有权限");
        data.put("traceId", traceId == null ? "mock-trace-access-denied" : traceId);
        return data;
    }

    private Map<String, Object> envelope(Object data, Object traceId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data);
        envelope.put("success", true);
        envelope.put("code", "200");
        envelope.put("errorMsg", null);
        envelope.put("detailErrorMsg", null);
        envelope.put("traceId", traceId == null ? "mock-trace" : traceId);
        return envelope;
    }

    /** 读取夹具并深拷贝为可变结构；下游会就地替换占位符/分页，故不能直接复用缓存。 */
    private Map<String, Object> load(String file) {
        Map<String, Object> raw = rawCache.computeIfAbsent(file, key -> {
            String location = BASE + key;
            try (InputStream in = new ClassPathResource(location).getInputStream()) {
                Map<String, Object> parsed = objectMapper.readValue(in, new TypeReference<Map<String, Object>>() {});
                log.info("[local-mock] 已装载 Aloudata 夹具 {}", location);
                return parsed;
            } catch (Exception e) {
                throw new IllegalStateException("本地 mock 夹具缺失或不可读: " + location, e);
            }
        });
        return copy(raw);
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return objectMapper.convertValue(source, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 取子节点 Map，**保留引用**而不是拷贝。
     * <p>
     * 上游 {@link #load(String)} 已通过 Jackson 生成全可变的嵌套结构，这里若再 convertValue
     * 会得到副本，导致 {@code data.put(...)}（列表过滤、列切片、total 改写）全部写丢。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {}));
            }
        }
        return out;
    }

    /** 参数可能是单值（QUERY 单次传入）或集合（Array 参数展开后的多值），两种都归一为集合。 */
    private Set<String> nameSet(Object value) {
        Set<String> names = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                String text = firstString(item);
                if (text != null) names.add(text);
            }
        } else {
            String text = firstString(value);
            if (text != null) names.add(text);
        }
        return names;
    }

    private String firstString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
