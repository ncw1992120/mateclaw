package vip.mate.dataagent.service.impl;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import vip.mate.dataagent.dto.InsightDashboardVO;
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

    /** SSE 报告生成线程池（报告生成频率低，2-4 个线程足够） */
    private final ExecutorService reportExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32));

    /** SSE 超时时间：10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    /** LLM 指令前缀 */
    private static final String LLM_INSTRUCTION = """
            你是一个数据分析专家。请基于以下仪表盘数据生成完整的分析报告。
            报告模板中的数据部分已填充，请补充"趋势分析"、"关键发现"、"建议"三个部分，
            直接输出完整的 Markdown 报告，保留已填充的数据部分：

            """;

    @Override
    public String generateReport(Long dashboardId) {
        String prompt = buildReportPrompt(dashboardId);
        Long agentId = resolveAgentId(dashboardId);
        String conversationId = DataAgentConstants.INSIGHT_REPORT_CONVERSATION_PREFIX + UUID.randomUUID();

        String result = runtime.chat(agentId, prompt, conversationId);
        return result != null ? result.trim() : "";
    }

    @Override
    public SseEmitter streamReport(Long dashboardId) {
        SseEmitter emitter = new Utf8SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        // 在 HTTP 线程预构建 prompt（确保 WorkspaceGuard 可用）
        final String prompt = buildReportPrompt(dashboardId);
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
     */
    private String buildReportPrompt(Long dashboardId) {
        InsightDashboardVO dashboard = dashboardService.getDashboard(dashboardId);
        List<InsightComponentDataDTO> componentData = dataBindService.previewData(dashboardId);

        String template = loadReportTemplate();
        template = fillDataPlaceholders(template, dashboard, componentData);

        return LLM_INSTRUCTION + template;
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

        if (schema != null && schema.getComponents() != null) {
            for (InsightDashboardSchemaDTO.Component comp : schema.getComponents()) {
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
                if (dataTable.length() == 0) {
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
                .replace("{{DATA_TABLE}}", dataTable.length() == 0 ? "无数据" : dataTable.toString());
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
}
