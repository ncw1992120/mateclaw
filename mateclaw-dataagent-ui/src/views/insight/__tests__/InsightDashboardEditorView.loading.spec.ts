import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import InsightDashboardEditorView from '../InsightDashboardEditorView.vue'

const mocks = vi.hoisted(() => ({
  selectDashboard: vi.fn(),
  updateDashboard: vi.fn(),
  restoreResultSetData: vi.fn(),
  setDashboard: (_dashboard: any) => undefined,
}))

vi.mock('@/stores/useInsightDashboardStore', async () => {
  const { ref } = await import('vue')
  const currentDashboard = ref<any>(null)
  mocks.setDashboard = (dashboard) => { currentDashboard.value = dashboard }
  return {
    useInsightDashboardStore: () => ({
      get currentDashboard() { return currentDashboard.value },
      selectDashboard: mocks.selectDashboard,
      updateDashboard: mocks.updateDashboard,
    }),
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
}))

vi.mock('@/stores/useUserStore', () => ({
  useUserStore: () => ({ token: 'test-token', userId: 'owner-1', fetchCurrentUser: vi.fn() }),
}))

vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ canModifyResource: () => true }),
}))

vi.mock('../components/card-attribute/useInsight', () => ({
  useInsight: () => ({
    openMetricStyle: vi.fn(),
    state: { datasets: [], cards: [], activeCardId: '', filterCatalog: [], ui: {
      dataDialog: { datasetId: '' }, queryConfigDialog: { datasetId: '' },
    }, backend: { componentId: '' } },
    saveQueryConfig: vi.fn(),
    getLoadedDashboardSchema: vi.fn(() => null),
  }),
}))

vi.mock('../composables/useResultSetRestore', () => ({
  toComponentData: vi.fn(),
  restoreResultSetData: mocks.restoreResultSetData,
}))

vi.mock('@/api/insight-dashboard', () => ({
  saveAsTemplate: vi.fn(),
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function dashboardRecord(schema: unknown) {
  return {
    id: 'dashboard-1', name: '性能测试看板', description: '加载验证', ownerName: '测试用户', ownerId: 'owner-1',
    schemaJson: JSON.stringify(schema),
  }
}

async function mountEditor() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
  return mount(InsightDashboardEditorView, {
    props: { dashboardId: 'dashboard-1' },
    global: {
      plugins: [i18n],
      mocks: {
        $route: { query: {} },
        $router: { push: vi.fn(), replace: vi.fn(), back: vi.fn() },
      },
      stubs: {
        ComponentPalette: true,
        DashboardCanvas: {
          props: ['components', 'dashboardTheme'],
          template: '<div data-test="canvas" :data-component-count="components.length" :data-theme="dashboardTheme.presetId" :data-filter-binding="components[1]?.config?.filterBindings?.[0]">canvas</div>',
        },
        PropertyPanel: true,
        CardAttributeSidebar: true,
        DatasetDataDialog: true,
        QueryConfigDialog: true,
        AiChatPanel: true,
        PanelFloatButton: true,
        DashboardThemePanel: true,
        ElButton: { template: '<button :disabled="disabled"><slot /></button>', props: ['disabled', 'loading'] },
        ElInput: true, ElIcon: true,
        ElTree: { template: '<div />', methods: { setCurrentKey: vi.fn() } },
        ElDropdown: true, ElDropdownMenu: true, ElDropdownItem: true,
        ElDialog: true, ElDrawer: true, ElMessage: true,
      },
    },
  })
}

describe('InsightDashboardEditorView 加载态', () => {
  let wrapper: ReturnType<typeof mount> | undefined

  beforeEach(() => {
    mocks.setDashboard(null)
    mocks.selectDashboard.mockReset()
    mocks.updateDashboard.mockReset()
    mocks.restoreResultSetData.mockReset().mockResolvedValue({})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('详情延迟时先显示编辑器外壳和加载态，详情到达后恢复页面、组件、主题和筛选绑定', async () => {
    const pending = deferred<void>()
    mocks.selectDashboard.mockReturnValue(pending.promise)
    wrapper = await mountEditor()
    await flushPromises()

    expect(wrapper.get('.editor-toolbar').exists()).toBe(true)
    expect(wrapper.get('.editor-canvas-state[role="status"]').text()).toContain('insight.loadingSchema')
    expect(wrapper.find('[data-test="canvas"]').exists()).toBe(false)
    expect(wrapper.get('button').attributes('aria-label')).toBe('common.back')

    mocks.setDashboard(dashboardRecord({
      version: '1.1',
      pages: [{
        id: 'page-1', name: '首页', components: [
          { id: 'filter-1', type: 'filter', title: '渠道筛选', position: { x: 0, y: 0, w: 2, h: 1 }, config: { field: 'channel', defaultValue: 'APP' } },
          { id: 'table-1', type: 'table', title: '明细表', position: { x: 0, y: 1, w: 4, h: 3 }, config: { filterBindings: ['filter-1'] } },
        ],
      }],
      datasetInputs: [],
      theme: { presetId: 'indigo' },
    }))
    pending.resolve()
    await flushPromises()

    expect(wrapper.find('.editor-canvas-state').exists()).toBe(false)
    expect(wrapper.get('[data-test="canvas"]').attributes('data-component-count')).toBe('2')
    expect(wrapper.get('[data-test="canvas"]').attributes('data-theme')).toBe('indigo')
    expect(wrapper.get('[data-test="canvas"]').attributes('data-filter-binding')).toBe('filter-1')
    expect(mocks.restoreResultSetData).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ id: 'filter-1' }),
      expect.objectContaining({ id: 'table-1' }),
    ]))
  })

  it('详情请求失败时显示可理解错误态和返回入口', async () => {
    mocks.selectDashboard.mockRejectedValue(new Error('network unavailable'))
    wrapper = await mountEditor()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('insight.loadFailed')
    expect(wrapper.get('.editor-canvas-state button').text()).toContain('common.back')
    expect(wrapper.find('[data-test="canvas"]').exists()).toBe(false)
  })
})
