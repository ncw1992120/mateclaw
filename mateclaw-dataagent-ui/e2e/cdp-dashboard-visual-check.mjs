import { chromium } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

function required(name) {
  const value = process.env[name]
  if (!value) throw new Error(`缺少 CDP 视觉验收环境变量 ${name}`)
  return value
}

const token = required('MATECLAW_E2E_TOKEN')
const workspaceId = required('MATECLAW_E2E_WORKSPACE_ID')
const dashboardId = required('MATECLAW_E2E_DASHBOARD_ID')
const echartsDashboardId = required('MATECLAW_E2E_ECHARTS_DASHBOARD_ID')
const baseUrl = process.env.MATECLAW_UI_BASE_URL ?? 'http://127.0.0.1:15174'
const outputDir = process.env.MATECLAW_CDP_SCREENSHOT_DIR ?? '/tmp/mateclaw-dashboard-cdp'
const channel = process.env.MATECLAW_E2E_BROWSER_CHANNEL ?? 'chrome'

fs.mkdirSync(outputDir, { recursive: true })

function axSummary(tree) {
  const roles = new Set(['button', 'heading', 'textbox', 'combobox', 'table', 'row', 'cell', 'alert'])
  return tree.nodes.filter(node => node.role?.value && roles.has(node.role.value)).slice(0, 120)
    .map(node => ({ role: node.role.value, name: node.name?.value ?? '' }))
}

async function capture(page, context, name, waitFor) {
  if (waitFor) await page.getByRole('heading', { name: waitFor, exact: true })
    .waitFor({ state: 'visible', timeout: 30_000 })
  const cdp = await context.newCDPSession(page)
  const tree = await cdp.send('Accessibility.getFullAXTree')
  const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  const screenshotPath = path.join(outputDir, `${name}.png`)
  fs.writeFileSync(screenshotPath, Buffer.from(screenshot.data, 'base64'))
  return { name, screenshotPath, axNodeCount: tree.nodes.length, axSummary: axSummary(tree) }
}

const browser = await chromium.launch({ channel, headless: true })
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(({ authToken, workspace }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspace))
  }, { authToken: token, workspace: workspaceId })
  const page = await context.newPage()
  await page.goto(`${baseUrl}/?nav=insight&dashboardId=${encodeURIComponent(dashboardId)}`)
  await page.waitForLoadState('networkidle')
  const results = [await capture(page, context, 'dashboard-list', '洞察仪表盘')]
  const jdbcCard = page.locator('.card-name', { hasText: 'E2E JDBC + Aloudata Dashboard' }).filter({ hasText: /^E2E JDBC \+ Aloudata Dashboard$/ }).first()
    .locator('xpath=ancestor::div[contains(@class,"dashboard-card")]')
  await jdbcCard.getByRole('button', { name: '编辑' }).click()
  await page.locator('.dataset-input-panel').waitFor({ state: 'visible', timeout: 30_000 })
  results.push(await capture(page, context, 'dashboard-editor', null))
  await page.getByRole('button', { name: '最终结果预览' }).click()
  await page.locator('.result-table tbody tr').first().waitFor({ state: 'visible', timeout: 120_000 })
  const preview = await capture(page, context, 'dashboard-preview', null)
  preview.rows = await page.locator('.result-table tbody tr').count()
  preview.contains120_5 = (await page.locator('body').innerText()).includes('120.5')
  results.push(preview)

  // 返回列表后从真实卡片进入 ECharts 预览，确保图表运行时也有 CDP 证据。
  await page.locator('.back-btn').click()
  await page.getByRole('heading', { name: '洞察仪表盘', exact: true })
    .waitFor({ state: 'visible', timeout: 30_000 })
  const echartsCard = page.locator('.card-name', { hasText: 'E2E ECharts Binding Dashboard' }).filter({ hasText: /^E2E ECharts Binding Dashboard$/ }).first()
    .locator('xpath=ancestor::div[contains(@class,"dashboard-card")]')
  await echartsCard.getByRole('button', { name: /预览/ }).click()
  await page.locator('.chart-widget').waitFor({ state: 'visible', timeout: 120_000 })
  await page.locator('.chart-container canvas').waitFor({ state: 'visible', timeout: 30_000 })
  const echartsPreview = await capture(page, context, 'dashboard-echarts-preview', null)
  echartsPreview.canvasCount = await page.locator('.chart-container canvas').count()
  echartsPreview.hasChartTitle = (await page.locator('.chart-widget').innerText()).includes('E2E Script Chart')
  results.push(echartsPreview)
  console.log(JSON.stringify({ baseUrl, dashboardId, results }, null, 2))
} finally {
  await browser.close()
}
