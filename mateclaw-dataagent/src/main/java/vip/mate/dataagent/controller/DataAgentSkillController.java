package vip.mate.dataagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.model.SkillEntity;

import java.util.List;

/**
 * 技能管理控制器
 * <p>
 * 代理 mateclaw-server 的技能管理接口，供"智能问数"工作台使用。
 * 提供技能的分页查询、详情、创建、更新、删除、启停切换等能力。
 */
@RestController
@RequestMapping("/v1/skills")
@RequiredArgsConstructor
@Tag(name = "技能管理", description = "智能问数工作台技能管理接口（代理 mateclaw-server）")
public class DataAgentSkillController {

    private final MateClawRuntime runtime;

    /**
     * 技能分页列表
     */
    @GetMapping
    @Operation(summary = "技能分页列表", description = "按工作区查询技能分页数据，支持关键字、类型、启用状态过滤")
    public R<IPage<SkillEntity>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String skillType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String lifecycleState) {
        return R.ok(runtime.pageSkills(page, size, keyword, skillType, enabled, workspaceId, sort, lifecycleState));
    }

    /**
     * 技能列表（不分页）
     */
    @GetMapping("/all")
    @Operation(summary = "技能列表", description = "获取工作区下所有技能，不分页")
    public R<List<SkillEntity>> list(@RequestParam(required = false) Long workspaceId) {
        return R.ok(runtime.listSkills(workspaceId));
    }

    /**
     * 已启用技能列表
     */
    @GetMapping("/enabled")
    @Operation(summary = "已启用技能", description = "获取工作区下已启用的技能列表")
    public R<List<SkillEntity>> listEnabled(@RequestParam(required = false) Long workspaceId) {
        return R.ok(runtime.listEnabledSkills(workspaceId));
    }

    /**
     * 技能详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "技能详情", description = "根据 ID 获取技能详情")
    public R<SkillEntity> get(@PathVariable Long id) {
        return R.ok(runtime.getSkill(id));
    }

    /**
     * 创建技能
     */
    @PostMapping
    @Operation(summary = "创建技能", description = "新增技能配置")
    public R<SkillEntity> create(@RequestBody SkillEntity entity) {
        return R.ok(runtime.createSkill(entity));
    }

    /**
     * 更新技能
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新技能", description = "更新技能配置")
    public R<SkillEntity> update(@PathVariable Long id, @RequestBody SkillEntity entity) {
        entity.setId(id);
        return R.ok(runtime.updateSkill(entity));
    }

    /**
     * 硬删除技能
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除技能", description = "硬删除指定技能（不可恢复）")
    public R<Void> delete(@PathVariable Long id) {
        runtime.hardDeleteSkill(id);
        return R.ok();
    }

    /**
     * 切换技能启停状态
     */
    @PutMapping("/{id}/toggle")
    @Operation(summary = "启停切换", description = "启用或禁用指定技能")
    public R<SkillEntity> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return R.ok(runtime.toggleSkill(id, enabled));
    }
}
