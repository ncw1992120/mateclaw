package vip.mate.sdk.service.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import vip.mate.agent.AgentService;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.agent.binding.service.AgentBindingService;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.WikiRuntime;
import vip.mate.sdk.service.tool.ToolRuntime;
import vip.mate.tool.ToolRegistry;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.tool.model.ToolEntity;
import vip.mate.tool.repository.ToolMapper;
import vip.mate.tool.service.AvailableToolService;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;

import java.util.List;

/**
 * 工具运行时实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRuntimeImpl implements ToolRuntime {

    private final ToolRegistry toolRegistry;
    private final ToolMapper toolMapper;
    private final AgentBindingService agentBindingService;
    private final AgentService agentService;
    private final AvailableToolService availableToolService;
    private final WikiRuntime wikiRuntime;

    /**
     * 注册插件工具，可用性检查默认始终返回 true
     */
    @Override
    public void registerTool(ToolCallback tool) {
        toolRegistry.registerPluginTool(tool, () -> true);
    }

    /**
     * 按 Spring Bean 名称禁用内置 @Tool Bean。
     * <p>
     * 通过向 mate_tool 表写入或更新一条 enabled=false 的记录，使 ToolRegistry 在构建
     * AgentToolSet 时跳过该 Bean。若已存在同 beanName 的记录则更新为禁用状态，否则插入新记录。
     */
    @Override
    public void disableBuiltinToolByBeanName(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            throw new MateClawException("err.tool.bean_name_blank", "beanName 不能为空");
        }
        ToolEntity existing = toolMapper.selectOne(
                new LambdaQueryWrapper<ToolEntity>().eq(ToolEntity::getBeanName, beanName));
        if (existing != null) {
            if (Boolean.FALSE.equals(existing.getEnabled())) {
                log.debug("Builtin tool already disabled, skip: beanName={}", beanName);
                return;
            }
            existing.setEnabled(false);
            toolMapper.updateById(existing);
            log.info("Disabled builtin tool: beanName={}, id={}", beanName, existing.getId());
            return;
        }
        ToolEntity entity = new ToolEntity();
        entity.setName(beanName);
        entity.setBeanName(beanName);
        entity.setToolType("builtin");
        entity.setEnabled(false);
        entity.setBuiltin(true);
        toolMapper.insert(entity);
        log.info("Disabled builtin tool by inserting mate_tool row: beanName={}", beanName);
    }

    @Override
    public List<Object> getEnabledTools() {
        return toolRegistry.getEnabledTools();
    }

    @Override
    public List<AgentSkillBinding> listAgentSkillBindings(Long agentId) {
        return agentBindingService.listSkillBindings(agentId);
    }

    @Override
    public void setAgentSkillBindings(Long agentId, List<Long> skillIds) {
        agentBindingService.setSkillBindings(agentId, skillIds);
        agentService.invalidateAgentCache(agentId);
    }

    @Override
    public List<AgentToolBinding> listAgentToolBindings(Long agentId) {
        return agentBindingService.listToolBindings(agentId);
    }

    @Override
    public void setAgentToolBindings(Long agentId, List<String> toolNames) {
        agentBindingService.setToolBindings(agentId, toolNames);
        agentService.invalidateAgentCache(agentId);
    }

    @Override
    public List<AgentProviderPreference> listAgentProviderPreferences(Long agentId) {
        return agentBindingService.listProviderPreferences(agentId);
    }

    @Override
    public void setAgentProviderPreferences(Long agentId, List<String> providerIds) {
        agentBindingService.setProviderPreferences(agentId, providerIds);
        agentService.invalidateAgentCache(agentId);
    }

    @Override
    public List<AvailableToolDTO> listAvailableTools() {
        return availableToolService.listAvailable();
    }

    @Override
    public List<WikiKnowledgeBaseEntity> listBindableKnowledgeBases(Long workspaceId) {
        return wikiRuntime.listKBsByWorkspace(workspaceId);
    }
}
