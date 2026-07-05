package vip.mate.dataagent.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.model.SkillEntity;

/**
 * 技能资源归属校验守卫
 * <p>
 * 校验指定技能是否属于当前工作区，防止跨工作区越权访问。
 */
@Component
@RequiredArgsConstructor
public class SkillGuard {

    private final MateClawRuntime mateClawRuntime;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 校验技能是否属于当前工作区
     *
     * @param skillId 技能 ID
     * @throws BusinessException 当技能不存在或不属于当前工作区时抛出
     */
    public void requireSkillInCurrentWorkspace(Long skillId) {
        SkillEntity skill = mateClawRuntime.getSkill(skillId);
        if (skill == null) {
            throw new BusinessException(404, "技能不存在: " + skillId);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (skill.getWorkspaceId() == null
                || !skill.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该技能");
        }
    }
}
