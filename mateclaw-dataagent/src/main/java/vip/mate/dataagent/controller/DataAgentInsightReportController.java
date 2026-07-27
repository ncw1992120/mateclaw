package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.InsightReportPublishRequest;
import vip.mate.dataagent.dto.InsightReportVO;
import vip.mate.dataagent.service.InsightReportService;

import java.util.List;

/**
 * 洞察报告控制器
 * <p>
 * 提供报告的发布、查询、删除和订阅 API，报告内容从仪表盘复制而来，
 * 独立存储在 dataagent_insight_report 表中。
 * <p>
 * 子视图说明：
 * <ul>
 *   <li>我的洞察（/mine）：当前用户发布的报告</li>
 *   <li>洞察广场（GET /）：工作区所有已发布报告</li>
 *   <li>我的订阅（/subscribed）：当前用户订阅的报告</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/insight/reports")
@RequiredArgsConstructor
@Tag(name = "洞察报告", description = "报告发布、查询、删除与订阅接口")
public class DataAgentInsightReportController {

    private final InsightReportService reportService;

    /**
     * 发布报告
     * <p>
     * 从仪表盘复制报告内容，创建独立的报告记录。
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "发布报告", description = "从仪表盘复制报告内容，创建独立的已发布报告记录")
    public R<InsightReportVO> publish(@Valid @RequestBody InsightReportPublishRequest request) {
        return R.ok(reportService.publishReport(request));
    }

    /**
     * 洞察广场 - 报告列表
     * <p>
     * 查询当前工作区的所有已发布报告，按更新时间降序排列，
     * 并标记当前用户是否已订阅。
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "洞察广场", description = "获取当前工作区所有已发布报告，标记当前用户订阅状态")
    public R<List<InsightReportVO>> list() {
        return R.ok(reportService.listAllReports());
    }

    /**
     * 我的洞察 - 当前用户发布的报告
     * <p>
     * 按工作区 + 当前用户过滤，按更新时间降序排列。
     * <p>
     * 注意：此路由必须放在 /{id} 之前，避免被路径变量匹配。
     */
    @GetMapping("/mine")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "我的洞察", description = "获取当前用户发布的报告列表")
    public R<List<InsightReportVO>> listMine() {
        return R.ok(reportService.listMyReports());
    }

    /**
     * 我的订阅 - 当前用户订阅的报告
     * <p>
     * 查询当前用户订阅的报告列表，按更新时间降序排列。
     * <p>
     * 注意：此路由必须放在 /{id} 之前，避免被路径变量匹配。
     */
    @GetMapping("/subscribed")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "我的订阅", description = "获取当前用户订阅的报告列表")
    public R<List<InsightReportVO>> listSubscribed() {
        return R.ok(reportService.listSubscribedReports());
    }

    /**
     * 报告详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "报告详情", description = "根据 ID 获取报告详情（含 HTML 报告内容）")
    public R<InsightReportVO> get(
            @Parameter(description = "报告 ID") @PathVariable Long id) {
        return R.ok(reportService.getReportDetail(id));
    }

    /**
     * 订阅报告
     */
    @PostMapping("/{id}/subscribe")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "订阅报告", description = "订阅指定报告，重复订阅会返回错误提示")
    public R<Void> subscribe(
            @Parameter(description = "报告 ID") @PathVariable Long id) {
        reportService.subscribeReport(id);
        return R.ok(null);
    }

    /**
     * 取消订阅报告
     */
    @DeleteMapping("/{id}/subscribe")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "取消订阅", description = "取消订阅指定报告")
    public R<Void> unsubscribe(
            @Parameter(description = "报告 ID") @PathVariable Long id) {
        reportService.unsubscribeReport(id);
        return R.ok(null);
    }

    /**
     * 删除报告
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "删除报告", description = "逻辑删除指定报告")
    public R<Void> delete(
            @Parameter(description = "报告 ID") @PathVariable Long id) {
        reportService.deleteReport(id);
        return R.ok(null);
    }
}
