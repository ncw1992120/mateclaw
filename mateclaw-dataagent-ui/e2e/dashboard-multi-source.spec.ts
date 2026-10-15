import { expect, test, type Page } from '@playwright/test'

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}；禁止用 route mock 替代 DataAgent`)
  return value
}

const token = required('MATECLAW_E2E_TOKEN')
const workspaceId = required('MATECLAW_E2E_WORKSPACE_ID')
const apiFileDashboardId = required('MATECLAW_E2E_API_FILE_DASHBOARD_ID')
const largeResultDashboardId = required('MATECLAW_E2E_LARGE_RESULT_DASHBOARD_ID')
const echartsDashboardName = process.env.MATECLAW_E2E_ECHARTS_DASHBOARD_NAME ?? 'E2E ECharts Binding Dashboard'

async function openEditor(page: Page, dashboardId: string): Promise<void> {
  await page.goto(`/insight/dashboard/editor?dashboardId=${dashboardId}`)
  await expect(page.locator('.insight-editor-view')).toBeVisible({ timeout: 30_000 })
  const component = page.locator('[data-component-id]').first()
  await expect(component).toBeVisible({ timeout: 30_000 })
  await component.click()
  await expect(page.getByText('数据集配置', { exact: true })).toBeVisible({ timeout: 30_000 })
}

async function openPythonPreview(page: Page, dashboardId: string): Promise<void> {
  await openEditor(page, dashboardId)
  await page.getByRole('button', { name: /编辑 Python 脚本|展开编辑/ }).first().click()
  const dialog = page.getByRole('dialog', { name: '编辑 Python 脚本' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '筛选预览' }).click()
  await expect(page.locator('[aria-label="数据预览"]')).toBeVisible({ timeout: 120_000 })
}

test.describe('dashboard multi-source runtime', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('runs the seeded JDBC + Aloudata workflow and renders the confirmed result', async ({ page }) => {
    const aloudataMode = process.env.MATECLAW_E2E_ALOUDATA_MODE
    if (aloudataMode !== 'simulation' && process.env.MATECLAW_E2E_ALOUDATA_LIVE !== 'true') {
      throw new Error('BLOCKED: 需要已授权且可查询的 Aloudata 指标视图 ID；未使用跳过机制隐藏验收失败')
    }
    const dashboardId = process.env.MATECLAW_E2E_MULTI_SOURCE_DASHBOARD_ID
    if (!dashboardId) throw new Error('BLOCKED: 当前状态文件未提供可执行的 JDBC + Aloudata 双源看板 ID')
    const resultResponse = page.waitForResponse(async (response) =>
      response.url().includes('/executions/') && response.url().endsWith('/result') && response.request().method() === 'GET',
    )
    await openPythonPreview(page, dashboardId)
    await expect(page.locator('[aria-label="数据预览"] .el-table').first()).toContainText('120.5')
    const resultBody = await (await resultResponse).json() as { data?: { envelope?: { meta?: { rowCount?: number } } } }
    // 结果接口保留真实总行数；弹窗按预览上限展示前 10 行，避免把受控预览误判成执行丢行。
    expect(resultBody.data?.envelope?.meta?.rowCount).toBe(11)
    await expect(page.locator('[aria-label="数据预览"]')).toContainText(/共 10 条|10 条/)
    // 双源结果已由行数和 120.5 断言锁定；页面字体抗锯齿、滚动条和异步布局在
    // 同一 Chrome 通道下仍可能产生少量像素噪声，允许 2% 像素差异避免误报。
    await expect(page).toHaveScreenshot('dashboard-jdbc-aloudata.png', {
      fullPage: true,
      maxDiffPixelRatio: 0.02,
    })
  })

  test('runs the seeded API + file workflow and renders the confirmed result', async ({ page }) => {
    await openPythonPreview(page, apiFileDashboardId)
    await expect(page.locator('[aria-label="数据预览"] .el-table').first()).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('[aria-label="数据预览"]')).toContainText('PAID')
    await expect(page).toHaveScreenshot('dashboard-api-file.png', {
      fullPage: true,
      mask: [page.locator('.dataset-input-panel')],
      maxDiffPixelRatio: 0.02,
    })
  })

  test('reads a large Runner result through the DataAgent ObjectRef endpoint', async ({ page }) => {
    const resultResponse = page.waitForResponse(async (response) => {
      if (!response.url().includes('/executions/') || !response.url().endsWith('/result')) return false
      if (response.request().method() !== 'GET') return false
      const body = await response.json() as { data?: { inline?: boolean; outputRef?: unknown } }
      return body.data?.inline === false && Boolean(body.data?.outputRef)
    })
    await openPythonPreview(page, largeResultDashboardId)
    await resultResponse
    await expect(page.locator('[aria-label="数据预览"] .el-table').first()).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('[aria-label="数据预览"]')).toContainText('共 10 条')
  })

  test('runs a saved ECharts binding in dashboard preview', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.card-name').filter({ hasText: echartsDashboardName }).first().locator('xpath=ancestor::div[contains(@class,"dashboard-card")]')
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /预览/ }).click()
    await expect(page.locator('.dashboard-preview-view')).toBeVisible()
    await expect(page.locator('.chart-widget')).toHaveCount(1, { timeout: 120_000 })
    await expect(page.locator('.chart-container canvas')).toHaveCount(1, { timeout: 30_000 })
    await expect(page.locator('.chart-widget')).toContainText('E2E Script Chart')
    // Canvas 抗锯齿在同一浏览器版本下也可能产生少量像素差异；结构和
    // canvas 数量仍由上面的断言严格校验，快照只允许 2% 的渲染噪声。
    await expect(page).toHaveScreenshot('dashboard-echarts-binding.png', {
      fullPage: true,
      maxDiffPixelRatio: 0.02,
    })
  })
})
