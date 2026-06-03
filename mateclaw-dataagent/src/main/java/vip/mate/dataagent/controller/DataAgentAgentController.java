package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.agent.model.AgentEntity;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.ApplyTemplateRequest;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;

/**
 * Agent 管理控制器
 * <p>
 * 提供 Agent CRUD 和模板应用 API。
 */
@RestController
@RequestMapping("/v1/agents")
@RequiredArgsConstructor
@Tag(name = "Agent 管理", description = "数据分析 Agent 管理接口")
public class DataAgentAgentController {

    private final MateClawRuntime runtime;

    /**
     * Agent 列表
     */
    @GetMapping
    @Operation(summary = "Agent 列表", description = "按工作区列出 Agent")
    public R<List<AgentEntity>> list(@RequestParam(defaultValue = "1") Long workspaceId,
                                     @RequestParam(required = false) Boolean enabled) {
        return R.ok(runtime.listAgentsByWorkspace(workspaceId, enabled));
    }

    /**
     * Agent 详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "Agent 详情", description = "根据 ID 获取 Agent 详情")
    public R<AgentEntity> get(@PathVariable Long id) {
        return R.ok(runtime.getAgent(id));
    }

    /**
     * 创建 Agent
     */
    @PostMapping
    @Operation(summary = "创建 Agent", description = "新增 Agent 配置")
    public R<AgentEntity> create(@RequestBody AgentEntity agent) {
        return R.ok(runtime.createAgent(agent));
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent", description = "更新 Agent 配置")
    public R<AgentEntity> update(@PathVariable Long id, @RequestBody AgentEntity agent) {
        agent.setId(id);
        return R.ok(runtime.updateAgent(agent));
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent", description = "删除指定 Agent")
    public R<Void> delete(@PathVariable Long id) {
        runtime.deleteAgent(id);
        return R.ok(null);
    }

    /**
     * 应用模板
     */
    @PostMapping("/apply-template")
    @Operation(summary = "应用模板", description = "从模板创建 Agent")
    public R<AgentEntity> applyTemplate(@RequestBody ApplyTemplateRequest req) {
        return R.ok(runtime.applyTemplate(req.getTemplateId(), req.getWorkspaceId(), null));
    }
}