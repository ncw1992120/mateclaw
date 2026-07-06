import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { CurrentUserInfo, LoginResponse, Workspace } from '@/types'

/** 用户认证状态管理 */
export const useUserStore = defineStore('user', () => {
  /** JWT 令牌 */
  const token = ref<string | null>(localStorage.getItem('token'))
  /** 用户 ID */
  const userId = ref<number | string | null>(null)
  /** 用户名 */
  const username = ref<string>('')
  /** 昵称 */
  const nickname = ref<string>('')
  /** 全局角色 */
  const role = ref<string>('')
  /** 可见工作区列表 */
  const workspaces = ref<Workspace[]>([])
  /** 当前工作区 ID */
  const currentWorkspaceId = ref<number | string | null>(
    localStorage.getItem('workspaceId')
      ? JSON.parse(localStorage.getItem('workspaceId')!)
      : null
  )

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)

  /** 是否为全局管理员 */
  const isAdmin = computed(() => role.value === 'admin')

  /** 当前工作区 */
  const currentWorkspace = computed(() => {
    return workspaces.value.find((w) => w.id === currentWorkspaceId.value) || null
  })

  /**
   * 应用登录响应数据到状态
   */
  function applyLoginResponse(data: LoginResponse | CurrentUserInfo): void {
    token.value = data.token
    userId.value = data.id
    username.value = data.username
    nickname.value = data.nickname
    role.value = data.role
    workspaces.value = data.workspaces || []

    if (data.token) {
      localStorage.setItem('token', data.token)
    }

    // 登录时校验 workspaceId 是否属于当前用户，不匹配则重置为第一个工作区
    // 防止切换用户后 localStorage 残留旧 workspaceId 导致权限校验失败
    const wsExists = workspaces.value.some((w) => w.id === currentWorkspaceId.value)
    if (workspaces.value.length > 0 && !wsExists) {
      setCurrentWorkspace(workspaces.value[0].id)
    } else if (workspaces.value.length === 0) {
      currentWorkspaceId.value = null
      localStorage.removeItem('workspaceId')
    }
  }

  /**
   * 用户登录
   */
  async function login(user: string, password: string): Promise<void> {
    const data = await authApi.login({ username: user, password })
    applyLoginResponse(data as unknown as LoginResponse)
  }

  /**
   * 从后端恢复当前用户信息（页面刷新时调用）
   */
  async function fetchCurrentUser(): Promise<boolean> {
    if (!token.value) {
      return false
    }
    try {
      const data = await authApi.getCurrentUser()
      applyLoginResponse(data as unknown as CurrentUserInfo)
      return true
    } catch {
      logout()
      return false
    }
  }

  /**
   * 切换当前工作区
   */
  function setCurrentWorkspace(workspaceId: number | string): void {
    currentWorkspaceId.value = workspaceId
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }

  /**
   * 清除所有 mc- 前缀的 localStorage（聊天会话等业务状态），防止切换用户后脏数据
   */
  function clearMcLocalStorage(): void {
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key && key.startsWith('mc-')) {
        keysToRemove.push(key)
      }
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k))
  }

  /**
   * 退出登录
   */
  function logout(): void {
    token.value = null
    userId.value = null
    username.value = ''
    nickname.value = ''
    role.value = ''
    workspaces.value = []
    currentWorkspaceId.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('workspaceId')
    // 清除所有 mc- 前缀的业务状态（聊天会话、Agent 选择、模型选择、数据源选择等）
    clearMcLocalStorage()
  }

  return {
    token,
    userId,
    username,
    nickname,
    role,
    workspaces,
    currentWorkspaceId,
    isLoggedIn,
    isAdmin,
    currentWorkspace,
    login,
    fetchCurrentUser,
    setCurrentWorkspace,
    logout,
  }
})
