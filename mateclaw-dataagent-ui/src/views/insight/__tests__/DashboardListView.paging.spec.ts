import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  listSummary: vi.fn(),
  create: vi.fn(),
  remove: vi.fn(),
  selectDashboard: vi.fn(),
  push: vi.fn(),
  canModifyResource: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/api/insight-dashboard', () => ({
  listSummary: mocks.listSummary,
  create: mocks.create,
  selectDashboard: mocks.selectDashboard,
  list: vi.fn(),
  get: vi.fn(),
  update: vi.fn(),
  remove: mocks.remove,
  copy: vi.fn(),
  saveAsTemplate: vi.fn(),
  streamAiChat: vi.fn(),
}))

const push = mocks.push
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => true, canModifyResource: mocks.canModifyResource }),
  PERMISSION: { INSIGHT_CREATE: 'insight:create' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm },
}))

vi.mock('@/stores/useUserStore', () => ({
  useUserStore: () => ({ token: 'token', userId: 1 }),
}))

vi.mock('./components/AiChatPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('./DashboardPreviewView.vue', () => ({ default: { template: '<div />' } }))

import DashboardListView from '../DashboardListView.vue'

/** 构造一页摘要响应 */
function page(records: Array<{ id: string; name: string; status?: string; visibility?: string; ownerId?: number; templateMeta?: string }>, total: number, pageNo: number, size = 2) {
  return {
    records: records.map((item) => ({ ...item, status: item.status ?? 'draft', visibility: item.visibility ?? 'private', chartKind: 'bar' })),
    total,
    page: pageNo,
    size,
    counts: { all: total, draft: total, published: 0 },
  }
}

describe('DashboardListView 服务端分页摘要', () => {
  let wrapper: ReturnType<typeof mount> | undefined

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    mocks.listSummary.mockReset()
    mocks.create.mockReset()
    mocks.remove.mockReset().mockResolvedValue(undefined)
    mocks.canModifyResource.mockReset().mockReturnValue(true)
    mocks.confirm.mockReset().mockResolvedValue(true)
    mocks.push.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountList() {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardListView, {
      global: {
        plugins: [i18n],
        stubs: {
          DashboardCanvas: true,
          ElButton: { template: '<button><slot /></button>' },
          ElInput: {
            props: ['modelValue'],
            template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          ElTag: { template: '<span><slot /></span>' },
          ElTooltip: { template: '<span><slot /></span>' },
          ElIcon: true, ElDrawer: true, ElDialog: true, ElMessage: true,
          ElPagination: { props: ['total', 'pageSize'], template: '<div data-test="pager" :data-total="total" />' },
        },
      },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
  }

  it('初始加载按「我的」可见性向服务端请求第一页摘要', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }, { id: '2', name: 'B' }], 7, 1))
    await mountList()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({
      visibility: 'private,workspace',
      page: 1,
      size: 20,
      sortBy: 'updateTime',
      sortOrder: 'desc',
    }))
    expect(wrapper!.text()).toContain('A')
    expect(wrapper!.text()).toContain('B')
  })

  it('关键词搜索走服务端查询，而不是只过滤当前页', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }, { id: '2', name: 'B' }], 7, 1))
    await mountList()
    mocks.listSummary.mockClear()

    await wrapper!.find('.search-input').setValue('策略')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({ keyword: '策略', page: 1 }))
  })

  it('状态筛选后回到第一页并由服务端过滤', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }], 7, 1))
    await mountList()
    const publishedTab = wrapper!.findAll('.filter-tab').find((item) => item.text().includes('published'))
    mocks.listSummary.mockClear()

    await publishedTab!.trigger('click')
    await flushPromises()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({ status: 'published', page: 1 }))
  })

  it('切换排序方向后按服务端排序重新取数', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }], 7, 1))
    await mountList()
    mocks.listSummary.mockClear()

    await wrapper!.find('.filter-sort').trigger('click')
    await flushPromises()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({ sortOrder: 'asc', sortBy: 'updateTime' }))
  })

  it('切换到模板 Tab 时用模板可见性重新取数', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }], 7, 1))
    await mountList()
    mocks.listSummary.mockClear()

    await wrapper!.findAll('.main-tab')[1].trigger('click')
    await flushPromises()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({ visibility: 'template,official', page: 1 }))
  })

  it('模板列表为当前用户可管理的非官方模板提供删除操作', async () => {
    mocks.listSummary.mockImplementation(async ({ visibility }: { visibility?: string } = {}) =>
      visibility === 'template,official'
        ? page([{ id: 'template-1', name: '我的模板', visibility: 'template', ownerId: 1, templateMeta: '{"isOfficial":false}' }], 1, 1)
        : page([], 0, 1),
    )
    await mountList()
    await wrapper!.findAll('.main-tab')[1].trigger('click')
    await flushPromises()

    const deleteButton = wrapper!.find('.action-delete')
    expect(deleteButton).toBeDefined()
    await deleteButton!.trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledOnce()
    expect(mocks.remove).toHaveBeenCalledWith('template-1')
  })

  it('官方模板或当前用户无管理权限时不显示删除操作', async () => {
    mocks.listSummary.mockImplementation(async ({ visibility }: { visibility?: string } = {}) =>
      visibility === 'template,official'
        ? page([
          { id: 'official-1', name: '官方模板', visibility: 'official', ownerId: 1, templateMeta: '{"isOfficial":true}' },
          { id: 'other-template', name: '他人模板', visibility: 'template', ownerId: 2, templateMeta: '{"isOfficial":false}' },
        ], 2, 1)
        : page([], 0, 1),
    )
    mocks.canModifyResource.mockReturnValue(false)
    await mountList()
    await wrapper!.findAll('.main-tab')[1].trigger('click')
    await flushPromises()

    expect(wrapper!.find('.action-delete').exists()).toBe(false)
  })

  it('翻页请求对应页码并展示该页记录', async () => {
    // 总数超过每页条数时分页条才出现
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'P1' }], 40, 1, 20))
    await mountList()
    mocks.listSummary.mockClear()
    mocks.listSummary.mockResolvedValue(page([{ id: '3', name: 'P2' }], 40, 2, 20))

    await wrapper!.findComponent('[data-test="pager"]').vm.$emit('current-change', 2)
    await flushPromises()

    expect(mocks.listSummary).toHaveBeenCalledWith(expect.objectContaining({ page: 2 }))
    expect(wrapper!.text()).toContain('P2')
  })

  it('新建成功后立即跳转编辑器，不等待列表刷新', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }], 1, 1))
    await mountList()
    let releaseCreate!: (value: unknown) => void
    let releaseList!: (value: unknown) => void
    mocks.create.mockReturnValue(new Promise((resolve) => { releaseCreate = resolve }))
    mocks.listSummary.mockReturnValue(new Promise((resolve) => { releaseList = resolve }))

    await wrapper!.findAll('.header-actions button').at(-1)!.trigger('click')
    releaseCreate({ id: 'new-9', name: '未命名仪表盘' })
    await flushPromises()

    expect(push).toHaveBeenCalledWith({ name: 'insight-dashboard-editor', query: { dashboardId: 'new-9' } })
    // 列表刷新仍在进行也不影响跳转结果
    releaseList(page([{ id: 'new-9', name: '未命名仪表盘' }], 1, 1))
    await flushPromises()
  })

  it('新建失败时不产生编辑器路由跳转', async () => {
    mocks.listSummary.mockResolvedValue(page([{ id: '1', name: 'A' }], 1, 1))
    await mountList()
    mocks.create.mockRejectedValue(new Error('创建失败'))

    await wrapper!.findAll('.header-actions button').at(-1)!.trigger('click')
    await flushPromises()

    expect(push).not.toHaveBeenCalled()
    expect(wrapper!.find('.list-header').exists()).toBe(true)
  })
})
