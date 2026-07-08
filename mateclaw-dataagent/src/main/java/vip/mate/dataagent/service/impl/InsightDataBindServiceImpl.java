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
import java.util.HashMap;
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
        return schema.getComponents().stream()
                .map(component -> bindComponentWithFilters(component, filterContext))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 绑定单个组件数据（合并运行时筛选条件）
     * <p>
     * 将筛选上下文中的时间范围和维度筛选值合并到组件的静态 filters 中，再执行取数。
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
            Component merged = mergeFilters(component, filterContext);
            return doBind(merged);
        } catch (Exception e) {
            log.warn("组件 {} 带筛选取数失败: {}", component.getId(), e.getMessage());
            return buildError(component.getId(), e.getMessage());
        }
    }

    /**
     * 将运行时筛选条件合并到组件的 DataSource.filters 中
     * <p>
     * 合并策略：保留组件静态 filters，追加时间范围筛选和维度筛选值。
     * 使用深拷贝避免修改原始 Schema 对象。
     */
    private Component mergeFilters(Component component, DashboardFilterContextDTO filterContext) {
        Component merged = JSONUtil.toBean(JSONUtil.toJsonStr(component), Component.class);
        DataSource ds = merged.getDataSource();
        if (ds == null) {
            return merged;
        }
        List<Map<String, Object>> filters = new ArrayList<>();
        if (ds.getFilters() != null) {
            filters.addAll(ds.getFilters());
        }
        // 合并时间范围筛选
        if (filterContext.getTimeRange() != null) {
            Map<String, Object> timeFilter = buildTimeRangeFilter(filterContext.getTimeRange());
            if (timeFilter != null) {
                filters.add(timeFilter);
            }
        }
        // 合并维度筛选值
        if (filterContext.getDimensionFilters() != null) {
            for (FilterValue fv : filterContext.getDimensionFilters()) {
                if (fv.getField() == null || fv.getField().isBlank()) {
                    continue;
                }
                Map<String, Object> dimFilter = new HashMap<>();
                dimFilter.put(DataAgentConstants.INSIGHT_FILTER_KEY_FIELD, fv.getField());
                dimFilter.put(DataAgentConstants.INSIGHT_FILTER_KEY_OPERATOR, DataAgentConstants.INSIGHT_FILTER_OP_IN);
                dimFilter.put(DataAgentConstants.INSIGHT_FILTER_KEY_VALUE, fv.getValue());
                filters.add(dimFilter);
            }
        }
        ds.setFilters(filters);
        return merged;
    }

    /**
     * 根据时间预设构建时间范围筛选条件
     * <p>
     * 返回格式：{field: "metric_time", operator: "between", value: ["2024-01-01", "2024-01-31"]}
     * preset=custom 时使用 start/end；预设类型自动计算日期区间。
     *
     * @return 时间筛选条件 Map，无效预设时返回 null
     */
    private Map<String, Object> buildTimeRangeFilter(TimeRangeValue timeRange) {
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
        Map<String, Object> filter = new HashMap<>();
        filter.put(DataAgentConstants.INSIGHT_FILTER_KEY_FIELD, DataAgentConstants.INSIGHT_FILTER_TIME_FIELD);
        filter.put(DataAgentConstants.INSIGHT_FILTER_KEY_OPERATOR, DataAgentConstants.INSIGHT_FILTER_OP_BETWEEN);
        filter.put(DataAgentConstants.INSIGHT_FILTER_KEY_VALUE, List.of(start.format(fmt), end.format(fmt)));
        return filter;
    }

    /**
     * 执行单组件取数与渲染数据构建
     */
    private InsightComponentDataDTO doBind(Component component) {
        DataSource ds = component.getDataSource();
        Long datasourceId = Long.parseLong(ds.getDatasourceId());

        // 1. 构建 AloudataMetricQueryRequest
        AloudataMetricQueryRequest request = new AloudataMetricQueryRequest();
        request.setMetrics(ds.getMetrics());
        request.setDimensions(ds.getDimensions());
        request.setFilters(ds.getFilters());
        request.setLimit(ds.getLimit() != null ? ds.getLimit() : 100);

        // 2. 调用 Aloudata 查询
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
