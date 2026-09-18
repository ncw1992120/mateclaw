// 复现③：grid / vertical 布局模式下子组件是否可拖动/缩放（假设：用户容器切到了非 free 模式）
import { chromium } from 'playwright'

const BASE = 'http://localhost:5180'
const browser = await chromium.launch({
  headless: true,
  executablePath: '/Users/srant/Library/Caches/ms-playwright/chromium-1140/chrome-mac/Chromium.app/Contents/MacOS/Chromium',
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message))

const fakeDashboard = {
  id: 1, name: '验证用仪表盘', description: '', ownerName: 'tester', ownerId: 'null',
  schemaJson: JSON.stringify({ version: '1.1', pages: [{ id: 'page_1', name: '页面 1', components: [] }] }),
}
await page.route('**/dataagent/api/v1/insight/dashboards/*', (r) =>
  r.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok', data: fakeDashboard }) }))
await page.route('**/dataagent/api/v1/datasources**', (r) =>
  r.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok', data: [] }) }))

await page.goto(`${BASE}/insight/dashboard/editor-preview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)

async function dragByDataTransfer(srcText, targetSelector) {
  return page.evaluate(([text, dst]) => {
    const all = Array.from(document.querySelectorAll('[draggable="true"]'))
    const s = all.find((el) => (el.textContent || '').includes(text))
    const t = document.querySelector(dst)
    if (!s || !t) return 'missing'
    const dt = new DataTransfer()
    const rect = t.getBoundingClientRect()
    const o = { bubbles: true, cancelable: true, dataTransfer: dt, clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2 }
    s.dispatchEvent(new DragEvent('dragstart', o)); t.dispatchEvent(new DragEvent('dragenter', o))
    t.dispatchEvent(new DragEvent('dragover', o)); t.dispatchEvent(new DragEvent('drop', o))
    s.dispatchEvent(new DragEvent('dragend', o))
    return 'ok'
  }, [srcText, targetSelector])
}

async function getState() {
  return page.evaluate(() => {
    const comp = document.querySelector('.combination-card').__vueParentComponent.props.component
    const c = (comp.children || [])[0]
    const body = document.querySelector('.combination-card .cc-child')
    const se = document.querySelector('.combination-card .rs.se')
    return {
      mode: comp.containerConfig?.layoutMode,
      layout: c ? { x: c.layout.x, y: c.layout.y, col: c.layout.col, h: c.layout.h } : null,
      childPosition: body ? getComputedStyle(body).position : null,
      handleCount: document.querySelectorAll('.combination-card .rs').length,
      hasHandle: !!se,
    }
  })
}

await dragByDataTransfer('组合卡片', '.dashboard-canvas')
await page.waitForTimeout(600)
await page.locator('.grid-item-toolbar .grid-item-title').click()
await page.waitForTimeout(400)
await dragByDataTransfer('时间筛选', '.combination-card')
await page.waitForTimeout(600)
// 重新选中容器
await page.locator('.grid-item-toolbar .grid-item-title').click()
await page.waitForTimeout(400)

console.log('初始状态:', JSON.stringify(await getState()))

// 切换布局模式：找面板里的模式选择（radio/select/segmented）
const modeControls = await page.evaluate(() => {
  const labels = Array.from(document.querySelectorAll('.editor-property label, .editor-property .el-radio, .editor-property .el-segmented__item, .editor-property button'))
  return labels.map((l) => l.textContent?.trim()).filter(Boolean).slice(0, 20)
})
console.log('面板控件文本:', JSON.stringify(modeControls))

// 逐个尝试点击「网格」「纵向」模式（按文本匹配）
for (const modeName of ['网格', '自由', '纵向']) {
  const btn = page.locator('.editor-property').getByText(modeName, { exact: false }).first()
  if (await btn.count() > 0) {
    const visible = await btn.isVisible().catch(() => false)
    console.log(`模式按钮「${modeName}」count>0 visible=${visible}`)
  }
}
await browser.close()
