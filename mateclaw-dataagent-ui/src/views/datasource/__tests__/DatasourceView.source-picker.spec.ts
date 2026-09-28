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

  it('shows JDBC engines on the second step and opens the selected engine form', async () => {
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
    await wrapper.get('[aria-label="选择JDBC数据源"]').trigger('click')
    expect(wrapper.findAll('.source-option strong').map((item) => item.text())).toEqual([
      'MySQL', 'PostgreSQL', 'SQL Server', 'Doris', 'ClickHouse', 'StarRocks',
    ])

    await wrapper.get('[aria-label="选择StarRocks数据源"]').trigger('click')
    expect(wrapper.get('[data-testid="datasource-form"]').attributes('data-source-id')).toBe('63')
  })
})
