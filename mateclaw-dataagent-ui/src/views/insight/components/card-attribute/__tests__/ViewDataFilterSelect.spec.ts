import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const listDimensionValues = vi.hoisted(() => vi.fn())
vi.mock('@/api/datasource', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/api/datasource')>(),
  listDimensionValues: (...args: unknown[]) => listDimensionValues(...args),
}))

import ViewDataFilterSelect from '../ViewDataFilterSelect.vue'

const stubs = {
  'el-select': defineComponent({
    name: 'ElSelectStub',
    props: ['modelValue', 'multiple', 'remote', 'loading', 'disabled'],
    emits: ['update:modelValue', 'visible-change'],
    template: '<div class="select-stub" :data-multiple="String(multiple)" :data-remote="String(remote)"><slot /></div>',
  }),
  'el-option': defineComponent({
    props: ['label', 'value'],
    template: '<span class="option" :data-value="value">{{ label }}</span>',
  }),
}

beforeEach(() => listDimensionValues.mockReset())

describe('ViewDataFilterSelect', () => {
  it('uses configured static options and exposes multiple selection mode', () => {
    const wrapper = mount(ViewDataFilterSelect, {
      props: {
        modelValue: ['east'],
        filter: {
          optionSource: 'static',
          selectionMode: 'multiple',
          staticOptions: [{ label: '华东', value: 'east' }, { label: '华南', value: 'south' }],
        },
      },
      global: { stubs },
    })

    expect(wrapper.find('.select-stub').attributes('data-multiple')).toBe('true')
    expect(wrapper.findAll('.option').map((option) => option.text())).toEqual(['华东', '华南'])
  })

  it('opens with remote dimension values while retaining a selected default missing from the response', async () => {
    listDimensionValues.mockResolvedValue(['华南'])
    const wrapper = mount(ViewDataFilterSelect, {
      props: {
        modelValue: 'east',
        filter: { optionSource: 'dynamic', datasourceId: 'ds-7', field: 'region', selectionMode: 'single' },
      },
      global: { stubs },
    })
    wrapper.getComponent({ name: 'ElSelectStub' }).vm.$emit('visible-change', true)
    await flushPromises()
    await nextTick()

    expect(listDimensionValues).toHaveBeenCalledWith('ds-7', 'region', undefined, 200)
    expect(wrapper.findAll('.option').map((option) => option.text())).toEqual(['华南', 'east'])
  })
})
