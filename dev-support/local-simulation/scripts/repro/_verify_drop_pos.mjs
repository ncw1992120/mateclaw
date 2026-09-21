// 验证：洞察仪表盘拖入组件按鼠标位置放置（不再固定落到最下方）
// 跑法：BASE=http://127.0.0.1:5181 PLAYWRIGHT_EXECUTABLE_PATH=... node _verify_drop_pos.mjs
import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://127.0.0.1:5181'

const envelope = (data) => ({ code: 200, msg: 'ok', data })

const browser = await chromium.launch({
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : {}),
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
const errors = []
page.on('pageerror', (e) => errors.push(String(e)))

await page.route('**/dataagent/api/**', async (route) => {
  const url = route.request().url()
  const method = route.request().method()
  if (url.includes('/insight/dashboards') && method === 'GET') {
    return route.fulfill({
      json: envelope({
        id: '1', name: '落点验证', ownerName: 'tester', ownerId: 'null',
        schemaJson: JSON.stringify({ pages: [{ id: 'page-1', name: 'P1', components: [] }] }),
      }),
    })
  }
  return route.fulfill({ json: envelope([]) })
})

await page.goto(`${BASE}/insight/dashboard/editor-preview`, { waitUntil: 'networkidle' })
await page.waitForSelector('.dashboard-canvas', { timeout: 15000 })
await page.waitForSelector('.palette-item', { timeout: 15000 })

async function dragTo(label, ratioX, offsetY) {
  await page.evaluate(({ label, ratioX, offsetY }) => {
    const src = [...document.querySelectorAll('[draggable="true"]')]
      .find((el) => el.textContent.includes(label))
    if (!src) throw new Error('palette item not found: ' + label)
    const dt = new DataTransfer()
    const dispatch = (type, x, y, target) => {
      const e = new DragEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y })
      Object.defineProperty(e, 'dataTransfer', { value: dt })
      target.dispatchEvent(e)
    }
    const sr = src.getBoundingClientRect()
    dispatch('dragstart', sr.x + 5, sr.y + 5, src)
    const canvas = document.querySelector('.dashboard-canvas')
    const cr = canvas.getBoundingClientRect()
    const dx = cr.x + cr.width * ratioX
    const dy = cr.y + offsetY
    dispatch('dragenter', dx, dy, canvas)
    dispatch('dragover', dx, dy, canvas)
    dispatch('drop', dx, dy, canvas)
  }, { label, ratioX, offsetY })
  await page.waitForTimeout(600)
}

// 第一块：折线图 → 画布中上部（画布为空，网格尚未创建，走画布根元素换算）
await dragTo('折线图', 0.5, 180)
await page.waitForSelector('.vgl-item', { timeout: 10000 })

// 第二块：数据表格 → 指定落点（90% 宽、300px 高，避开第一块的列区间），网格已存在，走 .vgl-layout 精确换算
const grid = await page.$('.vgl-layout')
const gr = await grid.boundingBox()
const dropX = gr.x + gr.width * 0.9
const dropY = gr.y + 300
await dragTo('数据表格', 0.9, 300)
await page.waitForFunction(() => document.querySelectorAll('.vgl-item').length >= 2, { timeout: 10000 })

// 读取两块卡片的实际盒子，换算成栅格坐标（vgl: left = margin + col*(colW+margin)）
const boxes = await page.evaluate(() => {
  const gr = document.querySelector('.vgl-layout').getBoundingClientRect()
  const colW = (gr.width - 12 * 25) / 24
  const toGrid = (el) => {
    const r = el.getBoundingClientRect()
    return {
      col: Math.round((r.x - gr.x - 12) / (colW + 12)),
      row: Math.round((r.y - gr.y - 12) / 42),
      colW,
    }
  }
  return [...document.querySelectorAll('.vgl-item')].map((el) => ({ ...toGrid(el), title: el.textContent.slice(0, 30) }))
})

const colW = boxes[0].colW
const colWidth = (gr.width - 12 * 25) / 24
const expectCol = Math.max(0, Math.min(Math.floor((dropX - gr.x - 12) / (colWidth + 12)), 24 - 6))
const expectRow = Math.floor((300 - 12) / 42)
const second = boxes[1]
const colOk = second.col === expectCol
const rowOk = second.row === expectRow
console.log('items:', JSON.stringify(boxes.map(({ colW: _c, ...b }) => b)))
console.log(`expect second at col=${expectCol} row=${expectRow}; actual col=${second.col} row=${second.row} (colW=${colW.toFixed(1)})`)

if (!colOk || !rowOk || errors.length) {
  console.error('FAIL', { colOk, rowOk, errors })
  process.exit(1)
}
console.log('PASS: 组件按鼠标位置放置（精确命中栅格）')
await browser.close()
