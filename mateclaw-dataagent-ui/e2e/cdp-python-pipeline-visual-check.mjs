import { chromium } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

// 只接管用户已经启动的 Google Chrome；本脚本禁止 launch Chromium 或创建独立浏览器。
const endpoint = process.env.MATECLAW_CDP_ENDPOINT ?? 'http://127.0.0.1:9222'
const baseUrl = process.env.MATECLAW_UI_BASE_URL ?? 'http://127.0.0.1:15174'
const stateFile = process.env.MATECLAW_E2E_STATE_FILE
const evidenceDir = process.env.MATECLAW_CDP_EVIDENCE_DIR ?? path.resolve('docs/superpowers/evidence/2026-09-21-python-pipeline')
if (!stateFile || !fs.existsSync(stateFile)) throw new Error('缺少 MATECLAW_E2E_STATE_FILE；视觉验收不得使用硬编码 dashboard ID')
const seed = JSON.parse(fs.readFileSync(stateFile, 'utf8'))
const browser = await chromium.connectOverCDP(endpoint)
const context = browser.contexts()[0]
if (!context) throw new Error('9222 上没有可用 Google Chrome context')
const page = await context.newPage()
fs.mkdirSync(evidenceDir, { recursive: true })

const consoleErrors = []
const failedRequests = []
page.on('pageerror', (error) => consoleErrors.push(`pageerror: ${error.message}`))
page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })
page.on('requestfailed', (request) => failedRequests.push(`FAILED ${request.url()}`))
page.on('response', (response) => { if (response.status() >= 400) failedRequests.push(`${response.status()} ${response.url()}`) })

const report = { browser: 'Google Chrome via CDP 9222', dashboardIds: seed, screenshots: [], consoleErrors, failedRequests }
async function shot(name, action) {
  const item = { name, status: 'PASS', screenshotPath: path.join(evidenceDir, `${name}.png`) }
  try {
    await action()
    await page.screenshot({ path: item.screenshotPath, fullPage: true })
  } catch (error) {
    item.status = 'FAIL'
    item.detail = String(error.message || error)
    await page.screenshot({ path: item.screenshotPath, fullPage: true }).catch(() => {})
  }
  report.screenshots.push(item)
}

async function openSeededCard(name) {
  await page.goto(`${baseUrl}/?nav=insight`, { waitUntil: 'domcontentloaded' })
  const card = page.locator('.dashboard-card').filter({ hasText: name }).first()
  await card.waitFor({ state: 'visible', timeout: 30_000 })
  await card.getByRole('button', { name: /编辑/ }).click()
  await page.locator('.insight-editor-view').waitFor({ state: 'visible', timeout: 30_000 })
  const canvasCard = page.locator('[data-component-id]').first()
  await canvasCard.waitFor({ state: 'visible', timeout: 30_000 })
  await canvasCard.click()
}

await shot('01-dataset-filter-defaults', async () => {
  await openSeededCard('Python Filter Dashboard')
  await page.getByRole('button', { name: /查看数据/ }).first().click()
  await page.locator('[data-testid="bound-filter-row"]').first().waitFor({ state: 'visible' })
})
await shot('02-filter-single-boundary-result', async () => {
  const rows = page.locator('[data-testid="bound-filter-row"] input')
  await rows.first().fill('2026-09-01')
  await page.getByRole('button', { name: '查询' }).click()
})
await shot('03-python-generated-mode', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('Python Managed System Dashboard')
  await page.getByRole('button', { name: /编辑 Python 脚本|展开编辑/ }).first().click()
  await page.getByTestId('system-mode-tag').waitFor({ state: 'visible' })
})
await shot('04-python-managed-mode', async () => {
  if (await page.getByTestId('unlock-btn').count()) {
    await page.getByTestId('unlock-btn').click()
    await page.getByRole('button', { name: /解锁/ }).last().click().catch(() => {})
  }
  await page.getByTestId('system-code-managed').waitFor({ state: 'visible' })
})
await shot('05-python-system-diff', async () => {
  if (await page.getByTestId('diff-toggle').count()) await page.getByTestId('diff-toggle').click()
})
await shot('06-a-to-b-result-preview', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('Python A-to-B Dashboard')
  await page.getByRole('button', { name: '最终结果预览' }).click()
  await page.locator('.result-table').waitFor({ state: 'visible', timeout: 120_000 })
})
await shot('07-output-schema-preview', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('Python Output Contract Dashboard')
  await page.getByRole('button', { name: '最终结果预览' }).click()
  await page.getByText(/字段结构|输出结构/).first().waitFor({ state: 'visible', timeout: 120_000 })
})
await shot('08-table-component-preview', async () => {
  await page.getByText(/数据预览/).first().click().catch(() => {})
  await page.locator('.result-table').waitFor({ state: 'visible' })
})
await shot('09-kpi-component-preview', async () => {
  await page.getByText(/组件预览|最终结果预览/).first().click().catch(() => {})
})
await shot('10-chart-component-preview', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('E2E ECharts Binding Dashboard')
  await page.locator('.chart-container canvas').waitFor({ state: 'visible', timeout: 120_000 })
})
await shot('11-output-contract-error', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('Python Output Error Dashboard')
  await page.getByRole('button', { name: '最终结果预览' }).click()
  await page.locator('.execution-alert').waitFor({ state: 'visible', timeout: 120_000 })
})
await shot('12-saved-dashboard-preview', async () => {
  await page.keyboard.press('Escape')
  await openSeededCard('Python Large Result Dashboard')
  await page.getByRole('button', { name: '保存' }).click().catch(() => {})
})

report.axNodeCount = await page.locator('button, input, textarea, [role="table"], [role="alert"]').count().catch(() => 0)
report.failedRequests = [...new Set(report.failedRequests)]
fs.writeFileSync(path.join(evidenceDir, 'visual-report.json'), JSON.stringify(report, null, 2))
if (report.screenshots.some((item) => item.status === 'FAIL') || report.consoleErrors.length || report.failedRequests.length) {
  process.exitCode = 1
}
await page.close()
