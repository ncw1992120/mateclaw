// 复现：组合卡片内子组件拖动/缩放是否失效（无页签态 + 页签内两种状态）
import { chromium } from 'playwright'

const BASE = 'http://localhost:5180'
const browser = await chromium.launch({
  headless: true,
  executablePath: '/Users/srant/Library/Caches/ms-playwright/chromium-1140/chrome-mac/Chromium.app/Contents/MacOS/Chromium',
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

// 准备：组合卡片 + 拉大容器 + 放入「时间筛选」子组件
await dragByDataTransfer('组合卡片', '.dashboard-canvas')
await page.waitForTimeout(600)
let gi = page.locator('.vgl-item').first()
let gb = await gi.boundingBox()
await page.mouse.move(gb.x + gb.width - 4, gb.y + gb.height - 4)
await page.mouse.down()
await page.mouse.move(gb.x + gb.width + 260, gb.y + gb.height + 300, { steps: 12 })
await page.mouse.up()
await page.waitForTimeout(400)
await dragByDataTransfer('时间筛选', '.combination-card')
await page.waitForTimeout(600)

// ── 状态1：无页签态，真实鼠标拖动子组件 ──
const before1 = await childLayout()
const child = page.locator('.combination-card .cc-child').first()
let cb = await child.boundingBox()
await page.mouse.move(cb.x + cb.width / 2, cb.y + 12)
await page.mouse.down()
await page.mouse.move(cb.x + cb.width / 2 + 80, cb.y + 12 + 60, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after1 = await childLayout()
console.log(`无页签态拖动: before=${JSON.stringify(before1)} after=${JSON.stringify(after1)} => ${before1.x !== after1.x || before1.y !== after1.y ? 'MOVED ✓' : '未移动 ✗'}`)

// 真实鼠标缩放（SE 手柄）
const before2 = await childLayout()
const se = page.locator('.combination-card .cc-child .rs.se').first()
let sb = await se.boundingBox()
await page.mouse.move(sb.x + sb.width / 2, sb.y + sb.height / 2)
await page.mouse.down()
await page.mouse.move(sb.x + sb.width / 2 + 50, sb.y + sb.height / 2 + 40, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after2 = await childLayout()
console.log(`无页签态缩放: before=${JSON.stringify(before2)} after=${JSON.stringify(after2)} => ${before2.col !== after2.col || before2.h !== after2.h ? 'RESIZED ✓' : '未缩放 ✗'}`)

// ── 状态2：加页签（子组件平移进页签1）后再拖动/缩放 ──
await page.locator('.combination-card .cc-tab-add').click().catch(async () => {
  await page.locator('.combination-tab-add').click()
})
await page.waitForTimeout(600)
const inTab = await childLayout()
console.log(`页签内子组件布局: ${JSON.stringify(inTab)}`)

const before3 = await childLayout()
let cb3 = await page.locator('.combination-card .cc-child').first().boundingBox()
await page.mouse.move(cb3.x + cb3.width / 2, cb3.y + 12)
await page.mouse.down()
await page.mouse.move(cb3.x + cb3.width / 2 + 60, cb3.y + 12 + 40, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after3 = await childLayout()
console.log(`页签内拖动: before=${JSON.stringify(before3)} after=${JSON.stringify(after3)} => ${before3.x !== after3.x || before3.y !== after3.y ? 'MOVED ✓' : '未移动 ✗'}`)

const before4 = await childLayout()
const se4 = page.locator('.combination-card .cc-child .rs.se').first()
let sb4 = await se4.boundingBox()
console.log('SE 手柄包围盒(页签内):', JSON.stringify(sb4))
await page.mouse.move(sb4.x + sb4.width / 2, sb4.y + sb4.height / 2)
await page.mouse.down()
await page.mouse.move(sb4.x + sb4.width / 2 + 40, sb4.y + sb4.height / 2 + 30, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const after4 = await childLayout()
console.log(`页签内缩放: before=${JSON.stringify(before4)} after=${JSON.stringify(after4)} => ${before4.col !== after4.col || before4.h !== after4.h ? 'RESIZED ✓' : '未缩放 ✗'}`)

await page.screenshot({ path: '/tmp/combo_drag_repro.png' })
await browser.close()
