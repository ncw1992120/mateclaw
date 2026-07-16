package vip.mate.dataagent.service.impl;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.model.AgentEntity;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.InsightComponentDataDTO;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;
import vip.mate.dataagent.dto.InsightDashboardUpdateRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.dto.AttributionAnalysisRequest;
import vip.mate.dataagent.dto.AttributionAnalysisResponse;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.InsightDataBindService;
import vip.mate.dataagent.service.InsightReportService;
import vip.mate.dataagent.support.Utf8SseEmitter;
import vip.mate.sdk.service.MateClawRuntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 洞察仪表盘 AI 解读报告服务实现
 * <p>
 * 流程：
 * <ol>
 *   <li>加载仪表盘 Schema + 组件渲染数据</li>
 *   <li>从 classpath 读取报告模板（report-template.md）</li>
 *   <li>填充数据占位符（METRIC_NAMES / DIMENSION_NAMES / TIME_RANGE / FILTERS / ROW_COUNT / DATA_TABLE）</li>
 *   <li>调用 {@link MateClawRuntime#chat} 生成分析部分（TREND_ANALYSIS / KEY_FINDINGS / RECOMMENDATIONS）</li>
 * </ol>
 * <p>
 * 支持同步（blockLast 累积）和 SSE 流式两种模式。
 * <p>
 * 线程上下文：SSE 模式在 HTTP 线程预构建 prompt（确保 WorkspaceGuard 可用），再提交到独立线程池执行 LLM 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightReportServiceImpl implements InsightReportService {

    private final InsightDashboardService dashboardService;
    private final InsightDataBindService dataBindService;
    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;
    private final AloudataService aloudataService;

    /** SSE 报告生成线程池（报告生成频率低，2-4 个线程足够） */
    private final ExecutorService reportExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32));

    /** SSE 超时时间：10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    /** LLM 指令前缀 */
    private static final String LLM_INSTRUCTION = """
            你是一个数据分析专家。请基于以下仪表盘数据生成分析结论。
            报告模板中已填充数据部分，你只需要补充"趋势分析"、"关键发现"、"建议"三个章节，
            直接输出这三个章节的 Markdown 内容，不要重复输出数据部分。

            在分析中引用图表时，请使用占位符 `[echarts:图表ID]`，占位符单独占一行。
            例如：
            [echarts:chart_0]
            表示在此处插入对应图表。你可以在趋势分析或关键发现章节中引用图表来辅助说明。

            """;

    /** 归因分析指令追加（当存在归因数据时附加到 prompt 末尾） */
    private static final String ATTRIBUTION_INSTRUCTION = """

            ---
            以下是该指标的归因分析数据，请在"关键发现"和"建议"章节中结合归因分析结果进行深入解读：
            - 分析各维度对指标变化的贡献程度
            - 指出贡献最大的维度值及其影响
            - 基于归因结果给出针对性的建议

            """;

    @Override
    public String generateReport(Long dashboardId) {
        List<InsightComponentDataDTO> componentData = dataBindService.previewData(dashboardId);

        // 预收集 echarts option 映射（chart_0 → option JSON）
        Map<String, String> echartsOptions = collectEchartsOptions(componentData);

        String prompt = buildReportPrompt(dashboardId, componentData);
        Long agentId = resolveAgentId(dashboardId);
        String conversationId = DataAgentConstants.INSIGHT_REPORT_CONVERSATION_PREFIX + UUID.randomUUID();

        String result = runtime.chat(agentId, prompt, conversationId);
        String rawReport = result != null ? result.trim() : "";

        // 清洗 LLM 输出：去掉代码块包裹，只保留分析结论部分
        String cleanedReport = cleanLlmOutput(rawReport);

        // 持久化到 Schema 中所有 aiAnalysis 组件
        saveAiAnalysisContent(dashboardId, cleanedReport);

        // 转换为 HTML（含 echarts 占位符替换）并持久化到 reportContent 字段
        String htmlReport = convertMarkdownToHtml(cleanedReport, echartsOptions);
        saveReportContent(dashboardId, htmlReport);

        return htmlReport;
    }

    @Override
    public String getReport(Long dashboardId) {
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
        return dashboard != null ? dashboard.getReportContent() : null;
    }

    /**
     * 清洗 LLM 输出
     * <p>
     * LLM 可能输出：
     * 1. 对话式前言（如"我注意到..."）
     * 2. ````markdown ... ```` 代码块包裹
     * 3. 完整报告（含查询概要、数据结果等不应重复的部分）
     * <p>
     * 本方法提取"趋势分析"、"关键发现"、"建议"、"报告解读"章节，去掉代码块包裹。
     */
    private String cleanLlmOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        String content = rawOutput;

        // 1. 去掉 ```markdown 和 ``` 包裹
        if (content.contains("```markdown")) {
            int start = content.indexOf("```markdown");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start + 10) {
                content = content.substring(start + 10, end).trim();
            }
        } else if (content.startsWith("```")) {
            // 去掉首尾 ```
            content = content.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        // 2. 提取分析章节（从"## 趋势分析"或"## 报告解读"开始）
        String[] markers = {"## 趋势分析", "## 关键发现", "## 建议", "## 报告解读"};
        int bestStart = -1;
        for (String marker : markers) {
            int idx = content.indexOf(marker);
            if (idx >= 0 && (bestStart < 0 || idx < bestStart)) {
                bestStart = idx;
            }
        }

        if (bestStart > 0) {
            content = content.substring(bestStart).trim();
        }

        return content;
    }

    @Override
    public SseEmitter streamReport(Long dashboardId) {
        SseEmitter emitter = new Utf8SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        // 在 HTTP 线程预构建 prompt（确保 WorkspaceGuard 可用）
        final List<InsightComponentDataDTO> componentData = dataBindService.previewData(dashboardId);
        final String prompt = buildReportPrompt(dashboardId, componentData);
        final Long agentId = resolveAgentId(dashboardId);
        final String conversationId = DataAgentConstants.INSIGHT_REPORT_CONVERSATION_PREFIX + UUID.randomUUID();

        reportExecutor.execute(() -> {
            try {
                Flux<StreamDelta> stream = runtime.chatStructuredStream(agentId, prompt, conversationId);
                stream.subscribe(
                        delta -> {
                            if (emitterDone.get()) {
                                return;
                            }
                            if (!delta.isEvent() && delta.content() != null && !delta.content().isBlank()) {
                                sendSseEvent(emitter, "content", delta.content());
                            }
                        },
                        error -> {
                            log.error("报告生成失败: {}", error.getMessage());
                            completeEmitter(emitter, emitterDone, error);
                        },
                        () -> completeEmitter(emitter, emitterDone, null)
                );
            } catch (Exception e) {
                completeEmitter(emitter, emitterDone, e);
            }
        });

        return emitter;
    }

    /**
     * 构建报告 Prompt：填充模板数据部分，附加 LLM 指令
     * <p>
     * 如果仪表盘组件配置了数据源且指标支持归因分析，会自动执行归因分析
     * 并将结果注入 prompt，使 LLM 生成包含归因洞察的分析报告。
     * <p>
     * 同时收集所有 echarts 组件的 option，以图表 ID 映射方式注入 prompt，
     * 使 LLM 可以在分析中引用对应图表。
     */
    private String buildReportPrompt(Long dashboardId, List<InsightComponentDataDTO> componentData) {
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);

        String template = loadReportTemplate();
        template = fillDataPlaceholders(template, dashboard, componentData);

        StringBuilder prompt = new StringBuilder(LLM_INSTRUCTION).append(template);

        // 收集 echarts 图表配置，注入 prompt 供 LLM 引用
        String chartsContext = buildChartsContext(componentData);
        if (chartsContext != null) {
            prompt.append("\n---\n以下是仪表盘中的图表配置，你可以在分析中引用：\n\n");
            prompt.append(chartsContext);
        }

        // 尝试执行归因分析，将结果注入 prompt
        String attributionContext = buildAttributionContext(dashboard);
        if (attributionContext != null) {
            prompt.append(ATTRIBUTION_INSTRUCTION).append(attributionContext);
        }

        return prompt.toString();
    }

    /**
     * 收集所有 echarts 组件的 option，构建图表上下文
     * <p>
     * 为每个 echarts 组件分配 ID（chart_0, chart_1, ...），
     * 输出图表 ID + 标题 + option 摘要，供 LLM 在报告中引用。
     */
    private String buildChartsContext(List<InsightComponentDataDTO> componentData) {
        StringBuilder sb = new StringBuilder();
        int chartIndex = 0;
        for (InsightComponentDataDTO data : componentData) {
            if (!"echarts".equals(data.getRenderType()) || data.getOption() == null) {
                continue;
            }
            String chartId = "chart_" + chartIndex++;
            String optionJson = JSONUtil.toJsonStr(data.getOption());
            // 截断过长的 option，避免 prompt 过大
            if (optionJson.length() > 2000) {
                optionJson = optionJson.substring(0, 2000) + "...(truncated)";
            }
            sb.append("**图表ID**: ").append(chartId).append("\n");
            sb.append("**组件ID**: ").append(data.getComponentId()).append("\n");
            sb.append("**ECharts配置**:\n```json\n").append(optionJson).append("\n```\n\n");
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * 构建归因分析上下文
     * <p>
     * 遍历仪表盘组件，对配置了数据源且有指标和维度的组件执行归因分析，
     * 将结果格式化为 Markdown 供 LLM 参考。
     *
     * @return 归因分析 Markdown，无可用数据时返回 null
     */
    private String buildAttributionContext(InsightDashboardVO dashboard) {
        InsightDashboardSchemaDTO schema = JSONUtil.toBean(dashboard.getSchemaJson(), InsightDashboardSchemaDTO.class);
        if (schema == null || schema.getAllComponents().isEmpty()) {
            return null;
        }

        StringBuilder context = new StringBuilder();
        for (InsightDashboardSchemaDTO.Component comp : schema.getAllComponents()) {
            if (comp.getDataSource() == null || comp.getDataSource().getDatasourceId() == null) {
                continue;
            }
            if (comp.getDataSource().getMetrics() == null || comp.getDataSource().getMetrics().isEmpty()) {
                continue;
            }
            if (comp.getDataSource().getDimensions() == null || comp.getDataSource().getDimensions().isEmpty()) {
                continue;
            }

            try {
                String attributionMd = executeAttributionForComponent(comp);
                if (attributionMd != null) {
                    context.append(attributionMd);
                }
            } catch (Exception e) {
                log.debug("组件 [{}] 归因分析失败，跳过: {}", comp.getId(), e.getMessage());
            }
        }

        return context.isEmpty() ? null : context.toString();
    }

    /**
     * 对单个组件执行归因分析并格式化为 Markdown
     */
    private String executeAttributionForComponent(InsightDashboardSchemaDTO.Component comp) {
        InsightDashboardSchemaDTO.DataSource ds = comp.getDataSource();
        Long datasourceId = Long.valueOf(ds.getDatasourceId());
        String metric = ds.getMetrics().get(0);

        // 1. 校验指标是否可归因
        AttributionAnalysisResponse.CheckResult checkResult =
                aloudataService.checkAttribution(datasourceId, metric);
        if (!Boolean.TRUE.equals(checkResult.getResult())) {
            return null;
        }

        StringBuilder md = new StringBuilder();
        md.append("### 归因分析：").append(metric).append("\n\n");

        // 2. 多维归因分析
        AttributionAnalysisRequest request = new AttributionAnalysisRequest();
        request.setDatasourceId(datasourceId);
        request.setMetric(metric);
        request.setDimensions(ds.getDimensions());
        request.setGranularity("DAY");
        request.setComparisonType("DOD");
        request.setFilters(Collections.emptyList());

        try {
            AttributionAnalysisResponse.MultiDimResult multiDimResult =
                    aloudataService.queryMultiDimAttribution(request);

            if (multiDimResult != null) {
                // 整体概要
                if (multiDimResult.getAll() != null) {
                    AttributionAnalysisResponse.AllSummary all = multiDimResult.getAll();
                    md.append("**整体变化**: 当前值 ").append(formatVal(all.getCurrentValue()))
                      .append("，对比值 ").append(formatVal(all.getComparisonValue()))
                      .append("，变化 ").append(formatChange(all.getGrowth(), all.getGrowthRate()))
                      .append("\n\n");
                }

                // 各维度贡献
                if (multiDimResult.getDimensions() != null) {
                    for (Map.Entry<String, AttributionAnalysisResponse.DimAttribution> entry
                            : multiDimResult.getDimensions().entrySet()) {
                        String dimName = entry.getKey();
                        AttributionAnalysisResponse.DimAttribution dimAttr = entry.getValue();
                        md.append("**维度: ").append(dimName).append("**\n\n");
                        md.append("| 维度值 | 当前值 | 对比值 | 变化 | 贡献率 |\n");
                        md.append("|--------|--------|--------|------|--------|\n");
                        if (dimAttr.getDimensionValue() != null) {
                            for (int i = 0; i < dimAttr.getDimensionValue().size(); i++) {
                                md.append("| ").append(dimAttr.getDimensionValue().get(i))
                                  .append(" | ").append(formatVal(safeGet(dimAttr.getCurrentValue(), i)))
                                  .append(" | ").append(formatVal(safeGet(dimAttr.getComparisonValue(), i)))
                                  .append(" | ").append(formatChange(safeGet(dimAttr.getGrowth(), i), safeGet(dimAttr.getGrowthRate(), i)))
                                  .append(" | ").append(formatPercent(safeGet(dimAttr.getOverallContributionRate(), i)))
                                  .append(" |\n");
                            }
                        }
                        md.append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("多维归因查询失败: {}", e.getMessage());
        }

        // 3. 指标拆解 + 树归因
        try {
            AttributionAnalysisResponse.MetricTreeDef treeDef =
                    aloudataService.breakdownMetric(datasourceId, metric);
            if (treeDef != null && treeDef.getRootNode() != null) {
                md.append("**指标拆解归因**\n\n");
                md.append("| 子指标 | 当前值 | 对比值 | 变化 | 贡献率 |\n");
                md.append("|--------|--------|--------|------|--------|\n");

                // 树归因需要时间对比参数，此处使用默认日环比
                LocalDate today = LocalDate.now();
                Map<String, AttributionAnalysisResponse.TreeNodeAttribution> treeResult =
                        aloudataService.queryTreeAttribution(
                                datasourceId, treeDef,
                                "DateTrunc([metric_time],\"DAY\")=\"" + today + "\"",
                                "DateTrunc([metric_time],\"DAY\")=\"" + today.minusDays(1) + "\"",
                                Collections.emptyList());

                if (treeResult != null) {
                    for (Map.Entry<String, AttributionAnalysisResponse.TreeNodeAttribution> entry : treeResult.entrySet()) {
                        AttributionAnalysisResponse.TreeNodeAttribution node = entry.getValue();
                        String name = treeDef.getMetricTreeNodes() != null
                                ? treeDef.getMetricTreeNodes().getOrDefault(entry.getKey(), entry.getKey())
                                : entry.getKey();
                        md.append("| ").append(name)
                          .append(" | ").append(formatVal(node.getCurrentValue()))
                          .append(" | ").append(formatVal(node.getComparisonValue()))
                          .append(" | ").append(formatChange(node.getGrowth(), node.getGrowthRate()))
                          .append(" | ").append(formatPercent(node.getRelativeContributionRate()))
                          .append(" |\n");
                    }
                }
                md.append("\n");
            }
        } catch (Exception e) {
            log.debug("指标树归因失败: {}", e.getMessage());
        }

        return md.length() > 20 ? md.toString() : null;
    }

    /** 安全获取列表元素 */
    private <T> T safeGet(List<T> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** 格式化数值 */
    private String formatVal(Double val) {
        if (val == null) return "--";
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf(val.longValue());
        }
        return String.format("%.2f", val);
    }

    /** 格式化变化值 */
    private String formatChange(Double growth, Double growthRate) {
        if (growth == null) return "--";
        String sign = growth >= 0 ? "+" : "";
        String rateStr = growthRate != null
                ? String.format(" (%s%.1f%%)", growthRate >= 0 ? "+" : "", growthRate * 100)
                : "";
        return sign + formatVal(growth) + rateStr;
    }

    /** 格式化百分比 */
    private String formatPercent(Double val) {
        if (val == null) return "--";
        return String.format("%.1f%%", val * 100);
    }

    /**
     * 加载报告模板（从 classpath 读取）
     */
    private String loadReportTemplate() {
        try (InputStream in = new ClassPathResource(
                DataAgentConstants.INSIGHT_REPORT_TEMPLATE_PATH).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("加载报告模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充数据占位符
     * <p>
     * 聚合所有组件的指标、维度、筛选条件，构建数据表格，替换模板中的占位符。
     */
    private String fillDataPlaceholders(String template, InsightDashboardVO dashboard,
                                         List<InsightComponentDataDTO> componentData) {
        InsightDashboardSchemaDTO schema = JSONUtil.toBean(dashboard.getSchemaJson(), InsightDashboardSchemaDTO.class);

        Set<String> metrics = new LinkedHashSet<>();
        Set<String> dimensions = new LinkedHashSet<>();
        List<Map<String, Object>> filters = new ArrayList<>();
        int totalRows = 0;
        StringBuilder dataTable = new StringBuilder();

        if (schema != null && !schema.getAllComponents().isEmpty()) {
            for (InsightDashboardSchemaDTO.Component comp : schema.getAllComponents()) {
                if (comp.getDataSource() != null) {
                    if (comp.getDataSource().getMetrics() != null) {
                        metrics.addAll(comp.getDataSource().getMetrics());
                    }
                    if (comp.getDataSource().getDimensions() != null) {
                        dimensions.addAll(comp.getDataSource().getDimensions());
                    }
                    if (comp.getDataSource().getFilters() != null) {
                        filters.addAll(comp.getDataSource().getFilters());
                    }
                }
            }
        }

        // 构建数据表格（取第一个含表格数据的组件）
        for (InsightComponentDataDTO data : componentData) {
            if (data.getTable() != null && data.getTable().getRows() != null) {
                totalRows += data.getTable().getRows().size();
                if (dataTable.isEmpty()) {
                    dataTable.append(buildMarkdownTable(data.getTable()));
                }
            }
        }

        return template
                .replace("{{METRIC_NAMES}}", metrics.isEmpty() ? "无" : String.join("、", metrics))
                .replace("{{DIMENSION_NAMES}}", dimensions.isEmpty() ? "无" : String.join("、", dimensions))
                .replace("{{TIME_RANGE}}", "全部时间")
                .replace("{{FILTERS}}", filters.isEmpty() ? "无" : JSONUtil.toJsonStr(filters))
                .replace("{{ROW_COUNT}}", String.valueOf(totalRows))
                .replace("{{DATA_TABLE}}", dataTable.isEmpty() ? "无数据" : dataTable.toString());
    }

    /**
     * 将表格数据构建为 Markdown 表格
     */
    private String buildMarkdownTable(InsightComponentDataDTO.TableData tableData) {
        StringBuilder sb = new StringBuilder();
        List<String> columns = tableData.getColumns();
        List<List<String>> rows = tableData.getRows();

        if (columns == null || columns.isEmpty()) {
            return "";
        }

        // 表头
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        // 分隔行
        sb.append("|").append(columns.stream().map(c -> "---").collect(Collectors.joining("|"))).append("|\n");
        // 数据行
        if (rows != null) {
            for (List<String> row : rows) {
                sb.append("| ").append(String.join(" | ", row)).append(" |\n");
            }
        }
        return sb.toString();
    }

    /**
     * 将 AI 分析内容持久化到 Schema 中所有 aiAnalysis 组件
     */
    private void saveAiAnalysisContent(Long dashboardId, String content) {
        try {
            InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
            InsightDashboardSchemaDTO schema = JSONUtil.toBean(dashboard.getSchemaJson(), InsightDashboardSchemaDTO.class);
            if (schema == null || schema.getAllComponents().isEmpty()) return;

            boolean changed = false;
            for (InsightDashboardSchemaDTO.Component comp : schema.getAllComponents()) {
                if ("aiAnalysis".equals(comp.getType())) {
                    comp.setAiAnalysisContent(content);
                    changed = true;
                }
            }
            if (changed) {
                InsightDashboardUpdateRequest updateReq = new InsightDashboardUpdateRequest();
                updateReq.setSchemaJson(JSONUtil.toJsonStr(schema));
                dashboardService.updateDashboard(dashboardId, updateReq);
            }
        } catch (Exception e) {
            log.warn("AI 分析内容持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 收集所有 echarts 组件的 option，构建 chartId → optionJson 映射
     */
    private Map<String, String> collectEchartsOptions(List<InsightComponentDataDTO> componentData) {
        Map<String, String> options = new LinkedHashMap<>();
        int chartIndex = 0;
        for (InsightComponentDataDTO data : componentData) {
            if (!"echarts".equals(data.getRenderType()) || data.getOption() == null) {
                continue;
            }
            String chartId = "chart_" + chartIndex++;
            options.put(chartId, JSONUtil.toJsonStr(data.getOption()));
        }
        return options;
    }

    /**
     * 将 Markdown 转换为完整 HTML 片段
     * <p>
     * 使用 commonmark-java 将 Markdown 渲染为 HTML。
     * 将 LLM 输出的 `[echarts:chart_N]` 占位符替换为带 data-chart-id 属性的 div，
     * 供前端 ECharts 渲染。
     */
    private String convertMarkdownToHtml(String markdown, Map<String, String> echartsOptions) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        try {
            // 先将 echarts 占位符替换为 HTML 标记
            String processed = markdown;
            for (Map.Entry<String, String> entry : echartsOptions.entrySet()) {
                String placeholder = "[echarts:" + entry.getKey() + "]";
                String chartDiv = "<div class=\"echarts-container\" data-chart-id=\"" + entry.getKey() + "\"></div>";
                processed = processed.replace(placeholder, chartDiv);
            }

            Node document = Parser.builder().build().parse(processed);
            String bodyHtml = HtmlRenderer.builder()
                    .escapeHtml(true)
                    .build()
                    .render(document);

            // 还原被 commonmark 转义的 echarts div 标签
            bodyHtml = restoreEchartsDivs(bodyHtml);

            return bodyHtml;
        } catch (Exception e) {
            log.warn("Markdown 转 HTML 失败，返回原始 Markdown: {}", e.getMessage());
            return markdown;
        }
    }

    /**
     * 还原被 commonmark 转义的 echarts div 标签
     */
    private String restoreEchartsDivs(String html) {
        return html.replaceAll(
                "&lt;div class=&quot;echarts-container&quot; data-chart-id=&quot;(\\w+)&quot;&gt;&lt;/div&gt;",
                "<div class=\"echarts-container\" data-chart-id=\"$1\"></div>"
        );
    }

    /**
     * 构建完整 HTML 文档
     */
    private String buildHtmlDocument(String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>AI 分析报告</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                           max-width: 800px; margin: 0 auto; padding: 24px; color: #333; line-height: 1.8; }
                    h1 { font-size: 24px; border-bottom: 2px solid #e8e8e8; padding-bottom: 8px; }
                    h2 { font-size: 20px; border-bottom: 1px solid #e8e8e8; padding-bottom: 6px; margin-top: 24px; }
                    h3 { font-size: 16px; margin-top: 16px; }
                    table { border-collapse: collapse; width: 100%; margin: 12px 0; }
                    th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
                    th { background-color: #f5f5f5; font-weight: 600; }
                    tr:nth-child(even) { background-color: #fafafa; }
                    code { background-color: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 14px; }
                    blockquote { border-left: 4px solid #ddd; margin: 12px 0; padding: 8px 16px; color: #666; }
                    ul, ol { padding-left: 24px; }
                    li { margin: 4px 0; }
                    .disclaimer { font-size: 12px; color: #999; text-align: center; margin-top: 32px;
                                 padding-top: 12px; border-top: 1px solid #e8e8e8; }
                  </style>
                </head>
                <body>
                """ + bodyHtml + """
                  <div class="disclaimer">AI 分析仅供参考</div>
                </body>
                </html>
                """;
    }

    /**
     * 将报告内容持久化到仪表盘的 reportContent 字段
     */
    private void saveReportContent(Long dashboardId, String htmlReport) {
        try {
            InsightDashboardUpdateRequest updateReq = new InsightDashboardUpdateRequest();
            updateReq.setReportContent(htmlReport);
            dashboardService.updateDashboard(dashboardId, updateReq);
        } catch (Exception e) {
            log.warn("报告内容持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 Agent ID：优先使用仪表盘配置的 agentId，否则取工作区第一个启用的 Agent
     */
    private Long resolveAgentId(Long dashboardId) {
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
        if (dashboard.getAgentId() != null) {
            return dashboard.getAgentId();
        }
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        List<AgentEntity> agents = runtime.listAgentsByWorkspace(workspaceId, true);
        if (agents == null || agents.isEmpty()) {
            throw new IllegalStateException("当前工作区下没有可用的 Agent，请先创建 Agent 或在仪表盘配置中指定 Agent");
        }
        return agents.get(0).getId();
    }

    /**
     * 发送 SSE 事件
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("SSE 发送失败: {}", e.getMessage());
        }
    }

    /**
     * 完成 SSE（含错误处理）
     */
    private void completeEmitter(SseEmitter emitter, AtomicBoolean done, Throwable error) {
        if (done.compareAndSet(false, true)) {
            try {
                if (error != null) {
                    emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                }
                emitter.complete();
            } catch (IOException e) {
                log.warn("SSE 完成失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public AttributionAnalysisResponse attributionAnalysis(AttributionAnalysisRequest request) {
        AttributionAnalysisResponse response = new AttributionAnalysisResponse();

        // 1. 校验指标是否可归因
        AttributionAnalysisResponse.CheckResult checkResult =
                aloudataService.checkAttribution(request.getDatasourceId(), request.getMetric());
        response.setCheckResult(checkResult);
        if (!Boolean.TRUE.equals(checkResult.getResult())) {
            response.setSuccess(false);
            response.setErrorMsg(checkResult.getErrorMsg() != null
                    ? checkResult.getErrorMsg() : "该指标不支持归因分析");
            return response;
        }

        // 2. 指标拆解，获取指标树结构
        AttributionAnalysisResponse.MetricTreeDef metricTreeDef = null;
        try {
            metricTreeDef = aloudataService.breakdownMetric(request.getDatasourceId(), request.getMetric());
            response.setMetricTreeDef(metricTreeDef);
        } catch (Exception e) {
            log.warn("指标拆解失败，跳过树归因: {}", e.getMessage());
        }

        // 3. 指标树归因（时间对比）：基于拆解结果分析各子指标贡献
        if (metricTreeDef != null && metricTreeDef.getRootNode() != null
                && request.getCurrentTimeExpr() != null && request.getCompareTimeExpr() != null) {
            try {
                Map<String, AttributionAnalysisResponse.TreeNodeAttribution> treeResult =
                        aloudataService.queryTreeAttribution(
                                request.getDatasourceId(),
                                metricTreeDef,
                                request.getCurrentTimeExpr(),
                                request.getCompareTimeExpr(),
                                request.getFilters());
                response.setTreeResult(treeResult);
            } catch (Exception e) {
                log.warn("指标树归因失败，跳过: {}", e.getMessage());
            }
        }

        // 4. 执行多维归因分析
        AttributionAnalysisResponse.MultiDimResult multiDimResult =
                aloudataService.queryMultiDimAttribution(request);
        response.setMultiDimResult(multiDimResult);

        // 5. 对高贡献维度自动下钻分析
        if (multiDimResult != null && multiDimResult.getDimensions() != null
                && request.getDimensions() != null && !request.getDimensions().isEmpty()) {
            for (String dimName : request.getDimensions()) {
                AttributionAnalysisResponse.DimAttribution dimAttr = multiDimResult.getDimensions().get(dimName);
                if (dimAttr == null || dimAttr.getOverallContributionRate() == null
                        || dimAttr.getOverallContributionRate().isEmpty()) {
                    continue;
                }
                // 找到贡献率最高的维度值进行下钻
                int maxIdx = -1;
                double maxRate = 0;
                for (int i = 0; i < dimAttr.getOverallContributionRate().size(); i++) {
                    Double rate = dimAttr.getOverallContributionRate().get(i);
                    if (rate != null && Math.abs(rate) > maxRate) {
                        maxRate = Math.abs(rate);
                        maxIdx = i;
                    }
                }
                if (maxIdx >= 0 && maxRate >= 0.1) {
                    try {
                        AttributionAnalysisRequest drillRequest = new AttributionAnalysisRequest();
                        drillRequest.setDatasourceId(request.getDatasourceId());
                        drillRequest.setMetric(request.getMetric());
                        drillRequest.setGranularity(request.getGranularity());
                        drillRequest.setComparisonType(request.getComparisonType());
                        drillRequest.setCurrentTimeExpr(request.getCurrentTimeExpr());
                        drillRequest.setCompareTimeExpr(request.getCompareTimeExpr());
                        drillRequest.setStartDateTime(request.getStartDateTime());
                        drillRequest.setEndDateTime(request.getEndDateTime());
                        drillRequest.setFilters(request.getFilters());
                        drillRequest.setDrillDimension(dimName);
                        // 构建下钻筛选条件
                        if (dimAttr.getDimensionValue() != null && maxIdx < dimAttr.getDimensionValue().size()) {
                            String dimValue = dimAttr.getDimensionValue().get(maxIdx);
                            List<String> drillFilters = new ArrayList<>();
                            drillFilters.add("IN(['" + dimName + "'],\"" + dimValue + "\")");
                            drillRequest.setDrillFilters(drillFilters);
                        }
                        aloudataService.queryDrilldownAttribution(drillRequest);
                    } catch (Exception e) {
                        log.debug("维度 [{}] 下钻分析失败，跳过: {}", dimName, e.getMessage());
                    }
                }
            }
        }

        response.setSuccess(true);
        response.setCode("200");
        return response;
    }
}
