package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.ChartInterpretRequest;
import vip.mate.dataagent.dto.ChartMetricMetaRequest;
import vip.mate.dataagent.dto.ChartMetricMetaResponse;
import vip.mate.dataagent.model.AloudataMetricDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.ChartInsightService;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ChartInsightService} 实现。
 * <p>
 * 指标查看：按 metricName / dimName 查本地语义层元数据表解析中文名与口径，权威准确、不依赖大模型；
 * 图表解读：镜像推荐问题的一次性轻量 LLM 调用（独立会话前缀，不污染真实历史）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartInsightServiceImpl implements ChartInsightService {

    private final MateClawRuntime runtime;
    private final AloudataMetricMapper metricMapper;
    private final AloudataMetricDimensionMapper metricDimensionMapper;
    private final DatasourceMapper datasourceMapper;

    /** metric_time 时间粒度 -> 中文描述 */
    private static final Map<String, String> TIME_GRAIN_CN = Map.of(
            "day", "按日", "week", "按周", "month", "按月",
            "quarter", "按季", "year", "按年"
    );

    @Override
    public ChartMetricMetaResponse resolveChartMetricMeta(ChartMetricMetaRequest request) {
        ChartMetricMetaResponse resp = new ChartMetricMetaResponse();
        if (request == null) {
            return resp;
        }
        resp.setTimeRange(request.getTimeConstraint());
        if (request.getFilters() != null) {
            resp.setFilters(new ArrayList<>(request.getFilters()));
        }

        // datasourceId 可能缺失（单一 Aloudata 源时服务端自动注入、未落入工具入参），此处兜底解析
        Long datasourceId = resolveDatasourceId(request.getDatasourceId());

        // ===== 指标 =====
        List<String> rawMetrics = request.getMetrics() != null ? request.getMetrics() : List.of();
        // 提取基名（去掉快速计算后缀），保序去重
        Set<String> baseMetricNames = rawMetrics.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.split("__")[0])
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, AloudataMetricEntity> metricMap = Map.of();
        if (!baseMetricNames.isEmpty()) {
            // 先按 datasourceId 精确查；查不到再按指标名跨源兜底（datasourceId 缺失或不一致时）
            List<AloudataMetricEntity> entities = queryMetrics(datasourceId, baseMetricNames);
            if (entities.isEmpty() && datasourceId != null) {
                entities = queryMetrics(null, baseMetricNames);
            }
            metricMap = entities.stream().collect(Collectors.toMap(
                    AloudataMetricEntity::getMetricName, Function.identity(), (a, b) -> a));
        }
        for (String base : baseMetricNames) {
            ChartMetricMetaResponse.MetricItem item = new ChartMetricMetaResponse.MetricItem();
            item.setName(base);
            AloudataMetricEntity e = metricMap.get(base);
            if (e != null) {
                item.setDisplayName(hasText(e.getMetricDisplayName()) ? e.getMetricDisplayName() : base);
                item.setCaliber(e.getBusinessCaliber());
                item.setUnit(hasText(e.getCnUnit()) ? e.getCnUnit() : e.getUnit());
                item.setCategory(e.getMetricCategoryName());
            } else {
                item.setDisplayName(base);
            }
            resp.getMetrics().add(item);
        }

        // ===== 维度 =====
        List<String> rawDims = request.getDimensions() != null ? request.getDimensions() : List.of();
        // 用户维度基名（排除 metric_time 系列，单独处理），保序去重
        Set<String> baseDimNames = new LinkedHashSet<>();
        // 保序记录全部维度（含时间维度），用于最终按序输出
        List<String> orderedDims = new ArrayList<>();
        for (String d : rawDims) {
            if (d == null || d.isBlank()) {
                continue;
            }
            orderedDims.add(d);
            if (!d.startsWith("metric_time")) {
                baseDimNames.add(d.contains("__") ? d.split("__")[0] : d);
            }
        }

        Map<String, String> dimNameMap = Map.of();
        if (!baseDimNames.isEmpty()) {
            List<AloudataMetricDimensionEntity> dimEntities = queryDimensions(datasourceId, baseDimNames);
            if (dimEntities.isEmpty() && datasourceId != null) {
                dimEntities = queryDimensions(null, baseDimNames);
            }
            dimNameMap = dimEntities.stream()
                    .filter(x -> hasText(x.getDimDisplayName()))
                    .collect(Collectors.toMap(
                            AloudataMetricDimensionEntity::getDimName,
                            AloudataMetricDimensionEntity::getDimDisplayName,
                            (a, b) -> a));
        }

        Set<String> emittedDim = new LinkedHashSet<>();
        for (String d : orderedDims) {
            if (d.startsWith("metric_time")) {
                // 系统时间维度：解析粒度中文
                String grain = d.contains("__") ? d.split("__")[1] : "day";
                String cn = TIME_GRAIN_CN.getOrDefault(grain, grain);
                if (emittedDim.add(d)) {
                    ChartMetricMetaResponse.DimItem item = new ChartMetricMetaResponse.DimItem();
                    item.setName(d);
                    item.setDisplayName("时间（" + cn + "）");
                    resp.getDimensions().add(item);
                }
            } else {
                String base = d.contains("__") ? d.split("__")[0] : d;
                if (emittedDim.add(base)) {
                    ChartMetricMetaResponse.DimItem item = new ChartMetricMetaResponse.DimItem();
                    item.setName(base);
                    item.setDisplayName(dimNameMap.getOrDefault(base, base));
                    resp.getDimensions().add(item);
                }
            }
        }

        return resp;
    }

    @Override
    public String interpretChart(ChartInterpretRequest request) {
        if (request == null || request.getAgentId() == null
                || !hasText(request.getEchartsOption())) {
            return "缺少图表数据，无法生成解读。";
        }
        try {
            String optionJson = request.getEchartsOption();
            if (optionJson.length() > DataAgentConstants.CHART_INTERPRET_OPTION_MAX_LENGTH) {
                optionJson = optionJson.substring(0, DataAgentConstants.CHART_INTERPRET_OPTION_MAX_LENGTH);
            }
            String question = hasText(request.getQuestion()) ? request.getQuestion() : "（未提供）";
            String prompt = DataAgentConstants.CHART_INTERPRET_PROMPT_TEMPLATE
                    .replace("{0}", question)
                    .replace("{1}", optionJson);

            // 独立会话：前缀 + 会话ID + 图表指纹，保证解读之间互不串扰，同图重复点击可复用
            String cid = DataAgentConstants.CHART_INTERPRET_CONVERSATION_PREFIX
                    + (hasText(request.getConversationId()) ? request.getConversationId() : "adhoc")
                    + "-" + Integer.toHexString(request.getEchartsOption().hashCode());

            String result = runtime.chat(request.getAgentId(), prompt, cid);
            if (!hasText(result)) {
                return "暂时无法生成解读，请稍后重试。";
            }
            return result.trim();
        } catch (Exception e) {
            log.warn("[ChartInsight] 图表解读生成失败: {}", e.getMessage());
            return "暂时无法生成解读，请稍后重试。";
        }
    }

    /**
     * 解析 datasourceId：入参非空则直接用；为空时若系统内只有一个启用的 Aloudata 数据源则自动采用
     * （镜像 AloudataCallTool.autoResolveDatasourceId 的确定性策略）。
     */
    private Long resolveDatasourceId(Long given) {
        if (given != null) {
            return given;
        }
        try {
            List<DatasourceEntity> sources = datasourceMapper.selectList(
                    new LambdaQueryWrapper<DatasourceEntity>()
                            .eq(DatasourceEntity::getSourceType, DataAgentConstants.SOURCE_TYPE_ALOUDATA)
                            .eq(DatasourceEntity::getEnabled, true)
                            .select(DatasourceEntity::getId));
            if (sources.size() == 1) {
                return sources.get(0).getId();
            }
        } catch (Exception e) {
            log.warn("[ChartInsight] 自动解析 Aloudata 数据源失败: {}", e.getMessage());
        }
        return null;
    }

    /** 按指标英文名查指标元数据；datasourceId 为空时跨源查询 */
    private List<AloudataMetricEntity> queryMetrics(Long datasourceId, Collection<String> names) {
        try {
            LambdaQueryWrapper<AloudataMetricEntity> w = new LambdaQueryWrapper<AloudataMetricEntity>()
                    .in(AloudataMetricEntity::getMetricName, names);
            if (datasourceId != null) {
                w.eq(AloudataMetricEntity::getDatasourceId, datasourceId);
            }
            return metricMapper.selectList(w);
        } catch (Exception e) {
            log.warn("[ChartInsight] 解析指标元数据失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按维度英文名查维度元数据；datasourceId 为空时跨源查询 */
    private List<AloudataMetricDimensionEntity> queryDimensions(Long datasourceId, Collection<String> names) {
        try {
            LambdaQueryWrapper<AloudataMetricDimensionEntity> w = new LambdaQueryWrapper<AloudataMetricDimensionEntity>()
                    .in(AloudataMetricDimensionEntity::getDimName, names);
            if (datasourceId != null) {
                w.eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId);
            }
            return metricDimensionMapper.selectList(w);
        } catch (Exception e) {
            log.warn("[ChartInsight] 解析维度元数据失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
