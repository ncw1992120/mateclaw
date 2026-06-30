package vip.mate.dataagent.auth.permission;

import vip.mate.dataagent.constants.DataAgentConstants;

import java.util.EnumSet;
import java.util.Set;

/**
 * DataAgent 细粒度权限点定义及角色映射矩阵
 * <p>
 * 权限点采用 "资源:动作" 命名，与 {@link DataAgentConstants} 中的 PERM_* 常量一一对应。
 * <p>
 * 角色映射规则（第一性原理）：
 * <ul>
 *   <li>全局 admin（mate_user.role=admin）：自动放行所有权限点</li>
 *   <li>工作区角色权限累加：owner ≥ admin ≥ member ≥ viewer</li>
 *   <li>model:manage 仅全局 admin 拥有（模型表为全局表，无 workspace_id）</li>
 *   <li>datasource:create / datasource:manage：member 及以上可创建/管理自己的数据源</li>
 *   <li>datasource:sync：仅 admin 及以上可同步元数据</li>
 *   <li>*:manage 类权限：admin 及以上</li>
 *   <li>*:view 类权限：viewer 及以上</li>
 * </ul>
 */
public enum DataAgentPermission {

    // ==================== 模型配置 ====================
    /** 模型查看：所有工作区角色可用 */
    MODEL_VIEW(DataAgentConstants.PERM_MODEL_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    /** 模型管理：仅全局 admin（工作区角色均无权限） */
    MODEL_MANAGE(DataAgentConstants.PERM_MODEL_MANAGE,
            EnumSet.noneOf(WorkspaceRole.class)),

    // ==================== 技能配置 ====================
    SKILL_VIEW(DataAgentConstants.PERM_SKILL_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    SKILL_MANAGE(DataAgentConstants.PERM_SKILL_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),

    // ==================== 数据配置 ====================
    DATASOURCE_VIEW(DataAgentConstants.PERM_DATASOURCE_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    DATASOURCE_CREATE(DataAgentConstants.PERM_DATASOURCE_CREATE,
            EnumSet.of(WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    DATASOURCE_MANAGE(DataAgentConstants.PERM_DATASOURCE_MANAGE,
            EnumSet.of(WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    DATASOURCE_SYNC(DataAgentConstants.PERM_DATASOURCE_SYNC,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),

    // ==================== 业务词典 ====================
    BUSINESS_TERM_VIEW(DataAgentConstants.PERM_BUSINESS_TERM_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    BUSINESS_TERM_MANAGE(DataAgentConstants.PERM_BUSINESS_TERM_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),

    // ==================== 智能体配置 ====================
    AGENT_VIEW(DataAgentConstants.PERM_AGENT_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    AGENT_MANAGE(DataAgentConstants.PERM_AGENT_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),

    // ==================== 业务知识库 ====================
    KNOWLEDGE_VIEW(DataAgentConstants.PERM_KNOWLEDGE_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    KNOWLEDGE_MANAGE(DataAgentConstants.PERM_KNOWLEDGE_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),

    // ==================== 工作空间 ====================
    WORKSPACE_VIEW(DataAgentConstants.PERM_WORKSPACE_VIEW,
            EnumSet.of(WorkspaceRole.VIEWER, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    WORKSPACE_MANAGE(DataAgentConstants.PERM_WORKSPACE_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    WORKSPACE_MEMBER_VIEW(DataAgentConstants.PERM_WORKSPACE_MEMBER_VIEW,
            EnumSet.of(WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    WORKSPACE_MEMBER_MANAGE(DataAgentConstants.PERM_WORKSPACE_MEMBER_MANAGE,
            EnumSet.of(WorkspaceRole.ADMIN, WorkspaceRole.OWNER)),
    ;

    private final String code;
    /** 拥有该权限的工作区角色集合 */
    private final Set<WorkspaceRole> allowedRoles;

    DataAgentPermission(String code, Set<WorkspaceRole> allowedRoles) {
        this.code = code;
        this.allowedRoles = allowedRoles;
    }

    public String getCode() {
        return code;
    }

    /**
     * 判断指定工作区角色是否拥有该权限
     *
     * @param workspaceRole 工作区角色（owner/admin/member/viewer），null 视为无权限
     * @param isGlobalAdmin 是否为全局 admin（全局 admin 自动放行所有权限）
     * @return 是否放行
     */
    public boolean isAllowed(String workspaceRole, boolean isGlobalAdmin) {
        if (isGlobalAdmin) {
            return true;
        }
        if (workspaceRole == null) {
            return false;
        }
        WorkspaceRole role = WorkspaceRole.fromCode(workspaceRole);
        return role != null && allowedRoles.contains(role);
    }

    /**
     * 根据 permission code 解析枚举
     */
    public static DataAgentPermission fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataAgentPermission perm : values()) {
            if (perm.code.equals(code)) {
                return perm;
            }
        }
        return null;
    }

    /**
     * 工作区角色枚举（仅用于权限映射，不替代 DataAgentConstants 中的字符串常量）
     */
    public enum WorkspaceRole {
        VIEWER(DataAgentConstants.WORKSPACE_ROLE_VIEWER, 1),
        MEMBER(DataAgentConstants.WORKSPACE_ROLE_MEMBER, 2),
        ADMIN(DataAgentConstants.WORKSPACE_ROLE_ADMIN, 3),
        OWNER(DataAgentConstants.WORKSPACE_ROLE_OWNER, 4),
        ;

        private final String code;
        private final int level;

        WorkspaceRole(String code, int level) {
            this.code = code;
            this.level = level;
        }

        public String getCode() {
            return code;
        }

        public int getLevel() {
            return level;
        }

        public static WorkspaceRole fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (WorkspaceRole role : values()) {
                if (role.code.equalsIgnoreCase(code)) {
                    return role;
                }
            }
            return null;
        }
    }
}
