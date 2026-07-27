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
 * 提供报告的发布、查询和删除 API，报告内容从仪表盘复制而来，
 * 独立存储在 dataagent_insight_report 表中。
 */
@RestController
@RequestMapping("/v1/insight/reports")
@RequiredArgsConstructor
@Tag(name = "洞察报告", description = "报告发布、查询与删除接口")
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
     * 报告列表
     * <p>
     * 查询当前工作区的所有已发布报告，按更新时间降序排列。
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "报告列表", description = "获取当前工作区的所有已发布报告")
    public R<List<InsightReportVO>> list() {
        return R.ok(reportService.listReports());
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
