package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.InsightDataBindService;
import vip.mate.dataagent.service.InsightReportService;

import java.util.List;

/**
 * 洞察仪表盘控制器
 * <p>
 * 提供低代码仪表盘 Schema 的 CRUD API，支持拖拽编辑、组件数据绑定和 AI 解读报告。
 */
@RestController
@RequestMapping("/v1/insight/dashboards")
@RequiredArgsConstructor
@Tag(name = "洞察仪表盘", description = "低代码仪表盘搭建、组件取数与 AI 解读报告接口")
public class DataAgentInsightDashboardController {

    private final InsightDashboardService dashboardService;
    private final InsightDataBindService dataBindService;
    private final InsightReportService reportService;

    /**
     * 仪表盘列表
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "仪表盘列表", description = "获取当前工作区的所有仪表盘")
    public R<List<InsightDashboardVO>> list() {
        return R.ok(dashboardService.listDashboards());
    }

    /**
     * 仪表盘详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "仪表盘详情", description = "根据 ID 获取仪表盘详情（含 Schema JSON）")
    public R<InsightDashboardVO> get(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id) {
        return R.ok(dashboardService.getDashboard(id));
    }

    /**
     * 创建仪表盘
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "创建仪表盘", description = "创建空白仪表盘，初始状态为草稿")
    public R<InsightDashboardVO> create(@RequestBody InsightDashboardCreateRequest request) {
        return R.ok(dashboardService.createDashboard(request));
    }

    /**
     * 更新仪表盘
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "更新仪表盘", description = "更新仪表盘基本信息或保存 Schema JSON")
    public R<InsightDashboardVO> update(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id,
            @RequestBody InsightDashboardUpdateRequest request) {
        return R.ok(dashboardService.updateDashboard(id, request));
    }

    /**
     * 删除仪表盘
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "删除仪表盘", description = "逻辑删除指定仪表盘")
    public R<Void> delete(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id) {
        dashboardService.deleteDashboard(id);
        return R.ok(null);
    }

    /**
     * 预览仪表盘
     * <p>
     * 获取仪表盘所有组件的渲染数据（取数 + 图表构建），用于预览模式渲染。
     * 支持传入运行时筛选上下文（时间范围、维度筛选），实现筛选联动取数。
     */
    @PostMapping("/{id}/preview")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "预览仪表盘", description = "获取仪表盘所有组件的渲染数据，支持运行时筛选条件（时间范围、维度筛选）")
    public R<List<InsightComponentDataDTO>> preview(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id,
            @RequestBody(required = false) DashboardFilterContextDTO filterContext) {
        return R.ok(dataBindService.previewData(id, filterContext));
    }

    /**
     * 预览单个组件数据
     * <p>
     * 根据组件配置（含数据源、指标、维度）实时查询并生成渲染数据，
     * 用于编辑器中配置组件后即时验证数据是否可用。
     */
    @PostMapping("/preview-component")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "预览组件数据", description = "根据组件配置实时查询数据，用于编辑器中即时验证")
    public R<InsightComponentDataDTO> previewComponent(
            @RequestBody InsightDashboardSchemaDTO.Component component) {
        return R.ok(dataBindService.bindComponent(component));
    }

    /**
     * AI助手对话
     * <p>
     * 统一AI生成和AI修改能力，通过dashboardId是否为空区分模式：
     * - dashboardId为空：AI生成模式，根据用户描述和数据源生成新仪表盘
     * - dashboardId不为空：AI修改模式，根据用户指令修改已有仪表盘
     */
    @PostMapping("/ai-chat")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "AI助手对话", description = "统一AI生成和AI修改能力，dashboardId为空时生成新仪表盘，不为空时修改已有仪表盘")
    public R<InsightDashboardVO> aiChat(@RequestBody InsightDashboardAiChatRequest request) {
        return R.ok(dashboardService.aiChatDashboard(request));
    }

    /**
     * 生成 AI 报告（同步）
     * <p>
     * 等待 LLM 完成后生成 HTML 格式报告，并持久化到仪表盘。
     */
    @PostMapping("/{id}/report")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "生成 AI 报告", description = "生成 HTML 格式报告并持久化到仪表盘")
    public R<String> generateReport(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id) {
        return R.ok(reportService.generateReport(id));
    }

    /**
     * 获取已生成的 AI 报告
     */
    @GetMapping("/{id}/report")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "获取 AI 报告", description = "获取已生成的 HTML 格式报告内容")
    public R<String> getReport(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id) {
        return R.ok(reportService.getReport(id));
    }

    /**
     * 生成 AI 报告（流式）
     * <p>
     * 通过 SSE 推送 LLM 流式响应，支持 content/error 命名事件。
     */
    @PostMapping(value = "/{id}/report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "生成 AI 报告（流式）", description = "通过 SSE 推送 LLM 流式响应，支持 content/error 事件")
    public SseEmitter streamReport(
            @Parameter(description = "仪表盘 ID") @PathVariable Long id) {
        return reportService.streamReport(id);
    }

    /**
     * 归因分析
     * <p>
     * 基于仪表盘组件的指标和维度，调用 Aloudata 归因分析 API：
     * 1. 校验指标是否可归因；2. 执行多维归因分析；3. 对高贡献维度自动下钻。
     */
    @PostMapping("/attribution")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "归因分析", description = "基于指标和维度执行归因分析，包含校验、多维归因和下钻分析")
    public R<AttributionAnalysisResponse> attributionAnalysis(
            @RequestBody AttributionAnalysisRequest request) {
        return R.ok(reportService.attributionAnalysis(request));
    }
}
