package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.service.HelpCenterService;

import java.util.List;

/**
 * 帮助中心控制器
 * <p>
 * 提供帮助文档分类管理和文档 CRUD API。
 */
@RestController
@RequestMapping("/v1/help-center")
@RequiredArgsConstructor
@Tag(name = "帮助中心", description = "帮助文档分类管理与文档 CRUD 接口")
public class DataAgentHelpCenterController {

    private final HelpCenterService helpCenterService;

    /**
     * 获取分类树
     */
    @GetMapping("/categories/tree")
    @Operation(summary = "获取分类树", description = "获取帮助文档分类树形结构")
    public R<List<HelpCategoryVO>> listCategoryTree() {
        return R.ok(helpCenterService.listCategoryTree());
    }

    /**
     * 创建分类
     */
    @PostMapping("/categories")
    @Operation(summary = "创建分类", description = "创建帮助文档分类")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpCategoryVO> createCategory(@RequestBody HelpCategoryRequest request) {
        return R.ok(helpCenterService.createCategory(request));
    }

    /**
     * 更新分类
     */
    @PutMapping("/categories/{id}")
    @Operation(summary = "更新分类", description = "更新帮助文档分类信息")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpCategoryVO> updateCategory(
            @Parameter(description = "分类 ID") @PathVariable Long id,
            @RequestBody HelpCategoryRequest request) {
        return R.ok(helpCenterService.updateCategory(id, request));
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除分类", description = "删除帮助文档分类及其下的文档")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> deleteCategory(
            @Parameter(description = "分类 ID") @PathVariable Long id) {
        helpCenterService.deleteCategory(id);
        return R.ok(null);
    }

    /**
     * 批量排序分类
     */
    @PostMapping("/categories/reorder")
    @Operation(summary = "批量排序分类", description = "根据传入的分类 ID 顺序更新排序号")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> reorderCategories(@RequestBody HelpReorderRequest request) {
        helpCenterService.reorderCategories(request.getIds());
        return R.ok(null);
    }

    /**
     * 获取分类下的文档列表
     */
    @GetMapping("/categories/{categoryId}/documents")
    @Operation(summary = "获取文档列表", description = "获取指定分类下的帮助文档列表")
    public R<List<HelpDocumentVO>> listDocuments(
            @Parameter(description = "分类 ID") @PathVariable Long categoryId) {
        return R.ok(helpCenterService.listDocuments(categoryId));
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    @Operation(summary = "获取文档详情", description = "根据 ID 获取帮助文档详情（含 Markdown 内容）")
    public R<HelpDocumentVO> getDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {
        return R.ok(helpCenterService.getDocument(id));
    }

    /**
     * 创建文档
     */
    @PostMapping("/documents")
    @Operation(summary = "创建文档", description = "创建帮助文档")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpDocumentVO> createDocument(@RequestBody HelpDocumentRequest request) {
        return R.ok(helpCenterService.createDocument(request));
    }

    /**
     * 更新文档
     */
    @PutMapping("/documents/{id}")
    @Operation(summary = "更新文档", description = "更新帮助文档内容")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpDocumentVO> updateDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id,
            @RequestBody HelpDocumentRequest request) {
        return R.ok(helpCenterService.updateDocument(id, request));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除文档", description = "删除帮助文档")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> deleteDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {
        helpCenterService.deleteDocument(id);
        return R.ok(null);
    }

    /**
     * 批量排序分类下的文档
     */
    @PostMapping("/categories/{categoryId}/documents/reorder")
    @Operation(summary = "批量排序文档", description = "根据传入的文档 ID 顺序更新分类下文档排序号")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> reorderDocuments(
            @Parameter(description = "分类 ID") @PathVariable Long categoryId,
            @RequestBody HelpReorderRequest request) {
        helpCenterService.reorderDocuments(categoryId, request.getIds());
        return R.ok(null);
    }

    /**
     * 发布文档
     */
    @PostMapping("/documents/{id}/publish")
    @Operation(summary = "发布文档", description = "将文档状态变更为已发布")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpDocumentVO> publishDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {
        return R.ok(helpCenterService.publishDocument(id));
    }

    /**
     * 取消发布文档
     */
    @PostMapping("/documents/{id}/unpublish")
    @Operation(summary = "取消发布文档", description = "将文档状态变更为草稿")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<HelpDocumentVO> unpublishDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {
        return R.ok(helpCenterService.unpublishDocument(id));
    }

    /**
     * 搜索文档
     */
    @GetMapping("/documents/search")
    @Operation(summary = "搜索文档", description = "根据关键字搜索帮助文档，支持标题和内容模糊匹配")
    public R<List<HelpSearchResultVO>> searchDocuments(
            @Parameter(description = "搜索关键字") @RequestParam String keyword,
            @Parameter(description = "返回条数") @RequestParam(required = false) Integer limit) {
        return R.ok(helpCenterService.searchDocuments(keyword, limit));
    }

    /**
     * 获取相关文档推荐
     */
    @GetMapping("/documents/{id}/related")
    @Operation(summary = "获取相关文档", description = "获取与指定文档相关的推荐文档列表")
    public R<List<HelpDocumentVO>> getRelatedDocuments(
            @Parameter(description = "文档 ID") @PathVariable Long id,
            @Parameter(description = "返回条数") @RequestParam(required = false) Integer limit) {
        return R.ok(helpCenterService.getRelatedDocuments(id, limit));
    }

    /**
     * 提交文档反馈
     */
    @PostMapping("/documents/{id}/feedback")
    @Operation(summary = "提交文档反馈", description = "对帮助文档提交评分和改进建议")
    public R<HelpFeedbackVO> submitFeedback(
            @Parameter(description = "文档 ID") @PathVariable Long id,
            @RequestBody HelpFeedbackRequest request) {
        return R.ok(helpCenterService.submitFeedback(id, request));
    }

    /**
     * 获取文档反馈汇总
     */
    @GetMapping("/documents/{id}/feedback/summary")
    @Operation(summary = "获取反馈汇总", description = "获取指定文档的反馈评分汇总信息")
    public R<HelpFeedbackSummaryVO> getFeedbackSummary(
            @Parameter(description = "文档 ID") @PathVariable Long id) {
        return R.ok(helpCenterService.getFeedbackSummary(id));
    }
}
