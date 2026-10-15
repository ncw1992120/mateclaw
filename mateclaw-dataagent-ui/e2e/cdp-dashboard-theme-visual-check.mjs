import { chromium } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'

/**
 * 主题外观真实 Chrome 视觉验收：只连接用户已经启动的 Chrome 9222，
 * 只创建和关闭自己的 Page，不关闭用户浏览器、不保存认证信息、不点击保存。
 */
const endpoint = process.env.MATECLAW_CDP_ENDPOINT ?? 'http://127.0.0.1:9222'
const baseUrl = process.env.MATECLAW_UI_BASE_URL ?? 'http://127.0.0.1:5174'
const candidateSha = execSync('git rev-parse HEAD', { cwd: process.cwd() }).toString().trim()
const outputDir = process.env.MATECLAW_CDP_THEME_EVIDENCE_DIR ?? `/tmp/mateclaw-dashboard-theme-${candidateSha.slice(0, 8)}`
fs.mkdirSync(outputDir, { recursive: true })

const browser = await chromium.connectOverCDP(endpoint)
const context = browser.contexts()[0]
if (!context) throw new Error('9222 上没有可用的 Google Chrome context')
const page = await context.newPage()
const consoleErrors = []
const failedRequests = []
page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text().slice(0, 300)) })
page.on('requestfailed', (request) => failedRequests.push(`FAILED ${request.url().slice(0, 180)}`))
page.on('response', (response) => { if (response.status() >= 400) failedRequests.push(`${response.status()} ${response.url().slice(0, 180)}`) })

const results = []
let completed = false
async function capture(name, detail = '') {
  const screenshotPath = path.join(outputDir, `${name}.png`)
  await page.screenshot({ path: screenshotPath, fullPage: false })
  const ax = await page.locator('body').ariaSnapshot().catch(() => '')
  const computed = await page.evaluate(() => {
    const root = document.querySelector('.dashboard-canvas, .insight-editor-view') ?? document.body
    const style = getComputedStyle(root)
    const firstChart = document.querySelector('.chart-container')
    return {
      width: window.innerWidth,
      height: window.innerHeight,
      overflowX: document.documentElement.scrollWidth > window.innerWidth,
      background: style.backgroundColor,
      primary: style.getPropertyValue('--insight-primary').trim(),
      chartColor: style.getPropertyValue('--insight-chart-1').trim(),
      chartPresent: Boolean(firstChart),
    }
  })
  results.push({ name, screenshotPath, detail, computed, axSummary: ax.slice(0, 4000) })
}

try {
  await page.goto(`${baseUrl}/?nav=insight`, { waitUntil: 'domcontentloaded' })
  await page.locator('.dashboard-card').first().waitFor({ state: 'visible', timeout: 30_000 })
  await capture('01-insight-list')

  const cards = page.locator('.dashboard-card')
  let card = cards.filter({ hasText: /策略解读/ }).first()
  if (!(await card.count())) card = cards.first()
  const dashboardName = (await card.locator('.card-name').innerText()).trim()
  await card.getByRole('button', { name: /编辑/ }).click()
  await page.locator('.insight-editor-view').waitFor({ state: 'visible', timeout: 30_000 })
  await page.getByRole('button', { name: '主题外观' }).click()
  await page.locator('.theme-presets').waitFor({ state: 'visible', timeout: 15_000 })
  await capture('02-theme-panel-1440', `仪表盘：${dashboardName}`)

  const presets = ['blue', 'indigo', 'teal', 'amber', 'dark-data', 'rose', 'coral', 'orange', 'gold', 'burgundy']
  for (const preset of presets) {
    const button = page.locator(`[data-preset-id="${preset}"]`)
    await button.click()
    await page.waitForTimeout(120)
    await capture(`03-preset-${preset}`)
  }

  for (const viewport of [{ width: 375, height: 800 }, { width: 768, height: 900 }, { width: 1024, height: 900 }, { width: 1440, height: 1000 }]) {
    await page.setViewportSize(viewport)
    await capture(`04-viewport-${viewport.width}`)
  }

  await page.keyboard.press('Escape')
  await page.locator('.back-btn').click().catch(() => {})
  await page.waitForTimeout(300)
  console.log(JSON.stringify({ candidateSha, endpoint, baseUrl, dashboardName, results, consoleErrors, failedRequests }, null, 2))
  completed = true
} finally {
  await page.close()
  // Playwright 的 CDP 连接没有公开 disconnect API；成功后主动结束脚本，
  // 避免为了释放 Node 句柄调用 browser.close() 进而关闭用户 Chrome。
  if (completed) process.exit(0)
}
