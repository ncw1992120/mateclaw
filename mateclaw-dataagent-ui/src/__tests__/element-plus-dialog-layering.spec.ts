import { createApp, defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import ElementPlus, { ElDialog } from 'element-plus'
import { elementPlusConfig } from '../element-plus-config'

describe('application dialog stacking', () => {
  let app: ReturnType<typeof createApp> | undefined

  afterEach(() => {
    app?.unmount()
    app = undefined
    document.body.innerHTML = ''
  })

  it('renders dashboard dialogs above the editor canvas layers', async () => {
    const firstVisible = ref(true)
    const secondVisible = ref(true)
    const host = document.createElement('div')
    document.body.append(host)
    app = createApp(defineComponent({
      setup: () => () => [
        h(ElDialog, { modelValue: firstVisible.value, 'onUpdate:modelValue': (value: boolean) => { firstVisible.value = value } }, () => 'first dialog'),
        h(ElDialog, { modelValue: secondVisible.value, 'onUpdate:modelValue': (value: boolean) => { secondVisible.value = value } }, () => 'second dialog'),
      ],
    }))
    app.use(ElementPlus, elementPlusConfig)
    app.mount(host)
    await nextTick()
    await nextTick()

    const overlays = [...document.body.querySelectorAll<HTMLElement>('.el-overlay')]
    const zIndices = overlays.map((overlay) => Number(overlay.style.zIndex))
    expect(zIndices).toHaveLength(2)
    expect(zIndices.every((zIndex) => zIndex > 3000)).toBe(true)
    expect(zIndices[1]).toBeGreaterThan(zIndices[0])
  })
})
