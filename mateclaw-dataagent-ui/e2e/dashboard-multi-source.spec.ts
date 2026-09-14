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
const echartsDashboardId = required('MATECLAW_E2E_ECHARTS_DASHBOARD_ID')

async function openEditor(page: Page, name: string): Promise<void> {
  const card = page.locator('.dashboard-card').filter({ hasText: name })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: /编辑/ }).click()
}

async function loadInputDescriptors(page: Page, expectedCount: number): Promise<void> {
  const rows = page.locator('.dataset-input-row')
  await expect(rows).toHaveCount(expectedCount)
  for (let index = 0; index < expectedCount; index += 1) {
    const row = rows.nth(index)
    const viewFields = row.getByRole('button', { name: '查看字段' })
    if (await viewFields.count()) await viewFields.click()
  }
  for (let index = 0; index < expectedCount; index += 1) {
    await expect(rows.nth(index).locator('.descriptor-summary').filter({ hasText: /[1-9]\d* 个字段/ })).toBeVisible({ timeout: 30_000 })
  }
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
    await page.goto('/?nav=insight')
    await openEditor(page, 'E2E JDBC + Aloudata Dashboard')
    await expect(page.getByText('脚本结果数据集输入')).toBeVisible()
    await loadInputDescriptors(page, 2)
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.result-table')).toContainText('120.5')
    await expect(page.locator('.result-table tbody tr')).toHaveCount(5)
    // 双源结果已由行数和 120.5 断言锁定；页面字体抗锯齿、滚动条和异步布局在
    // 同一 Chrome 通道下仍可能产生少量像素噪声，允许 2% 像素差异避免误报。
    await expect(page).toHaveScreenshot('dashboard-jdbc-aloudata.png', {
      fullPage: true,
      maxDiffPixelRatio: 0.02,
    })
  })

  test('runs the seeded API + file workflow and renders the confirmed result', async ({ page }) => {
    await page.goto('/?nav=insight')
    await openEditor(page, 'E2E API + File Dashboard')
    await expect(page.getByText('脚本结果数据集输入')).toBeVisible()
    await loadInputDescriptors(page, 2)
    await expect(page.locator('.dataset-input-panel')).not.toContainText('0 个字段', { timeout: 30_000 })
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.script-draft textarea')).toHaveValue(/PAID/)
    await expect(page.locator('.result-table tbody tr')).toHaveCount(4)
    await expect(page).toHaveScreenshot('dashboard-api-file.png', {
      fullPage: true,
      mask: [page.locator('.dataset-input-panel')],
    })
  })

  test('reads a large Runner result through the DataAgent ObjectRef endpoint', async ({ page }) => {
    const resultResponse = page.waitForResponse(async (response) => {
      if (!response.url().includes('/executions/') || !response.url().endsWith('/result')) return false
      if (response.request().method() !== 'GET') return false
      const body = await response.json() as { data?: { inline?: boolean; outputRef?: unknown } }
      return body.data?.inline === false && Boolean(body.data?.outputRef)
    })
    await page.goto(`/?nav=insight&dashboardId=${largeResultDashboardId}`)
    await openEditor(page, 'E2E Large Result Dashboard')
    await expect(page.getByText('脚本结果数据集输入')).toBeVisible()
    await loadInputDescriptors(page, 1)
    await expect(page.locator('.dataset-input-panel')).not.toContainText('0 个字段', { timeout: 30_000 })
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await resultResponse
    await expect(page.locator('.result-table tbody tr')).toHaveCount(10)
  })

  test('runs a saved ECharts binding in dashboard preview', async ({ page }) => {
    await page.goto(`/?nav=insight&dashboardId=${echartsDashboardId}`)
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E ECharts Binding Dashboard' })
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
