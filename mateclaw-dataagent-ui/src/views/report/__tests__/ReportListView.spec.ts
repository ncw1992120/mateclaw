import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReportListView from '../ReportListView.vue'

const mocks = vi.hoisted(() => ({
  listMyReports: vi.fn(),
  getReportDetail: vi.fn(),
}))
vi.mock('@/api/insight-report', () => ({
  listMyReports: mocks.listMyReports,
  listReports: vi.fn(),
  listSubscribedReports: vi.fn(),
  getReportDetail: mocks.getReportDetail,
  deleteReport: vi.fn(),
  subscribeReport: vi.fn(),
  unsubscribeReport: vi.fn(),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const stubs = {
  'el-input': { template: '<input v-bind="$attrs" />' },
  'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-drawer': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
  'el-icon': { template: '<span><slot /></span>' },
}

describe('ReportListView keyboard semantics', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.listMyReports.mockResolvedValue([{
      id: 'report-1',
      name: '销售报告',
      description: '月度销售',
      status: 'published',
      ownerId: '1',
      ownerName: '管理员',
      updateTime: '2026-09-15T00:00:00',
    }])
    mocks.getReportDetail.mockResolvedValue({ content: '<p>ok</p>' })
  })

  it('exposes report cards as keyboard-operable buttons', async () => {
    const wrapper = mount(ReportListView, {
      global: { plugins: [i18n], stubs },
    })
    await vi.waitFor(() => expect(wrapper.find('.report-card').exists()).toBe(true))
    const card = wrapper.find('.report-card')
    expect(card.attributes('role')).toBe('button')
    expect(card.attributes('tabindex')).toBe('0')
    expect(card.attributes('aria-label')).toBe('查看报告：销售报告')
  })

  it('exposes report sections as tabs with keyboard navigation', async () => {
    const wrapper = mount(ReportListView, { global: { plugins: [i18n], stubs } })
    await vi.waitFor(() => expect(wrapper.find('.report-card').exists()).toBe(true))
    const tabs = wrapper.findAll('.tab-bar .tab-btn')
    expect(tabs.length).toBe(3)
    expect(tabs[0].attributes('role')).toBe('tab')
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    await tabs[0].trigger('keydown', { key: 'ArrowRight' })
    expect(tabs[1].attributes('aria-selected')).toBe('true')
    expect(tabs[0].attributes('tabindex')).toBe('-1')
  })
})
