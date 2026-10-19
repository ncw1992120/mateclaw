import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import AiAnalysisWidget from '../AiAnalysisWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const globalStubs = {
  global: {
    plugins: [i18n],
    stubs: {
      'el-icon': true,
      'el-button': {
        template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
      },
    },
  },
}

const component = {
  id: 'ai-1',
  type: 'ai-analysis',
  title: 'AI 分析',
} as any

describe('AiAnalysisWidget', () => {
  it('emits generate with the component id when the action button is clicked', async () => {
    const wrapper = mount(AiAnalysisWidget, {
      props: { component },
      ...globalStubs,
    })

    await wrapper.find('button.el-button').trigger('click')

    expect(wrapper.emitted('generate')![0]).toEqual(['ai-1'])
  })

  it('shows the generating indicator instead of the action button while generating', () => {
    const wrapper = mount(AiAnalysisWidget, {
      props: { component, generating: true },
      ...globalStubs,
    })

    expect(wrapper.find('.generating-indicator').exists()).toBe(true)
    expect(wrapper.find('button.el-button').exists()).toBe(false)
  })

  it('hides manual generation when automatic generation is enabled', () => {
    const wrapper = mount(AiAnalysisWidget, {
      props: { component: { ...component, config: { autoGenerate: true } } },
      ...globalStubs,
    })

    expect(wrapper.find('button.el-button').exists()).toBe(false)
  })

  it('renders data and ai sections with their labels and unchanged markdown text', () => {
    const componentData = {
      aiAnalysis: {
        dataSection: '下发策略 **1,280** 条',
        analysisSection: '策略有效率 **92%**',
      },
    } as any

    const wrapper = mount(AiAnalysisWidget, {
      props: { component, componentData },
      ...globalStubs,
    })

    const sections = wrapper.findAll('.analysis-section')
    expect(sections.length).toBe(2)
    expect(sections[0].attributes('data-label')).toBe('insight.aiAnalysis.dataSectionLabel')
    expect(sections[1].attributes('data-label')).toBe('insight.aiAnalysis.aiSectionLabel')
    expect(sections[0].text()).toContain('1,280')
    expect(sections[1].text()).toContain('92%')
  })

  it('shows the empty placeholder when there is no content and not generating', () => {
    const wrapper = mount(AiAnalysisWidget, {
      props: { component },
      ...globalStubs,
    })

    expect(wrapper.find('.analysis-placeholder').exists()).toBe(true)
    expect(wrapper.findAll('.analysis-section').length).toBe(0)
  })

  it('等待数据时显示剩余组件名称，数据全部返回后切换到分析提示', async () => {
    const messages = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': { insight: { aiAnalysis: {
      generating: '正在分析当前数据…', waitingForComponents: '等待 {components} 组件数据返回…',
    } } } }, missingWarn: false, fallbackWarn: false })
    const wrapper = mount(AiAnalysisWidget, {
      props: { component, generating: true, waitingComponentTitles: ['策略概括', '策略执行'],
        componentData: { aiAnalysis: { analysisSection: '旧分析' } } as any },
      global: { ...globalStubs.global, plugins: [messages] },
    })
    expect(wrapper.get('[role="status"]').text()).toBe('等待 策略概括、策略执行 组件数据返回…')
    expect(wrapper.text()).not.toContain('旧分析')
    await wrapper.setProps({ waitingComponentTitles: ['策略执行'] })
    expect(wrapper.get('[role="status"]').text()).toBe('等待 策略执行 组件数据返回…')
    await wrapper.setProps({ waitingComponentTitles: [] })
    expect(wrapper.get('[role="status"]').text()).toBe('正在分析当前数据…')
  })
})
