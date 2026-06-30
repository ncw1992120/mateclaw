import { computed } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

/**
 * 权限点定义（与后端 DataAgentConstants.PERM_* 一一对应）
 *
 * 角色映射规则（第一性原理）：
 * - 全局 admin（role=admin）：自动放行所有权限点
 * - 工作区角色权限累加：owner ≥ admin ≥ member ≥ viewer
 * - model:manage 仅全局 admin（模型表为全局表）
 * - datasource:create/manage：member 及以上（创建/管理自己的数据源）
 * - datasource:sync：仅 admin 及以上
 * - *:manage 类权限：admin 及以上
 * - *:view 类权限：viewer 及以上
 */
export const PERMISSION = {
  // 模型配置
  MODEL_VIEW: 'model:view',
  MODEL_MANAGE: 'model:manage',
  // 技能配置
  SKILL_VIEW: 'skill:view',
  SKILL_MANAGE: 'skill:manage',
  // 数据配置
  DATASOURCE_VIEW: 'datasource:view',
  DATASOURCE_CREATE: 'datasource:create',
  DATASOURCE_MANAGE: 'datasource:manage',
  DATASOURCE_SYNC: 'datasource:sync',
  // 业务词典
  BUSINESS_TERM_VIEW: 'business-term:view',
  BUSINESS_TERM_MANAGE: 'business-term:manage',
  // 智能体配置
  AGENT_VIEW: 'agent:view',
  AGENT_MANAGE: 'agent:manage',
  // 业务知识库
  KNOWLEDGE_VIEW: 'knowledge:view',
  KNOWLEDGE_MANAGE: 'knowledge:manage',
  // 工作空间
  WORKSPACE_VIEW: 'workspace:view',
  WORKSPACE_MANAGE: 'workspace:manage',
  WORKSPACE_MEMBER_VIEW: 'workspace:member:view',
  WORKSPACE_MEMBER_MANAGE: 'workspace:member:manage',
} as const

export type PermissionCode = (typeof PERMISSION)[keyof typeof PERMISSION]

/**
 * 权限点 → 允许的工作区角色集合
 * 全局 admin 自动放行所有权限点，此处仅定义工作区角色映射
 */
const PERMISSION_ROLE_MAP: Record<string, string[]> = {
  // 模型：manage 仅全局 admin（工作区角色为空数组）
  [PERMISSION.MODEL_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.MODEL_MANAGE]: [],
  // 技能
  [PERMISSION.SKILL_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.SKILL_MANAGE]: ['admin', 'owner'],
  // 数据源
  [PERMISSION.DATASOURCE_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.DATASOURCE_CREATE]: ['member', 'admin', 'owner'],
  [PERMISSION.DATASOURCE_MANAGE]: ['member', 'admin', 'owner'],
  [PERMISSION.DATASOURCE_SYNC]: ['admin', 'owner'],
  // 业务词典
  [PERMISSION.BUSINESS_TERM_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.BUSINESS_TERM_MANAGE]: ['admin', 'owner'],
  // 智能体
  [PERMISSION.AGENT_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.AGENT_MANAGE]: ['admin', 'owner'],
  // 知识库
  [PERMISSION.KNOWLEDGE_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.KNOWLEDGE_MANAGE]: ['admin', 'owner'],
  // 工作空间
  [PERMISSION.WORKSPACE_VIEW]: ['viewer', 'member', 'admin', 'owner'],
  [PERMISSION.WORKSPACE_MANAGE]: ['admin', 'owner'],
  [PERMISSION.WORKSPACE_MEMBER_VIEW]: ['member', 'admin', 'owner'],
  [PERMISSION.WORKSPACE_MEMBER_MANAGE]: ['admin', 'owner'],
}

/**
 * 统一权限判断 composable
 *
 * 使用示例：
 * ```ts
 * const { hasPermission } = usePermission()
 * if (hasPermission(PERMISSION.MODEL_MANAGE)) { ... }
 * ```
 */
export function usePermission() {
  const userStore = useUserStore()

  /** 当前用户在工作区的生效角色（全局 admin 视为 owner） */
  const effectiveRole = computed<string | null>(() => {
    if (userStore.isAdmin) {
      return 'owner'
    }
    return userStore.currentWorkspace?.effectiveRole ?? userStore.currentWorkspace?.memberRole ?? null
  })

  /**
   * 判断当前用户是否拥有指定权限点
   * @param code 权限点 code
   * @returns true 如果有权限
   */
  function hasPermission(code: string): boolean {
    // 全局 admin 自动放行
    if (userStore.isAdmin) {
      return true
    }
    const allowedRoles = PERMISSION_ROLE_MAP[code]
    if (!allowedRoles) {
      return false
    }
    if (!effectiveRole.value) {
      return false
    }
    return allowedRoles.includes(effectiveRole.value)
  }

  /**
   * 判断当前用户是否拥有任意一个权限点
   * @param codes 权限点 code 数组
   * @returns true 如果有任意一个权限
   */
  function hasAnyPermission(codes: string[]): boolean {
    return codes.some((code) => hasPermission(code))
  }

  /**
   * 判断当前用户是否拥有全部权限点
   * @param codes 权限点 code 数组
   * @returns true 如果有全部权限
   */
  function hasAllPermissions(codes: string[]): boolean {
    return codes.every((code) => hasPermission(code))
  }

  return {
    effectiveRole,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
  }
}
