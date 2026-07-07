package vip.mate.dataagent.service.impl;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
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
        // filter 类型无需取数
        if ("filter".equals(component.getType())) {
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

        // 3. 转换响应格式：List<Map> → List<String> + List<List<String>>
        List<String> columns = extractColumnNames(response.getData().getColumns());
        List<List<String>> rows = convertRows(response.getData().getRows(), columns);

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
     * 从 Aloudata 响应的 columns 提取列名
     * <p>
     * columns 结构：List&lt;Map&lt;String, Object&gt;&gt;，每个 Map 含 "name" 字段（Aloudata metas 结构）。
     * 兼容 "fieldName" 键以适配不同 API 版本。
     */
    private List<String> extractColumnNames(List<Map<String, Object>> columns) {
        if (columns == null || columns.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(columns.size());
        for (Map<String, Object> col : columns) {
            Object name = col.get("name");
            if (name == null) {
                name = col.get("fieldName");
            }
            names.add(name != null ? name.toString() : "unknown");
        }
        return names;
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
