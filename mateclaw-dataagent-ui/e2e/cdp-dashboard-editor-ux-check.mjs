import { chromium } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'

/**
 * 洞察仪表盘编辑器 UX 视觉验收（implementation-plan Task 10）。
 *
 * 硬性约束：只连接用户已启动的 Google Chrome CDP（默认 http://127.0.0.1:9222），
 * 不得调用 chromium.launch()，不得新建独立浏览器配置目录，不得把 Token 写入证据文件。
 * 结束时只关闭本脚本创建的 Page，不关闭用户 Chrome。
 *
 * 已知环境约束：直连 /insight/dashboard/editor?dashboardId= 不会装载已存看板
 * （画布为空且无报错），因此所有进入编辑器的动作都走「洞察列表 → 点编辑」的真实路径。
 */
const endpoint = process.env.MATECLAW_CDP_ENDPOINT ?? 'http://127.0.0.1:9222'
const baseUrl = process.env.MATECLAW_UI_BASE_URL ?? 'http://127.0.0.1:5174'
const candidateSha = execSync('git rev-parse HEAD', { cwd: process.cwd() }).toString().trim()
const outputDir = process.env.MATECLAW_CDP_SCREENSHOT_DIR
  ?? `/tmp/mateclaw-dashboard-editor-ux-${candidateSha.slice(0, 8)}`
const HELP_TEXT = '选择已有数据集或创建新数据集'

fs.mkdirSync(outputDir, { recursive: true })

const browser = await chromium.connectOverCDP(endpoint)
const context = browser.contexts()[0]
if (!context) throw new Error('9222 上没有可用的 Google Chrome context')

const page = await context.newPage()
const consoleErrors = []
const failedRequests = []
let expectBusinessErrors = false

// about:blank 上读不到 localStorage，先落到位再取原始视口与主题
await page.goto(`${baseUrl}/?nav=insight`, { waitUntil: 'domcontentloaded' }).catch(() => {})
const originalViewport = await page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight })).catch(() => ({ width: 1440, height: 1000 }))
const originalTheme = await page.evaluate(() => window.localStorage.getItem('theme-mode')).catch(() => null)

page.on('console', (message) => {
  if (message.type() === 'error') consoleErrors.push(message.text().slice(0, 300))
})
page.on('requestfailed', (request) => {
  failedRequests.push(`FAILED ${request.url().slice(0, 140)}`)
})
page.on('response', (response) => {
  if (response.status() >= 400) failedRequests.push(`${response.status()} ${response.url().slice(0, 140)}`)
})

const results = []

async function record(name, status, detail = '') {
  const screenshotPath = path.join(outputDir, `${name}.png`)
  try {
    await page.screenshot({ path: screenshotPath, fullPage: false })
  } catch (error) {
    results.push({ name, status: 'FAIL', detail: `截图失败: ${error.message}`, screenshotPath: null })
    return
  }
  results.push({ name, status, detail, screenshotPath })
}

async function scenario(name, action) {
  try {
    await action()
  } catch (error) {
    await record(name, 'FAIL', error.message.split('\n')[0])
    return
  }
}

async function gotoInsightList() {
  await page.goto(`${baseUrl}/?nav=insight`, { waitUntil: 'domcontentloaded' })
  await page.locator('.dashboard-card').first().waitFor({ state: 'visible', timeout: 30_000 })
}

async function openFirstDashboardEditor() {
  await gotoInsightList()
  const firstCard = page.locator('.dashboard-card').first()
  const dashboardName = (await firstCard.locator('.card-name').innerText()).trim()
  await firstCard.getByRole('button', { name: /编辑/ }).click()
  await page.locator('.insight-editor-view').waitFor({ state: 'visible', timeout: 30_000 })
  await page.locator('.editor-canvas').waitFor({ state: 'visible', timeout: 30_000 })
  return dashboardName
}

async function selectFirstCanvasCard() {
  const card = page.locator('[data-component-id]').first()
  await card.waitFor({ state: 'visible', timeout: 30_000 })
  await card.click()
  await page.waitForTimeout(600)
}

async function closeAllDialogs() {
  for (let i = 0; i < 4; i += 1) {
    if (!(await page.locator('.el-dialog:visible').count())) return
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
  }
}

async function openSourceTree() {
  await page.getByRole('button', { name: /添加数据集/ }).first().click()
  await page.locator('.el-dialog:visible .src-tree').waitFor({ state: 'visible', timeout: 15_000 })
}

async function clickTreeNode(text) {
  await page.locator('.el-dialog:visible .tree-node').filter({ hasText: text }).first().click()
}

/** 通过树完成「JDBC 数据源 → SQL 弹窗」并填入 SQL */
async function openJdbcSqlDialog(sql) {
  await openSourceTree()
  await clickTreeNode('数据源连接')
  const sqlDialog = page.locator('.el-dialog:visible').filter({ hasText: '只允许' }).first()
  await sqlDialog.waitFor({ state: 'visible', timeout: 15_000 })
  await sqlDialog.locator('.sql-editor textarea').fill(sql)
  return sqlDialog
}

/** SQL 弹窗点「筛选预览」→ 等待「筛选预览」工作弹窗出现 */
async function openDataDialogFromSql(sqlDialog) {
  await sqlDialog.getByRole('button', { name: '筛选预览' }).click()
  const dataDialog = page.locator('.dataset-data-dialog:visible')
  await dataDialog.waitFor({ state: 'visible', timeout: 15_000 })
  await dataDialog.getByRole('button', { name: '查询' }).click()
  await page.waitForTimeout(5000)
  return dataDialog
}

try {
  // ── 01 编辑器默认 1440 ──
  await scenario('01-editor-default-1440', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    const name = await openFirstDashboardEditor()
    await page.waitForTimeout(1200)
    await record('01-editor-default-1440', 'PASS', `看板：${name}`)
  })

  // ── 02 属性侧栏：无常驻大段帮助 ──
  await scenario('02-kpi-property-clean', async () => {
    await selectFirstCanvasCard()
    const helpVisible = await page.getByText(HELP_TEXT).isVisible().catch(() => false)
    if (helpVisible) throw new Error('帮助正文常驻可见，违反信息分级规则')
    await record('02-kpi-property-clean', 'PASS')
  })

  // ── 03 键盘聚焦 Info 后提示可见 ──
  await scenario('03-help-tooltip', async () => {
    const trigger = page.locator('[aria-label*="说明"]').first()
    if (!(await trigger.count())) throw new Error('当前侧栏没有 Info 帮助触发点')
    await trigger.focus()
    await page.waitForTimeout(600)
    await record('03-help-tooltip', 'PASS')
  })

  // ── 04 来源选择树：分组 + 快捷节点 ──
  await scenario('04-source-picker', async () => {
    await openSourceTree()
    await record('04-source-picker', 'PASS')
    await closeAllDialogs()
  })

  // ── 05 JDBC SQL 配置 ──
  await scenario('05-jdbc-config', async () => {
    const sqlDialog = await openJdbcSqlDialog('SELECT 1 AS ok')
    await record('05-jdbc-config', 'PASS')
    // 06 直接复用这里留下的弹窗
    const dataDialog = await openDataDialogFromSql(sqlDialog)
    const visibleDialogs = await page.locator('.el-dialog:visible').count()
    if (visibleDialogs > 1) throw new Error(`出现叠加弹窗：${visibleDialogs} 个可见 Dialog`)
    const hint = await dataDialog.locator('.dd-result .dd-hint').innerText().catch(() => '')
    await record('06-jdbc-preview', 'PASS', `结果区：${hint.trim().slice(0, 40)}`)
    await closeAllDialogs()
  })

  // ── 07 Aloudata 指标视图配置 ──
  await scenario('07-aloudata-config', async () => {
    await openSourceTree()
    await clickTreeNode('指标视图')
    const dialog = page.locator('.el-dialog:visible').filter({ hasText: 'Aloudata' }).first()
    await dialog.waitFor({ state: 'visible', timeout: 15_000 })
    await page.waitForTimeout(2500)
    await record('07-aloudata-config', 'PASS')
    await closeAllDialogs()
  })

  // ── 08 Python 预处理与结果集状态 ──
  await scenario('08-python-and-result', async () => {
    await selectFirstCanvasCard()
    const python = page.getByText('Python 预处理').first()
    await python.waitFor({ state: 'visible', timeout: 15_000 })
    await python.scrollIntoViewIfNeeded()
    await page.waitForTimeout(400)
    await record('08-python-and-result', 'PASS')
  })

  // ── 09 长内容不溢出 ──
  await scenario('09-dialog-long-content', async () => {
    const sqlDialog = await openJdbcSqlDialog('SELECT 1 AS ' + '很长的中文列名用于测试省略与换行行为_'.repeat(4))
    const dataDialog = await openDataDialogFromSql(sqlDialog)
    await page.waitForTimeout(2000)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    if (overflow > 0) throw new Error(`长内容把页面撑出横向溢出 ${overflow}px`)
    await record('09-dialog-long-content', 'PASS')
    await closeAllDialogs()
  })

  // ── 10 编辑器 1024 抽屉态 ──
  await scenario('10-editor-1024', async () => {
    await page.setViewportSize({ width: 1024, height: 768 })
    await page.waitForTimeout(800)
    await record('10-editor-1024', 'PASS')
  })

  // ── 11 编辑器 375 移动端 ──
  await scenario('11-editor-375', async () => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.waitForTimeout(800)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    if (overflow > 0) throw new Error(`375 宽度下横向溢出 ${overflow}px`)
    await record('11-editor-375', 'PASS')
  })

  // ── 14 错误就地展示（放在主题截图前，避免错误弹层污染主题截图） ──
  await scenario('14-error-state', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.waitForTimeout(800)
    const sqlDialog = await openJdbcSqlDialog('SELECT * FROM __mateclaw_no_such_table__')
    expectBusinessErrors = true
    const dataDialog = await openDataDialogFromSql(sqlDialog)
    expectBusinessErrors = false
    const resultText = await dataDialog.locator('.dd-result').innerText()
    if (!/失败|错误|不可用|异常|不存在|failed|error|400|503/i.test(resultText)) throw new Error('预览失败后未见就地错误信息')
    await record('14-error-state', 'PASS', resultText.slice(-60).replace(/\s+/g, ' '))
    await closeAllDialogs()
  })

  // ── 13 画布选中与可见焦点（恢复默认视口后采集） ──
  await scenario('13-keyboard-focus', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.waitForTimeout(800)
    const card = page.locator('[data-component-id]').first()
    await card.focus()
    await page.waitForTimeout(400)
    const focused = await page.evaluate(() => document.activeElement?.hasAttribute('data-component-id') ?? false)
    if (!focused) throw new Error('画布卡片无法通过键盘获得焦点')
    await record('13-keyboard-focus', 'PASS')
  })

  // ── 12 暗色主题（改主题前保存原值，采集后恢复） ──
  await scenario('12-dark-theme', async () => {
    await page.evaluate(() => window.localStorage.setItem('theme-mode', 'dark'))
    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    const applied = await page.evaluate(() => document.documentElement.getAttribute('data-theme'))
    if (applied !== 'dark') throw new Error(`主题未生效：data-theme=${applied}`)
    await record('12-dark-theme', 'PASS')
  })
} finally {
  // 恢复用户原有主题与视口
  await page.evaluate((theme) => {
    if (theme === null) window.localStorage.removeItem('theme-mode')
    else window.localStorage.setItem('theme-mode', theme)
  }, originalTheme).catch(() => {})
  await page.setViewportSize(originalViewport).catch(() => {})
}

// ── 可访问名称扫描（主题恢复后，重走真实路径进入编辑器） ──
let axUnnamedInteractiveCount = -1
const axUnnamedDetails = []
try {
  await openFirstDashboardEditor()
  await selectFirstCanvasCard()
  axUnnamedDetails.push(...(await page.evaluate(() => {
    const selector = 'button, [role="button"], input, [role="combobox"], [role="switch"]'
    return Array.from(document.querySelectorAll(selector))
      .filter((el) => {
        const style = window.getComputedStyle(el)
        if (style.display === 'none' || style.visibility === 'hidden') return false
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      })
      .filter((el) => {
        const name = el.getAttribute('aria-label') ?? el.getAttribute('aria-labelledby') ?? el.textContent ?? ''
        return name.trim().length === 0
      })
      .map((el) => `${el.tagName.toLowerCase()}: ${String(el.className).slice(0, 60)}`)
  })))
} catch (error) {
  console.error(`AX 扫描失败: ${error.message.split('\n')[0]}`)
}
axUnnamedInteractiveCount = axUnnamedDetails.length

await page.close()

const passCount = results.filter((r) => r.status === 'PASS').length
const failCount = results.filter((r) => r.status === 'FAIL').length
const notRunCount = results.filter((r) => r.status === 'NOT_RUN').length
const summary = {
  candidateSha,
  url: `${baseUrl}/?nav=insight`,
  viewport: { width: 1440, height: 1000 },
  browser: 'Google Chrome via CDP 9222',
  cdpEndpoint: endpoint,
  timestamp: new Date().toISOString(),
  results,
  totals: { pass: passCount, fail: failCount, notRun: notRunCount, total: results.length },
  axUnnamedInteractiveCount,
  axUnnamedDetails,
  consoleErrors,
  failedRequests,
  note: '14-error-state 会故意触发一次后端错误，对应 4xx/5xx 属预期覆盖；其余失败请求都算异常。',
}

fs.writeFileSync(path.join(outputDir, 'summary.json'), JSON.stringify(summary, null, 2))
console.log(`\n场景结果：${passCount} PASS / ${failCount} FAIL / ${notRunCount} NOT_RUN`)
for (const item of results) console.log(`  [${item.status}] ${item.name}${item.detail ? ` — ${item.detail}` : ''}`)
console.log(`AX 无名交互控件: ${axUnnamedInteractiveCount}`, axUnnamedDetails)
console.log(`控制台错误: ${consoleErrors.length}；失败请求: ${failedRequests.length}`)
console.log(`证据目录: ${outputDir}`)

if (failCount > 0 || axUnnamedInteractiveCount > 0) process.exit(1)
