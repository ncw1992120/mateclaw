import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  fetchAgents: vi.fn().mockResolvedValue(undefined),
  fetchConversations: vi.fn().mockResolvedValue(undefined),
  tryResumeStream: vi.fn().mockResolvedValue(false),
  switchConversation: vi.fn().mockResolvedValue(undefined),
  fetchEnabledModels: vi.fn().mockResolvedValue(undefined),
  fetchProviders: vi.fn().mockResolvedValue(undefined),
  fetchActiveModel: vi.fn().mockResolvedValue(undefined),
  // 由 vue-router mock 工厂注入响应式 route（hoisted 阶段不能 import vue）
  route: undefined as undefined | { query: Record<string, string> },
}))

vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({ query: {} as Record<string, string> })
  mocks.route = route
  return { useRoute: () => route }
})

vi.mock('@/stores/useAgentStore', () => ({
  useAgentStore: () => ({ fetchAgents: mocks.fetchAgents }),
}))
vi.mock('@/stores/useChatStore', () => ({
  useChatStore: () => ({
    fetchConversations: mocks.fetchConversations,
    tryResumeStream: mocks.tryResumeStream,
    switchConversation: mocks.switchConversation,
    conversationId: '',
    isStreaming: false,
  }),
}))
vi.mock('@/stores/useModelStore', () => ({
  useModelStore: () => ({
    fetchEnabledModels: mocks.fetchEnabledModels,
    fetchProviders: mocks.fetchProviders,
    fetchActiveModel: mocks.fetchActiveModel,
  }),
}))
vi.mock('@/stores/useUserStore', () => ({
  useUserStore: () => ({ isAdmin: false }),
}))
vi.mock('../mainLayoutModelLoad', () => ({
  shouldLoadActiveModel: (nav: string) => nav !== 'insight',
}))

// 页面子组件与配置弹窗一律打桩：本用例只关心导航作用域的请求时机
vi.mock('../TopNavBar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../WorkbenchView.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../insight/DashboardListView.vue', () => ({
  default: { name: 'DashboardListViewStub', template: '<div />' },
}))
vi.mock('../../report/ReportListView.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../config/ConfigCenter.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../help/HelpCenterView.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../dialog/ModelConfigDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../dialog/AgentConfigDialog.vue', () => ({ default: { template: '<div />' } }))

import MainLayout from '../MainLayout.vue'
import { createI18n } from 'vue-i18n'

function mountLayout() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
  return mount(MainLayout, { global: { plugins: [i18n] } })
}

describe('MainLayout 导航作用域后台请求', () => {
  let wrapper: ReturnType<typeof mount> | undefined

  beforeEach(() => {
    mocks.route!.query = {}
    vi.clearAllMocks()
    mocks.fetchAgents.mockResolvedValue(undefined)
    mocks.fetchConversations.mockResolvedValue(undefined)
    mocks.tryResumeStream.mockResolvedValue(false)
    mocks.switchConversation.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('进入洞察不触发会话列表/历史消息恢复，也不加载智能体列表', async () => {
    mocks.route!.query = { nav: 'insight' }
    wrapper = mountLayout()
    await flushPromises()

    expect(mocks.fetchConversations).not.toHaveBeenCalled()
    expect(mocks.tryResumeStream).not.toHaveBeenCalled()
    expect(mocks.switchConversation).not.toHaveBeenCalled()
    expect(mocks.fetchAgents).not.toHaveBeenCalled()
  })

  it('进入问数仍执行原会话恢复流程', async () => {
    mocks.route!.query = { nav: 'smart-ask' }
    wrapper = mountLayout()
    await flushPromises()

    expect(mocks.fetchConversations).toHaveBeenCalledTimes(1)
    expect(mocks.tryResumeStream).toHaveBeenCalledTimes(1)
  })

  it('从洞察切换到问数时补齐会话初始化', async () => {
    mocks.route!.query = { nav: 'insight' }
    wrapper = mountLayout()
    await flushPromises()
    expect(mocks.fetchConversations).not.toHaveBeenCalled()

    mocks.route!.query = { nav: 'smart-ask' }
    await flushPromises()
    await flushPromises()

    expect(mocks.fetchConversations).toHaveBeenCalledTimes(1)
    expect(mocks.tryResumeStream).toHaveBeenCalledTimes(1)
  })

  it('快速切换回旧导航时，旧作用域未完成的恢复不再写会话状态', async () => {
    let releaseResume!: (value: boolean) => void
    mocks.tryResumeStream.mockReturnValue(new Promise<boolean>((resolve) => { releaseResume = resolve }))
    mocks.route!.query = { nav: 'smart-ask' }
    wrapper = mountLayout()
    await flushPromises()
    expect(mocks.fetchConversations).toHaveBeenCalledTimes(1)

    // 切走再切回：旧 token 失效
    mocks.route!.query = { nav: 'insight' }
    await flushPromises()
    releaseResume(true)
    await flushPromises()

    expect(mocks.switchConversation).not.toHaveBeenCalled()
  })
})
