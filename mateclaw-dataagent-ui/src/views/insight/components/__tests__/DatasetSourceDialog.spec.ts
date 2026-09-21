import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DatasetSourceDialog from '../DatasetSourceDialog.vue'

vi.mock('@/api/dataset', () => ({
  previewDraft: vi.fn().mockResolvedValue({ rows: [{ id: 1 }] }),
  confirmDraft: vi.fn().mockResolvedValue({ datasetId: 'draft-1' }),
}))
vi.mock('@/api/datasource', () => ({ listAnalysisViews: vi.fn().mockResolvedValue([]) }))

const stubs = {
  'el-dialog': {
    props: ['modelValue', 'title'],
    template: '<div class="el-dialog"><slot /><slot name="footer" /></div>',
  },
  'el-button': {
    props: ['type', 'plain', 'loading'],
    template: '<button :class="type === \'primary\' && !plain ? \'primary\' : \'secondary\'" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-input': { props: ['modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-input-number': { template: '<input />' },
  'el-empty': { template: '<div><slot /></div>' },
}

describe('DatasetSourceDialog', () => {
  it('keeps preview in the same dialog and exposes one primary confirm action', async () => {
    const wrapper = mount(DatasetSourceDialog, {
      props: {
        modelValue: true,
        selection: { sourceType: 'JDBC_SQL', datasourceId: 'mysql-1' },
      },
      global: { stubs },
    })

    expect(wrapper.findAll('.el-dialog')).toHaveLength(1)
    expect(wrapper.findAll('button').filter(button => button.text() === '确认添加')).toHaveLength(1)
    await wrapper.get('button[aria-label="筛选预览"]').trigger('click')
    expect(wrapper.find('[role="tabpanel"][aria-label="预览结果"]').exists()).toBe(true)
  })
})
