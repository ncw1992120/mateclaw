// 调试：真实编辑器页面结构
import { chromium } from 'playwright'
import crypto from 'node:crypto'

const BASE = 'http://localhost:5180'
const browser = await chromium.launch({
  headless: true,
  executablePath: '/Users/srant/Library/Caches/ms-playwright/chromium-1140/chrome-mac/Chromium.app/Contents/MacOS/Chromium',
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message))

await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
const pkRes = await page.evaluate(async () => (await (await fetch('/dataagent/api/v1/auth/pubkey')).json()))
const pem = pkRes?.data?.publicKey ?? pkRes?.data ?? pkRes?.publicKey
const innerB64 = Buffer.from(`${Date.now()}:admin123`, 'utf8').toString('base64')
const encryptedB64 = crypto.publicEncrypt({ key: pem, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' }, Buffer.from(innerB64)).toString('base64')
const loginRes = await page.evaluate(async ({ body }) => {
  const r = await fetch('/dataagent/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  return r.json()
}, { body: { username: 'admin', password: encryptedB64, channel: 'local' } })
const token = loginRes?.data?.token
await page.evaluate((t) => { localStorage.setItem('token', t); localStorage.setItem('workspaceId', JSON.stringify(1)) }, token)

const dashId = '2100840754749030402'
await page.goto(`${BASE}/insight/dashboard/editor?dashboardId=${dashId}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)

const info = await page.evaluate(() => {
  const draggables = Array.from(document.querySelectorAll('[draggable="true"]')).map((d) => (d.textContent || '').trim().slice(0, 12))
  return {
    url: location.href,
    hasCanvas: !!document.querySelector('.dashboard-canvas'),
    canvasRect: document.querySelector('.dashboard-canvas') ? JSON.stringify(document.querySelector('.dashboard-canvas').getBoundingClientRect()) : null,
    draggableCount: draggables.length,
    draggables: draggables.slice(0, 20),
    hasCombo: !!document.querySelector('.combination-card'),
    bodyChildren: Array.from(document.querySelector('#app')?.children ?? []).length,
    errText: document.querySelector('.el-message')?.textContent ?? null,
  }
})
console.log(JSON.stringify(info, null, 1))
await page.screenshot({ path: '/tmp/real_editor_debug.png' })
await browser.close()
