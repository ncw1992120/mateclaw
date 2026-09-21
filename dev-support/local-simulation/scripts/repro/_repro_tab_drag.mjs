// 复现②：页签内子组件拖动/缩放
import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://localhost:5180'
const browser = await chromium.launch({
  headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : {}),
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message))
page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE_ERR:', m.text().slice(0, 300)) })

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

async function childLayout() {
  return page.evaluate(() => {
    const comp = document.querySelector('.combination-card').__vueParentComponent.props.component
    const arr = (comp.containerConfig?.tabs?.length ? comp.containerConfig.tabs[0].children : comp.children) || []
    const c = arr[0]
    return c ? { x: c.layout.x, y: c.layout.y, col: c.layout.col, h: c.layout.h } : null
  })
}

// 准备：组合卡片 → 选中容器 → 面板拉大容器?（此处用真实鼠标拉大 GridItem）→ 放子组件 → 加页签
await dragByDataTransfer('组合卡片', '.dashboard-canvas')
await page.waitForTimeout(600)
// 选中容器，让面板出现
await page.locator('.grid-item-toolbar .grid-item-title').click()
await page.waitForTimeout(400)
let gi = page.locator('.vgl-item').first()
let gb = await gi.boundingBox()
await page.mouse.move(gb.x + gb.width - 4, gb.y + gb.height - 4)
await page.mouse.down()
await page.mouse.move(gb.x + gb.width + 300, gb.y + gb.height + 320, { steps: 12 })
await page.mouse.up()
await page.waitForTimeout(400)
await dragByDataTransfer('时间筛选', '.combination-card')
await page.waitForTimeout(600)
// 重新选中容器（刚才放子组件时选中了子组件）
await page.locator('.grid-item-toolbar .grid-item-title').click()
await page.waitForTimeout(400)
// 面板加页签 → 子组件平移进页签1
await page.locator('.combination-tab-add').click()
await page.waitForTimeout(600)

const inTab = await childLayout()
console.log('页签内初始布局:', JSON.stringify(inTab))

// 页签内真实鼠标拖动
const before3 = await childLayout()
let cb3 = await page.locator('.combination-card .cc-child').first().boundingBox()
console.log('页签内子卡片包围盒:', JSON.stringify(cb3))
await page.mouse.move(cb3.x + cb3.width / 2, cb3.y + 12)
await page.mouse.down()
await page.mouse.move(cb3.x + cb3.width / 2 + 60, cb3.y + 12 + 50, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after3 = await childLayout()
console.log(`页签内拖动: ${JSON.stringify(before3)} -> ${JSON.stringify(after3)} => ${before3.x !== after3.x || before3.y !== after3.y ? 'MOVED ✓' : '未移动 ✗'}`)

// 页签内缩放
const before4 = await childLayout()
const se4 = page.locator('.combination-card .cc-child .rs.se').first()
const sb4 = await se4.boundingBox()
console.log('SE 手柄包围盒:', JSON.stringify(sb4))
await page.mouse.move(sb4.x + sb4.width / 2, sb4.y + sb4.height / 2)
await page.mouse.down()
await page.mouse.move(sb4.x + sb4.width / 2 + 40, sb4.y + sb4.height / 2 + 30, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after4 = await childLayout()
console.log(`页签内缩放: ${JSON.stringify(before4)} -> ${JSON.stringify(after4)} => ${before4.col !== after4.col || before4.h !== after4.h ? 'RESIZED ✓' : '未缩放 ✗'}`)

await page.screenshot({ path: '/tmp/combo_tab_drag_repro.png' })
await browser.close()
