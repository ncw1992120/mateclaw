package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.BusinessTermCreateRequest;
import vip.mate.dataagent.dto.BusinessTermReferenceOptions;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermUpdateRequest;
import vip.mate.dataagent.dto.BusinessTermVO;
import vip.mate.dataagent.service.BusinessTermService;

import java.util.List;

/**
 * 业务术语管理控制器
 * <p>
 * 提供术语的 CRUD、关键词搜索等 API。
 */
@RestController
@RequestMapping("/v1/business-terms")
@RequiredArgsConstructor
@Tag(name = "业务术语管理", description = "术语与同义词的 CRUD、搜索接口")
public class DataAgentBusinessTermController {

    private final BusinessTermService businessTermService;

    /**
     * 列出所有已存在术语数据的租户编码
     */
    @GetMapping("/tenants")
    @Operation(summary = "列出租户编码", description = "返回所有已存在术语数据的租户编码（去重）")
    public R<List<String>> listTenants() {
        return R.ok(businessTermService.listTenantCodes());
    }

    /**
     * 按租户查询术语（默认仅启用；includeDisabled=true 时包含停用术语，供管理界面展示）
     */
    @GetMapping
    @Operation(summary = "查询术语列表", description = "按租户编码查询术语，可按类目筛选；includeDisabled=true 时包含停用术语")
    public R<List<BusinessTermVO>> list(
            @Parameter(description = "租户编码") @RequestParam String tenantCode,
            @Parameter(description = "类目（可选）") @RequestParam(required = false) String category,
            @Parameter(description = "是否包含停用术语") @RequestParam(defaultValue = "false") boolean includeDisabled) {
        if (category != null && !category.isBlank()) {
            return R.ok(businessTermService.listByTenantCodeAndCategory(tenantCode, category, includeDisabled));
        }
        return R.ok(businessTermService.listByTenantCode(tenantCode, includeDisabled));
    }

    /**
     * 获取术语详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取术语详情", description = "根据 ID 获取术语详情")
    public R<BusinessTermVO> get(
            @Parameter(description = "术语 ID") @PathVariable Long id) {
        return R.ok(businessTermService.getById(id));
    }

    /**
     * 创建术语
     */
    @PostMapping
    @Operation(summary = "创建术语", description = "创建新的业务术语")
    public R<BusinessTermVO> create(@RequestBody BusinessTermCreateRequest request) {
        return R.ok(businessTermService.create(request));
    }

    /**
     * 更新术语
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新术语", description = "更新术语信息")
    public R<BusinessTermVO> update(
            @Parameter(description = "术语 ID") @PathVariable Long id,
            @RequestBody BusinessTermUpdateRequest request) {
        return R.ok(businessTermService.update(id, request));
    }

    /**
     * 按租户删除所有术语
     */
    @DeleteMapping
    @Operation(summary = "按租户删除术语", description = "删除指定租户下的所有术语（逻辑删除）")
    public R<Void> deleteByTenantCode(
            @Parameter(description = "租户编码") @RequestParam String tenantCode) {
        businessTermService.deleteByTenantCode(tenantCode);
        return R.ok(null);
    }

    /**
     * 删除术语
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除术语", description = "删除指定术语")
    public R<Void> delete(
            @Parameter(description = "术语 ID") @PathVariable Long id) {
        businessTermService.delete(id);
        return R.ok(null);
    }

    /**
     * 启用术语
     */
    @PutMapping("/{id}/enable")
    @Operation(summary = "启用术语", description = "启用指定术语")
    public R<Void> enable(
            @Parameter(description = "术语 ID") @PathVariable Long id) {
        businessTermService.enable(id);
        return R.ok(null);
    }

    /**
     * 停用术语
     */
    @PutMapping("/{id}/disable")
    @Operation(summary = "停用术语", description = "停用指定术语")
    public R<Void> disable(
            @Parameter(description = "术语 ID") @PathVariable Long id) {
        businessTermService.disable(id);
        return R.ok(null);
    }

    /**
     * 关键词搜索术语
     */
    @GetMapping("/search")
    @Operation(summary = "关键词搜索", description = "在术语名、同义词、描述、分类等字段中搜索")
    public R<List<BusinessTermVO>> search(
            @Parameter(description = "租户编码") @RequestParam String tenantCode,
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        return R.ok(businessTermService.searchByKeyword(tenantCode, keyword));
    }

    /**
     * 语义混合检索术语
     */
    @GetMapping("/semantic-search")
    @Operation(summary = "语义混合检索", description = "使用 ES 关键词+向量语义混合检索（RRF融合），ES不可用时降级为MySQL LIKE查询")
    public R<BusinessTermSearchResult> semanticSearch(
            @Parameter(description = "搜索关键词") @RequestParam String query,
            @Parameter(description = "返回结果数量上限") @RequestParam(defaultValue = "10") int topK,
            @Parameter(description = "向量语义检索相似度阈值") @RequestParam(defaultValue = "0.3") double threshold) {
        return R.ok(businessTermService.semanticSearch(query, topK, threshold));
    }

    /**
     * 查询关联引用候选（跨数据源的指标 / 维度）
     */
    @GetMapping("/reference-options")
    @Operation(summary = "查询关联引用候选", description = "跨数据源检索指标/维度候选列表，供术语关联指标/维度时选择")
    public R<BusinessTermReferenceOptions> referenceOptions(
            @Parameter(description = "搜索关键词（可选，按名称/展示名/同义词模糊匹配）") @RequestParam(required = false) String keyword,
            @Parameter(description = "返回数量上限") @RequestParam(defaultValue = "20") int limit) {
        return R.ok(businessTermService.listReferenceOptions(keyword, limit));
    }

    /**
     * 为租户下的所有术语生成嵌入向量并写入 ES 索引
     */
    @PostMapping("/embed")
    @Operation(summary = "向量化并索引", description = "为租户下所有术语生成嵌入向量并写入ES索引，支持语义检索")
    public R<Integer> embedAndIndex(
            @Parameter(description = "租户编码") @RequestParam String tenantCode) {
        return R.ok(businessTermService.embedAndIndexAll(tenantCode));
    }

    /**
     * 重建租户的术语 ES 索引
     */
    @PostMapping("/rebuild-es")
    @Operation(summary = "重建ES索引", description = "从MySQL已同步数据重新向量化并写入ES")
    public R<Integer> rebuildEsIndex(
            @Parameter(description = "租户编码") @RequestParam String tenantCode) {
        return R.ok(businessTermService.rebuildEsIndex(tenantCode));
    }
}
