import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DataConfigView from '../DataConfigView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

describe('DataConfigView', () => {
  it('does not show the dataset management shortcut', () => {
    const wrapper = mount(DataConfigView, {
      global: {
        stubs: {
          DatasourceView: { template: '<div data-testid="datasource-view" />' },
        },
      },
    })

    expect(wrapper.get('.data-subnav-title').text()).toBe('数据源')
    expect(wrapper.find('.dataset-link').exists()).toBe(false)
    expect(wrapper.find('[data-testid="datasource-view"]').exists()).toBe(true)
  })
})
