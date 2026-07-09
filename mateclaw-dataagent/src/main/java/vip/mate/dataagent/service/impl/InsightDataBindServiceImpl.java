package vip.mate.dataagent.service.impl;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.dto.DashboardFilterContextDTO;
import vip.mate.dataagent.dto.DashboardFilterContextDTO.FilterValue;
import vip.mate.dataagent.dto.DashboardFilterContextDTO.TimeRangeValue;
import vip.mate.dataagent.dto.InsightComponentDataDTO;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO.Component;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO.DataSource;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.InsightDataBindService;
import vip.mate.dataagent.util.InsightChartOptionHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 洞察仪表盘数据绑定服务实现
 * <p>
 * 加载仪表盘 Schema，遍历每个组件的数据源配置，调用 {@link AloudataService#queryMetrics}
 * 获取指标数据，再通过 {@link InsightChartOptionHelper} 生成 ECharts option / KPI 卡片 / 表格数据。
 * <p>
 * 组件级错误隔离：单个组件取数失败不影响其他组件，失败时返回带 error 字段的降级数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightDataBindServiceImpl implements InsightDataBindService {

    private final InsightDashboardService dashboardService;
    private final AloudataService aloudataService;

    /** KPI 环比变化格式化模板 */
    private static final String KPI_CHANGE_FORMAT = "%.2f%%";

    @Override
    public List<InsightComponentDataDTO> bindDashboard(Long dashboardId) {
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
        InsightDashboardSchemaDTO schema = parseSchema(dashboard.getSchemaJson());
        if (schema == null || schema.getComponents() == null) {
            return Collections.emptyList();
        }
        return schema.getComponents().stream()
                .map(this::bindComponent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public InsightComponentDataDTO bindComponent(Component component) {
        // filter / timeFilter 类型无需取数
        if ("filter".equals(component.getType()) || "timeFilter".equals(component.getType())) {
            return null;
        }
        // 校验 dataSource
        DataSource ds = component.getDataSource();
        if (ds == null || ds.getDatasourceId() == null || ds.getDatasourceId().isBlank()) {
            return buildError(component.getId(), "组件未配置数据源");
        }
        try {
            return doBind(component);
        } catch (Exception e) {
            log.warn("组件 {} 取数失败: {}", component.getId(), e.getMessage());
            return buildError(component.getId(), e.getMessage());
        }
    }

    @Override
    public List<InsightComponentDataDTO> previewData(Long dashboardId) {
        return bindDashboard(dashboardId);
    }

    @Override
    public List<InsightComponentDataDTO> previewData(Long dashboardId, DashboardFilterContextDTO filterContext) {
        // 无筛选上下文时退化为普通预览
        if (filterContext == null) {
            return previewData(dashboardId);
        }
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
        InsightDashboardSchemaDTO schema = parseSchema(dashboard.getSchemaJson());
        if (schema == null || schema.getComponents() == null) {
            return Collections.emptyList();
        }
        String sourceFilterId = filterContext.getSourceFilterId();
        return schema.getComponents().stream()
                .map(component -> {
                    // filter/timeFilter 组件无需取数
                    if ("filter".equals(component.getType()) || "timeFilter".equals(component.getType())) {
                        return null;
                    }
                    // 作用范围判断
                    if (sourceFilterId != null && !sourceFilterId.isBlank()) {
                        // 组件绑定筛选器：仅影响绑定了该筛选器的组件
                        List<String> boundIds = component.getBoundFilterIds();
                        if (boundIds == null || !boundIds.contains(sourceFilterId)) {
                            // 该组件未绑定此筛选器，不受影响，返回 null（不重新取数）
                            return null;
                        }
                    } else {
                        // 全局筛选器：仅影响未绑定任何专属筛选器的组件
                        List<String> boundIds = component.getBoundFilterIds();
                        if (boundIds != null && !boundIds.isEmpty()) {
                            // 该组件已绑定专属筛选器，不受全局筛选器影响
                            return null;
                        }
                    }
                    return bindComponentWithFilters(component, filterContext);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 绑定单个组件数据（合并运行时筛选条件）
     * <p>
     * 运行时筛选上下文分为两类，分别对应 Aloudata API 的不同参数：
     * <ul>
     *   <li>时间范围（timeRange）→ 转换为 timeConstraint 表达式（API 5.6 节）</li>
     *   <li>维度筛选（dimensionFilters）→ 转换为 filters 表达式字符串数组（API 5.4 节）</li>
     * </ul>
     */
    private InsightComponentDataDTO bindComponentWithFilters(Component component,
                                                              DashboardFilterContextDTO filterContext) {
        if ("filter".equals(component.getType()) || "timeFilter".equals(component.getType())) {
            return null;
        }
        DataSource ds = component.getDataSource();
        if (ds == null || ds.getDatasourceId() == null || ds.getDatasourceId().isBlank()) {
            return buildError(component.getId(), "组件未配置数据源");
        }
        try {
            return doBind(component, filterContext);
        } catch (Exception e) {
            log.warn("组件 {} 带筛选取数失败: {}", component.getId(), e.getMessage());
            return buildError(component.getId(), e.getMessage());
        }
    }

    /**
     * 执行单组件取数与渲染数据构建
     */
    private InsightComponentDataDTO doBind(Component component) {
        return doBind(component, null);
    }

    /**
     * 执行单组件取数与渲染数据构建（支持运行时筛选上下文）
     * <p>
     * 查询参数构建顺序：
     * <ol>
     *   <li>filters：静态 filters（Map 结构）转换为表达式字符串 + 运行时维度筛选转换为表达式字符串</li>
     *   <li>timeConstraint：运行时时间范围优先，其次静态配置的 timeConstraint</li>
     * </ol>
     *
     * @param component      组件定义
     * @param filterContext  运行时筛选上下文（可为 null，表示无运行时筛选）
     */
    private InsightComponentDataDTO doBind(Component component, DashboardFilterContextDTO filterContext) {
        DataSource ds = component.getDataSource();
        Long datasourceId = Long.parseLong(ds.getDatasourceId());

        // 1. 构建 AloudataMetricQueryRequest
        AloudataMetricQueryRequest request = new AloudataMetricQueryRequest();
        request.setMetrics(ds.getMetrics());
        request.setDimensions(ds.getDimensions());
        request.setLimit(ds.getLimit() != null ? ds.getLimit() : 100);

        // 2. 构建 filters 表达式字符串数组（API 5.4 节）
        List<String> filterExpressions = new ArrayList<>(convertStaticFilters(ds.getFilters()));
        if (filterContext != null && filterContext.getDimensionFilters() != null) {
            for (FilterValue fv : filterContext.getDimensionFilters()) {
                String expr = buildDimensionFilterExpression(fv);
                if (expr != null) {
                    filterExpressions.add(expr);
                }
            }
        }
        request.setFilters(filterExpressions);

        // 3. 构建 timeConstraint 表达式（API 5.6 节）：运行时时间范围优先，其次静态配置
        String timeConstraint = null;
        if (filterContext != null && filterContext.getTimeRange() != null) {
            timeConstraint = buildTimeConstraint(filterContext.getTimeRange());
        }
        if (timeConstraint == null && ds.getTimeConstraint() != null && !ds.getTimeConstraint().isBlank()) {
            timeConstraint = ds.getTimeConstraint();
        }
        request.setTimeConstraint(timeConstraint);

        // 4. 调用 Aloudata 查询
        AloudataMetricQueryResponse response = aloudataService.queryMetrics(datasourceId, request);
        if (response == null || response.getData() == null) {
            return buildError(component.getId(), "查询无数据");
        }

        // 3. 转换响应格式：列式 columns → 行式 List<String> + List<List<String>>
        List<String> columns;
        List<List<String>> rows;

        AloudataMetricQueryResponse.MetricData metricData = response.getData();
        if (metricData.getColumns() != null && !metricData.getColumns().isEmpty()) {
            // 列式存储：从 columns 转换
            columns = new ArrayList<>(metricData.getColumns().keySet());
            rows = convertFromColumnar(metricData.getColumns(), columns);
        } else if (metricData.getRows() != null && !metricData.getRows().isEmpty()) {
            // 行式存储：兼容 rows 不为 null 的情况
            columns = extractColumnNames(metricData.getRows().get(0));
            rows = convertRows(metricData.getRows(), columns);
        } else {
            return buildError(component.getId(), "查询无数据");
        }

        // 4. 按组件类型生成渲染数据
        return buildByComponentType(component, columns, rows);
    }

    /**
     * 按组件类型生成渲染数据
     */
    private InsightComponentDataDTO buildByComponentType(Component component,
                                                          List<String> columns,
                                                          List<List<String>> rows) {
        InsightComponentDataDTO dto = new InsightComponentDataDTO();
        dto.setComponentId(component.getId());

        switch (component.getType()) {
            case "kpi" -> {
                dto.setRenderType(DataAgentConstants.INSIGHT_RENDER_TYPE_KPI);
                dto.setKpi(buildKpiData(component, columns, rows));
            }
            case "chart" -> {
                dto.setRenderType(DataAgentConstants.INSIGHT_RENDER_TYPE_ECHARTS);
                String chartType = component.getChartType() != null
                        ? component.getChartType()
                        : DataAgentConstants.CHART_TYPE_LINE;
                String optionJson = InsightChartOptionHelper.buildByType(chartType, columns, rows);
                if (optionJson != null) {
                    dto.setOption(JSONUtil.parseObj(optionJson));
                }
            }
            case "table" -> {
                dto.setRenderType(DataAgentConstants.INSIGHT_RENDER_TYPE_TABLE);
                String tableJson = InsightChartOptionHelper.buildTable(columns, rows);
                dto.setTable(JSONUtil.toBean(tableJson, InsightComponentDataDTO.TableData.class));
            }
            default -> dto.setRenderType(DataAgentConstants.INSIGHT_RENDER_TYPE_TABLE);
        }
        return dto;
    }

    /**
     * 构建 KPI 卡片数据
     * <p>
     * 取第一个数值列作为指标值列；若有多行数据，取最后两行计算环比变化。
     */
    private InsightComponentDataDTO.KpiData buildKpiData(Component component,
                                                          List<String> columns,
                                                          List<List<String>> rows) {
        DataSource ds = component.getDataSource();
        String metricName = (ds.getMetrics() != null && !ds.getMetrics().isEmpty())
                ? ds.getMetrics().get(0)
                : component.getTitle();

        int metricColIdx = findFirstNumericColumn(columns, rows);
        if (metricColIdx < 0 || rows.isEmpty()) {
            return buildEmptyKpi(metricName);
        }

        String currentValue = rows.get(rows.size() - 1).get(metricColIdx);

        // 单行数据：仅展示当前值
        if (rows.size() < 2) {
            InsightComponentDataDTO.KpiData kpi = new InsightComponentDataDTO.KpiData();
            kpi.setName(metricName);
            kpi.setValue(currentValue);
            return kpi;
        }

        // 多行数据：取最后两行计算环比
        String previousValue = rows.get(rows.size() - 2).get(metricColIdx);
        String change = computeChange(previousValue, currentValue);
        boolean up = isUp(previousValue, currentValue);

        InsightComponentDataDTO.KpiData kpi = new InsightComponentDataDTO.KpiData();
        kpi.setName(metricName);
        kpi.setValue(currentValue);
        kpi.setChg(change);
        kpi.setUp(up);
        return kpi;
    }

    /**
     * 从列式数据转换为行式数据
     * <p>
     * Aloudata 返回格式：columns = {"AUM": [{value: 7726, ...}, ...], "metric_time": [{value: "2024-01-12", ...}, ...]}
     * 转换为：rows = [["7726", "2024-01-12"], ["6747", "2024-01-11"], ...]
     */
    private List<List<String>> convertFromColumnar(
            Map<String, List<AloudataMetricQueryResponse.ColumnValue>> columnar,
            List<String> columnNames) {
        // 确定行数（取第一个列的长度）
        int rowCount = columnar.values().stream()
                .filter(v -> v != null && !v.isEmpty())
                .mapToInt(List::size)
                .findFirst()
                .orElse(0);
        if (rowCount == 0) {
            return Collections.emptyList();
        }
        List<List<String>> result = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            List<String> row = new ArrayList<>(columnNames.size());
            for (String colName : columnNames) {
                List<AloudataMetricQueryResponse.ColumnValue> colValues = columnar.get(colName);
                Object val = (colValues != null && i < colValues.size())
                        ? colValues.get(i).getValue() : null;
                row.add(val != null ? val.toString() : "");
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 从行式 Map 提取列名
     */
    private List<String> extractColumnNames(Map<String, Object> firstRow) {
        if (firstRow == null || firstRow.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(firstRow.keySet());
    }

    /**
     * 将行数据从 Map 格式转为 List 格式（按列顺序提取值）
     */
    private List<List<String>> convertRows(List<Map<String, Object>> rows, List<String> columns) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            List<String> rowList = new ArrayList<>(columns.size());
            for (String colName : columns) {
                Object val = row.get(colName);
                rowList.add(val != null ? val.toString() : "");
            }
            result.add(rowList);
        }
        return result;
    }

    /**
     * 找到第一个数值列的索引
     * <p>
     * 遍历每列，尝试将第二行（索引 1）的值解析为 BigDecimal，成功则判定为数值列。
     */
    private int findFirstNumericColumn(List<String> columns, List<List<String>> rows) {
        if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty()) {
            return -1;
        }
        int sampleRowIdx = Math.min(1, rows.size() - 1);
        for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
            String val = rows.get(sampleRowIdx).get(colIdx);
            if (isNumeric(val)) {
                return colIdx;
            }
        }
        return -1;
    }

    /**
     * 计算环比变化百分比
     */
    private String computeChange(String previous, String current) {
        try {
            BigDecimal prev = new BigDecimal(previous);
            BigDecimal curr = new BigDecimal(current);
            if (prev.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            BigDecimal change = curr.subtract(prev)
                    .divide(prev, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            return String.format(KPI_CHANGE_FORMAT, change);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否上升
     */
    private boolean isUp(String previous, String current) {
        try {
            return new BigDecimal(current).compareTo(new BigDecimal(previous)) >= 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 判断字符串是否为数值
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 构建空 KPI 卡片（无数据时降级）
     */
    private InsightComponentDataDTO.KpiData buildEmptyKpi(String metricName) {
        InsightComponentDataDTO.KpiData kpi = new InsightComponentDataDTO.KpiData();
        kpi.setName(metricName);
        kpi.setValue("--");
        return kpi;
    }

    /**
     * 构建错误降级数据
     */
    private InsightComponentDataDTO buildError(String componentId, String errorMsg) {
        InsightComponentDataDTO dto = new InsightComponentDataDTO();
        dto.setComponentId(componentId);
        dto.setRenderType(DataAgentConstants.INSIGHT_RENDER_TYPE_TABLE);
        dto.setError(errorMsg);
        return dto;
    }

    /**
     * 将静态 filters（Map 结构）转换为 Aloudata 表达式字符串列表
     * <p>
     * Schema 中 filters 保留结构化格式便于前端编辑器交互，构建查询请求时转换为
     * API 5.4 节要求的表达式字符串。Map 约定结构：
     * <ul>
     *   <li>field: 维度名</li>
     *   <li>operator: 操作符（in / = / != / between 等）</li>
     *   <li>value: 单值或多值数组</li>
     * </ul>
     * 转换示例：
     * <ul>
     *   <li>{field:"province", operator:"in", value:["浙江省","江苏省"]} → [province] IN ("浙江省","江苏省")</li>
     *   <li>{field:"product_id", operator:"=", value:13} → [product_id] = 13</li>
     *   <li>{field:"metric_time__day", operator:"between", value:["2024-01-01","2024-01-31"]} → [metric_time__day] BETWEEN ("2024-01-01","2024-01-31")</li>
     * </ul>
     *
     * @param staticFilters Schema 中结构化 filters，可为 null
     * @return 表达式字符串列表，空列表表示无筛选
     */
    private List<String> convertStaticFilters(List<Map<String, Object>> staticFilters) {
        List<String> expressions = new ArrayList<>();
        if (staticFilters == null || staticFilters.isEmpty()) {
            return expressions;
        }
        for (Map<String, Object> filter : staticFilters) {
            String expr = buildFilterExpression(
                    (String) filter.get(DataAgentConstants.INSIGHT_FILTER_KEY_FIELD),
                    (String) filter.get(DataAgentConstants.INSIGHT_FILTER_KEY_OPERATOR),
                    filter.get(DataAgentConstants.INSIGHT_FILTER_KEY_VALUE)
            );
            if (expr != null) {
                expressions.add(expr);
            }
        }
        return expressions;
    }

    /**
     * 构建维度筛选表达式字符串（运行时筛选值）
     * <p>
     * 运行时筛选值统一使用 IN 操作符（单值也用 IN，保持一致性）。
     *
     * @param filterValue 运行时维度筛选值
     * @return 表达式字符串，例如 [province] IN ("浙江省","江苏省")；无效输入返回 null
     */
    private String buildDimensionFilterExpression(FilterValue filterValue) {
        if (filterValue.getField() == null || filterValue.getField().isBlank()) {
            return null;
        }
        return buildFilterExpression(
                filterValue.getField(),
                DataAgentConstants.INSIGHT_FILTER_OP_IN,
                filterValue.getValue()
        );
    }

    /**
     * 构建 filters 表达式字符串
     * <p>
     * 根据操作符和值类型生成符合 Aloudata API 5.4 节规范的表达式：
     * <ul>
     *   <li>IN 操作符 + 数组值：[field] IN ("v1","v2") 或 [field] IN (1,2)</li>
     *   <li>IN 操作符 + 单值：[field] IN ("v1")</li>
     *   <li>= / != 操作符：[field] = "v1" 或 [field] = 13</li>
     *   <li>between 操作符：[field] BETWEEN ("start","end")</li>
     * </ul>
     * 数值类型不加引号，字符串类型加双引号。
     */
    private String buildFilterExpression(String field, String operator, Object value) {
        if (field == null || field.isBlank() || operator == null || value == null) {
            return null;
        }
        String op = operator.toLowerCase();
        String formattedValue = formatFilterValue(value, op);
        if (formattedValue == null) {
            return null;
        }
        // in → IN，between → BETWEEN，= / != 保持原样
        String opUpper = op.equals("in") ? "IN" : op.equals("between") ? "BETWEEN" : op;
        return "[" + field + "] " + opUpper + " " + formattedValue;
    }

    /**
     * 格式化筛选值为表达式片段
     * <p>
     * IN 操作符：数组值 → ("v1","v2")，单值 → ("v1")
     * between 操作符：数组值 → ("v1","v2")
     * 其他操作符（=、!= 等）：单值 → "v1"（字符串）或 13（数值）
     */
    @SuppressWarnings("unchecked")
    private String formatFilterValue(Object value, String op) {
        if (value instanceof List) {
            List<Object> values = (List<Object>) value;
            if (values.isEmpty()) {
                return null;
            }
            String joined = values.stream()
                    .map(this::quoteIfNeeded)
                    .collect(Collectors.joining(","));
            return "(" + joined + ")";
        }
        // 单值
        if ("in".equals(op) || "between".equals(op)) {
            return "(" + quoteIfNeeded(value) + ")";
        }
        return quoteIfNeeded(value);
    }

    /**
     * 数值不加引号，其他类型加双引号
     */
    private String quoteIfNeeded(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String str = value.toString();
        if (isNumeric(str)) {
            return str;
        }
        return "\"" + str.replace("\"", "\\\"") + "\"";
    }

    /**
     * 根据时间范围预设构建 timeConstraint 表达式（API 5.6 节）
     * <p>
     * 参考文档示例和生成的 SQL，timeConstraint 使用语义层表达式格式：
     * <ul>
     *   <li>自定义日期：使用 &gt;= 和 &lt; 字面量比较，例如
     *       ([metric_time__day] &gt;= "2024-01-01" AND [metric_time__day] &lt; "2024-02-01")</li>
     * </ul>
     * <p>
     * 注意：结束日期需要 +1 天，使用半开区间 [start, end+1)，
     * 因为 Aloudata 的指标日期是 DATETIME 类型，"2024-01-31" 实际代表当天零点，
     * 需要取到 &lt; "2024-02-01" 才能包含 1 月 31 日的数据。
     *
     * @param timeRange 时间范围筛选值
     * @return timeConstraint 表达式字符串，无效预设时返回 null
     */
    private String buildTimeConstraint(TimeRangeValue timeRange) {
        String preset = timeRange.getPreset();
        if (preset == null || preset.isBlank()) {
            return null;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate end = LocalDate.now();
        LocalDate start;
        switch (preset) {
            case DataAgentConstants.INSIGHT_TIME_PRESET_TODAY -> start = end;
            case DataAgentConstants.INSIGHT_TIME_PRESET_7D -> start = end.minusDays(6);
            case DataAgentConstants.INSIGHT_TIME_PRESET_30D -> start = end.minusDays(29);
            case DataAgentConstants.INSIGHT_TIME_PRESET_90D -> start = end.minusDays(89);
            case DataAgentConstants.INSIGHT_TIME_PRESET_CUSTOM -> {
                if (timeRange.getStart() == null || timeRange.getEnd() == null) {
                    return null;
                }
                start = LocalDate.parse(timeRange.getStart(), fmt);
                end = LocalDate.parse(timeRange.getEnd(), fmt);
            }
            default -> {
                return null;
            }
        }
        // 结束日期 +1 天，使用半开区间 [start, end+1) 以包含结束日期当天的全部数据
        LocalDate endExclusive = end.plusDays(1);
        return "([metric_time__day] >= \"" + start.format(fmt) + "\" AND [metric_time__day] < \"" + endExclusive.format(fmt) + "\")";
    }

    /**
     * 解析 Schema JSON
     */
    private InsightDashboardSchemaDTO parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.toBean(schemaJson, InsightDashboardSchemaDTO.class);
        } catch (Exception e) {
            log.warn("解析仪表盘 Schema 失败: {}", e.getMessage());
            return null;
        }
    }
}
