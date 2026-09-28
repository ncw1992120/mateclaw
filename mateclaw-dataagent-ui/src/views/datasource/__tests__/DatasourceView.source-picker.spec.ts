import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const datasourceStore = {
  fetchDatasources: vi.fn(),
}

vi.mock('pinia', () => ({
  storeToRefs: () => ({ datasources: ref([]), loading: ref(false) }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/stores/useDatasourceStore', () => ({
  useDatasourceStore: () => datasourceStore,
}))

vi.mock('@/stores/useUserStore', () => ({ useUserStore: () => ({}) }))
vi.mock('@/api/datasource', () => ({ listDatasourceAccounts: vi.fn().mockResolvedValue([]) }))
vi.mock('@/composables/useDebouncedFn', () => ({ useDebouncedFn: (fn: unknown) => fn }))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => true }),
  PERMISSION: { DATASOURCE_CREATE: 'datasource:create' },
}))
vi.mock('@/utils/sensitiveCrypto', () => ({ encryptSensitiveField: vi.fn() }))

import DatasourceView from '@/views/DatasourceView.vue'

describe('DatasourceView source picker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows only Aloudata and JDBC on the first step', async () => {
    const wrapper = mount(DatasourceView, {
      global: {
        stubs: {
          DatasourceForm: {
            props: ['sourceId'],
            template: '<div data-testid="datasource-form" :data-source-id="sourceId" />',
          },
          MetricPlatformPanel: true,
          AloudataBrandMark: true,
          'el-dialog': true,
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-button': true,
        },
      },
    })

    await wrapper.find('.btn-create-top').trigger('click')

    expect(wrapper.findAll('.source-option strong').map((item) => item.text())).toEqual(['Aloudata', 'JDBC'])
  })

  it('keeps JDBC unavailable and explains that it is not released yet', async () => {
    const wrapper = mount(DatasourceView, {
      global: {
        stubs: {
          DatasourceForm: {
            props: ['sourceId'],
            template: '<div data-testid="datasource-form" :data-source-id="sourceId" />',
          },
          MetricPlatformPanel: true,
          AloudataBrandMark: true,
          'el-dialog': true,
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-button': true,
        },
      },
    })

    await wrapper.find('.btn-create-top').trigger('click')
    const jdbcOption = wrapper.get('[aria-label="选择JDBC数据源"]')
    expect(jdbcOption.attributes('disabled')).toBeDefined()
    expect(jdbcOption.text()).toContain('开发中，还未上线，敬请期待')

    await jdbcOption.trigger('click')
    expect(wrapper.findAll('.source-option strong').map((item) => item.text())).toEqual(['Aloudata', 'JDBC'])
    expect(wrapper.find('[data-testid="datasource-form"]').exists()).toBe(false)
  })
})
