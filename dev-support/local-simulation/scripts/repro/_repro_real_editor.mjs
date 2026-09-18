// 复现④：真实编辑器（/insight/dashboard/editor?dashboardId=xxx，真实登录 + 真实后端 18089）
import { chromium } from 'playwright'
import crypto from 'node:crypto'

const BASE = 'http://localhost:5180'
const browser = await chromium.launch({
  headless: true,
  executablePath: '/Users/srant/Library/Caches/ms-playwright/chromium-1140/chrome-mac/Chromium.app/Contents/MacOS/Chromium',
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message))
page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE_ERR:', m.text().slice(0, 200)) })

// ── 登录（页面上下文拿 pubkey，Node 侧 RSA-OAEP-SHA256 加密）──
await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
const pkRes = await page.evaluate(async () => (await (await fetch('/dataagent/api/v1/auth/pubkey')).json()))
const pem = pkRes?.data?.publicKey ?? pkRes?.data ?? pkRes?.publicKey
console.log('pubkey 获取:', pem ? 'OK' : JSON.stringify(pkRes).slice(0, 150))
const innerB64 = Buffer.from(`${Date.now()}:admin123`, 'utf8').toString('base64')
const encryptedB64 = crypto.publicEncrypt({ key: pem, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' }, Buffer.from(innerB64, 'utf8')).toString('base64')
const loginRes = await page.evaluate(async ({ body }) => {
  const r = await fetch('/dataagent/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  return r.json()
}, { body: { username: 'admin', password: encryptedB64, channel: 'local' } })
const token = loginRes?.data?.token
console.log('登录:', token ? 'OK' : JSON.stringify(loginRes).slice(0, 200))
if (!token) { await browser.close(); process.exit(1) }
await page.evaluate((t) => { localStorage.setItem('token', t); localStorage.setItem('workspaceId', JSON.stringify(1)) }, token)

// ── 建仪表盘 ──
const dashRes = await page.evaluate(async () => {
  const r = await fetch('/dataagent/api/v1/insight/dashboards', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}`, 'X-Workspace-Id': '1' },
    body: JSON.stringify({ name: `拖拽复现-${Date.now()}`, description: '' }),
  })
  return r.json()
})
const dashId = dashRes?.data?.id
console.log('建仪表盘:', dashId ? `OK id=${dashId}` : JSON.stringify(dashRes).slice(0, 200))

await page.goto(`${BASE}/insight/dashboard/editor?dashboardId=${dashId}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)

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
    const card = document.querySelector('.combination-card')
    if (!card) return null
    const comp = card.__vueParentComponent.props.component
    const arr = (comp.containerConfig?.tabs?.length ? comp.containerConfig.tabs[0].children : comp.children) || []
    const c = arr[0]
    return c ? { x: c.layout.x, y: c.layout.y, col: c.layout.col, h: c.layout.h } : null
  })
}

await dragByDataTransfer('组合卡片', '.dashboard-canvas')
await page.waitForTimeout(700)
await dragByDataTransfer('时间筛选', '.combination-card')
await page.waitForTimeout(700)

const l0 = await childLayout()
console.log('初始子卡片布局:', JSON.stringify(l0))

// 真实鼠标拖动
let cb = await page.locator('.combination-card .cc-child').first().boundingBox()
console.log('子卡片包围盒:', JSON.stringify(cb))
await page.mouse.move(cb.x + cb.width / 2, cb.y + 12)
await page.mouse.down()
await page.mouse.move(cb.x + cb.width / 2 + 60, cb.y + 12 + 50, { steps: 10 })
await page.mouse.up()
await page.waitForTimeout(500)
const l1 = await childLayout()
console.log(`真实编辑器拖动: ${JSON.stringify(l0)} -> ${JSON.stringify(l1)}`)

// 真实鼠标缩放
const se = page.locator('.combination-card .cc-child .rs.se').first()
const sb = await se.boundingBox().catch(() => null)
console.log('SE 手柄:', JSON.stringify(sb))
if (sb) {
  await page.mouse.move(sb.x + sb.width / 2, sb.y + sb.height / 2)
  await page.mouse.down()
  await page.mouse.move(sb.x + sb.width / 2 + 40, sb.y + sb.height / 2 + 30, { steps: 10 })
  await page.mouse.up()
  await page.waitForTimeout(500)
  const l2 = await childLayout()
  console.log(`真实编辑器缩放: ${JSON.stringify(l1)} -> ${JSON.stringify(l2)}`)
}
await page.screenshot({ path: '/tmp/combo_real_editor_repro.png' })
console.log('SCREENSHOT: /tmp/combo_real_editor_repro.png')
await browser.close()
