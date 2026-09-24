package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.service.impl.QueryPlannerImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Query Planner 契约测试（实施计划任务 2 步骤 6）：
 * 日期范围 gte/lt、多选/单选/数值范围/文本搜索、伪造字段、被删除的映射、
 * 默认排序与无排序、多数据集及脚本结果 residual、页大小超限、显式空集合短路。
 */
class QueryPlannerTest {

    private static final String QUERY_CONFIG_INPUT = """
            {
              "datasetId": "42",
              "inputName": "strategy_data",
              "queryConfig": {
                "displayFields": [
                  {"field": "metric_date", "title": "指标日期", "role": "dimension", "dataType": "date"},
                  {"field": "strategy_id", "title": "策略编码", "role": "dimension", "dataType": "string"},
                  {"field": "in_account", "title": "入金客户数", "role": "measure", "dataType": "number"}
                ],
                "parameterBindings": [
                  {"filterComponentId": "strategy_type", "parameterName": "strategy_ids", "field": "strategy_id", "operator": "in"},
                  {"filterComponentId": "date_range", "parameterName": "start_date", "field": "metric_date", "operator": "gte"},
                  {"filterComponentId": "date_range", "parameterName": "end_date", "field": "metric_date", "operator": "lt"},
                  {"filterComponentId": "amount_range", "parameterName": "min_amount", "field": "in_account", "operator": "gte"},
                  {"filterComponentId": "name_search", "parameterName": "keyword", "field": "strategy_id", "operator": "contains"}
                ],
                "sortPolicy": {"enabled": true, "mode": "single", "allowedFields": ["in_account"], "defaultSort": null},
                "paginationPolicy": {"enabled": true, "defaultPageSize": 100, "maxPageSize": 500, "returnTotalCount": true}
              }
            }
            """;

    private static final String COMPONENT_WITH_BOUND_FILTERS = """
            {
              "id": "component-001",
              "config": {
                "datasetPipeline": {
                  "boundFilterComponentIds": ["strategy_type", "date_range", "amount_range", "name_search"],
                  "datasetInputs": [%s]
                }
              }
            }
            """;

    private static final String LEGACY_COMPONENT = """
            {
              "id": "component-001",
              "config": {
                "datasetPipeline": {
                  "datasetInputs": [{"datasetId": "42", "inputName": "strategy_data"}],
                  "scriptFilterBindings": [
                    {
                      "filterComponentId": "strategy_type",
                      "inputNames": ["strategy_data"],
                      "fieldMappings": {"strategy_data": "strategy_id"},
                      "conditions": [
                        {"inputName": "strategy_data", "field": "strategy_id", "operator": "in", "parameterNames": ["strategy_ids"]}
                      ]
                    }
                  ]
                }
              }
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private QueryPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new QueryPlannerImpl();
    }

    private JsonNode component(String inputJson) throws Exception {
        return mapper.readTree(COMPONENT_WITH_BOUND_FILTERS.formatted(inputJson));
    }

    private JsonNode input(JsonNode component) {
        return component.at("/config/datasetPipeline/datasetInputs/0");
    }

    private QueryContextDTO context(Map<String, Object> parameters) {
        return new QueryContextDTO("dashboard-001", "component-001", null, parameters, null, null, "run-test");
    }

    private QueryContextDTO context(Map<String, Object> parameters,
                                    QueryContextDTO.SortSpec sort,
                                    QueryContextDTO.PaginationSpec pagination) {
        return new QueryContextDTO("dashboard-001", "component-001", null, parameters, sort, pagination, "run-test");
    }

    @Test
    @DisplayName("日期范围按绑定展开为 gte/lt 半开区间")
    void dateRangeExpandsToGteLt() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A", "B"));
        parameters.put("start_date", "2026-09-01");
        parameters.put("end_date", "2026-10-01");
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(parameters), false);

        assertThat(plan.filters()).containsExactly(
                new DatasetQueryPlanDTO.FilterSpec("strategy_id", "in", List.of("A", "B")),
                new DatasetQueryPlanDTO.FilterSpec("metric_date", "gte", "2026-09-01"),
                new DatasetQueryPlanDTO.FilterSpec("metric_date", "lt", "2026-10-01"));
        assertThat(plan.columns()).containsExactly("metric_date", "strategy_id", "in_account");
        assertThat(plan.pushdown().filters()).isTrue();
        assertThat(plan.shortCircuitEmpty()).isFalse();
    }

    @Test
    @DisplayName("单选 eq 与数值范围、文本搜索按各自运算符下推")
    void singleSelectNumericRangeAndContains() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT.replace("\"operator\": \"in\"", "\"operator\": \"eq\"")
                .replace("{\"filterComponentId\": \"amount_range\", \"parameterName\": \"min_amount\", \"field\": \"in_account\", \"operator\": \"gte\"},", ""));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", "A");
        parameters.put("keyword", "策略");
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(parameters), false);

        assertThat(plan.filters()).containsExactlyInAnyOrder(
                new DatasetQueryPlanDTO.FilterSpec("strategy_id", "eq", "A"),
                new DatasetQueryPlanDTO.FilterSpec("strategy_id", "contains", "策略"));
    }

    @Test
    @DisplayName("数值范围绑定生成 gte 过滤")
    void numericRangeBinding() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT.replace(
                "{\"filterComponentId\": \"date_range\", \"parameterName\": \"start_date\", \"field\": \"metric_date\", \"operator\": \"gte\"},",
                "{\"filterComponentId\": \"amount_range\", \"parameterName\": \"min_amount\", \"field\": \"in_account\", \"operator\": \"gte\"},"));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A", "B"));
        parameters.put("min_amount", 100.5);
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(parameters), false);

        assertThat(plan.filters()).contains(
                new DatasetQueryPlanDTO.FilterSpec("in_account", "gte", 100.5));
    }

    @Test
    @DisplayName("伪造字段（绑定字段不在注册表）在 Planner 阶段失败")
    void forgedFieldFails() throws Exception {
        String badInput = QUERY_CONFIG_INPUT.replace(
                "{\"filterComponentId\": \"name_search\", \"parameterName\": \"keyword\", \"field\": \"strategy_id\", \"operator\": \"contains\"}",
                "{\"filterComponentId\": \"name_search\", \"parameterName\": \"keyword\", \"field\": \"hacked_field\", \"operator\": \"contains\"}");
        JsonNode component = component(badInput);
        assertThatThrownBy(() -> planner.plan(component, input(component), context(Map.of()), false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.FIELD_NOT_ALLOWED));
    }

    @Test
    @DisplayName("筛选字段可独立于展示字段：隐藏字段参与过滤但不进入输出列")
    void hiddenQueryableFieldCanFilterWithoutBeingProjected() throws Exception {
        String inputJson = """
                {
                  "datasetId": "42",
                  "inputName": "strategy_data",
                  "queryConfig": {
                    "displayFields": [
                      {"field": "strategy_id", "title": "策略编码", "role": "dimension"}
                    ],
                    "queryableFields": [
                      {"name": "strategy_id", "role": "dimension"},
                      {"name": "created_by", "displayName": "创建人", "role": "dimension"}
                    ],
                    "parameterBindings": [
                      {"filterComponentId": "strategy_type", "parameterName": "creator", "field": "created_by", "operator": "eq"}
                    ],
                    "sortPolicy": {"enabled": false, "allowedFields": []},
                    "paginationPolicy": {"enabled": false}
                  }
                }
                """;
        JsonNode component = component(inputJson);
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(Map.of("creator", "Alice")), false);

        assertThat(plan.columns()).containsExactly("strategy_id");
        assertThat(plan.filters()).containsExactly(
                new DatasetQueryPlanDTO.FilterSpec("created_by", "eq", "Alice"));
    }

    @Test
    @DisplayName("被删除的筛选器映射在 Planner 阶段失败")
    void deletedFilterMappingFails() throws Exception {
        // boundFilterComponentIds 移除 amount_range（模拟筛选器被删）
        String componentJson = COMPONENT_WITH_BOUND_FILTERS.formatted(QUERY_CONFIG_INPUT)
                .replace(", \"amount_range\"", "");
        JsonNode component = mapper.readTree(componentJson);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A"));
        parameters.put("min_amount", 1);
        assertThatThrownBy(() -> planner.plan(component, input(component), context(parameters), false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.FIELD_NOT_ALLOWED));
    }

    @Test
    @DisplayName("客户端伪造的参数键被拒绝")
    void forgedParameterRejected() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A"));
        parameters.put("evil_field", "x");
        assertThatThrownBy(() -> planner.plan(component, input(component), context(parameters), false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID));
    }

    @Test
    @DisplayName("无排序与默认排序：无 sort 时使用 defaultSort，无 defaultSort 则无 orders")
    void defaultSortAndNoSort() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        DatasetQueryPlanDTO noSort = planner.plan(component, input(component), context(Map.of()), false);
        assertThat(noSort.orders()).isEmpty();
        assertThat(noSort.pushdown().sort()).isFalse();

        String withDefault = QUERY_CONFIG_INPUT.replace(
                "\"defaultSort\": null",
                "\"defaultSort\": {\"field\": \"in_account\", \"direction\": \"asc\"}");
        JsonNode defaultComponent = component(withDefault);
        DatasetQueryPlanDTO defaultPlan = planner.plan(defaultComponent, input(defaultComponent), context(Map.of()), false);
        assertThat(defaultPlan.orders()).containsExactly(new DatasetQueryPlanDTO.OrderSpec("in_account", "asc"));
        assertThat(defaultPlan.pushdown().sort()).isTrue();
    }

    @Test
    @DisplayName("排序字段不在 allowedSortFields 时 SORT_NOT_ALLOWED")
    void sortNotAllowedFails() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        QueryContextDTO context = context(Map.of(),
                new QueryContextDTO.SortSpec("metric_date", "asc"), null);
        assertThatThrownBy(() -> planner.plan(component, input(component), context, false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.SORT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("有 Python 脚本时最终排序/分页置入结果集阶段并带读取上限")
    void scriptResidualSortAndPagination() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A", "B"));
        QueryContextDTO context = context(parameters,
                new QueryContextDTO.SortSpec("in_account", "desc"),
                new QueryContextDTO.PaginationSpec(2, 100));
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context, true);

        assertThat(plan.pushdown().sort()).isFalse();
        assertThat(plan.pushdown().pagination()).isFalse();
        assertThat(plan.orders()).containsExactly(new DatasetQueryPlanDTO.OrderSpec("in_account", "desc"));
        assertThat(plan.residualOperations()).containsExactlyInAnyOrder("SORT_AT_RESULT_STAGE", "PAGINATION_AT_RESULT_STAGE");
        assertThat(plan.readLimit()).isNotNull();
        // 输入级筛选仍然下推
        assertThat(plan.filters()).isNotEmpty();
        assertThat(plan.pushdown().filters()).isTrue();
    }

    @Test
    @DisplayName("无脚本单数据集第 2 页每页 100 条允许下推分页")
    void paginationPushdownForSingleDataset() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        QueryContextDTO context = context(Map.of(), null, new QueryContextDTO.PaginationSpec(2, 100));
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context, false);

        assertThat(plan.pushdown().pagination()).isTrue();
        assertThat(plan.pagination()).isEqualTo(new QueryContextDTO.PaginationSpec(2, 100));
        assertThat(plan.pagination().offset()).isEqualTo(100); // 固定样例：第 2 页 → offset=100
        assertThat(plan.residualOperations()).isEmpty();
    }

    @Test
    @DisplayName("页大小超过 maxPageSize（min 500）时 PAGE_SIZE_EXCEEDED")
    void pageSizeExceeded() throws Exception {
        // 501 超过服务端硬上限：DTO 构造即拒绝
        assertThatThrownBy(() -> new QueryContextDTO.PaginationSpec(1, 501))
                .isInstanceOf(IllegalArgumentException.class);

        // 配置收窄：maxPageSize=200 时 300 虽在硬上限内也被 Planner 拒绝
        String smallerMax = QUERY_CONFIG_INPUT.replace("\"maxPageSize\": 500", "\"maxPageSize\": 200");
        JsonNode smaller = component(smallerMax);
        QueryContextDTO withinHardCap = context(Map.of(), null, new QueryContextDTO.PaginationSpec(1, 300));
        assertThatThrownBy(() -> planner.plan(smaller, input(smaller), withinHardCap, false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.PAGE_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("显式空集合短路为空结果，不访问数据源；清空/null 不过滤")
    void explicitEmptyCollectionShortCircuits() throws Exception {
        JsonNode component = component(QUERY_CONFIG_INPUT);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of()); // 显式空集合
        parameters.put("start_date", "2026-09-01");
        parameters.put("end_date", "2026-10-01");
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(parameters), false);

        assertThat(plan.shortCircuitEmpty()).isTrue();
        assertThat(plan.filters()).isEmpty();
        assertThat(plan.pushdown().filters()).isFalse();

        // 清空（null 值）= 无过滤，正常执行
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("strategy_ids", null);
        DatasetQueryPlanDTO clearedPlan = planner.plan(component, input(component), context(cleared), false);
        assertThat(clearedPlan.shortCircuitEmpty()).isFalse();
        assertThat(clearedPlan.filters()).isEmpty();
    }

    @Test
    @DisplayName("旧 Schema：无 queryConfig 时归一化 scriptFilterBindings")
    void legacyScriptFilterBindingsNormalized() throws Exception {
        JsonNode component = mapper.readTree(LEGACY_COMPONENT);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("strategy_ids", List.of("A", "B"));
        DatasetQueryPlanDTO plan = planner.plan(component, input(component), context(parameters), false);

        assertThat(plan.inputName()).isEqualTo("strategy_data");
        assertThat(plan.filters()).containsExactly(
                new DatasetQueryPlanDTO.FilterSpec("strategy_id", "in", List.of("A", "B")));
        assertThat(plan.columns()).isEmpty(); // 旧 Schema 无字段注册表 → 不限制列
        // 旧 Schema 无分页策略 → 运行时分页请求被拒绝
        QueryContextDTO withPagination = context(Map.of(), null, new QueryContextDTO.PaginationSpec(1, 100));
        assertThatThrownBy(() -> planner.plan(component, input(component), withPagination, false))
                .isInstanceOfSatisfying(QueryPlanException.class, e ->
                        assertThat(e.getCode()).isEqualTo(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID));
    }

    @Test
    @DisplayName("QueryContextDTO 校验：page>=1、pageSize<=500、排序方向、requestId 长度")
    void queryContextValidation() {
        assertThatThrownBy(() -> new QueryContextDTO.PaginationSpec(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryContextDTO.PaginationSpec(1, 501))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryContextDTO.SortSpec("f", "sideways"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryContextDTO("d", "c", null, Map.of(), null, null, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        // 合法边界
        assertThat(new QueryContextDTO.PaginationSpec(1, 500).offset()).isEqualTo(0);
        assertThat(new QueryContextDTO.SortSpec("f", "DESC").direction()).isEqualTo("desc");
    }
}
