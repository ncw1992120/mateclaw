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

async function openEditor(page: Page, name: string): Promise<void> {
  const card = page.locator('.dashboard-card').filter({ hasText: name })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: /编辑/ }).click()
}

test.describe('dashboard multi-source runtime', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('runs the seeded JDBC + Aloudata workflow and renders the confirmed result', async ({ page }) => {
    if (process.env.MATECLAW_E2E_ALOUDATA_LIVE !== 'true') {
      throw new Error('BLOCKED: 需要已授权且可查询的 Aloudata 指标视图 ID；未使用跳过机制隐藏验收失败')
    }
    await page.goto('/?nav=insight')
    await openEditor(page, 'E2E JDBC + Aloudata Dashboard')
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.script-draft')).toContainText('[]')
    await expect(page).toHaveScreenshot('dashboard-jdbc-aloudata.png', { fullPage: true })
  })

  test('runs the seeded API + file workflow and renders the confirmed result', async ({ page }) => {
    await page.goto('/?nav=insight')
    await openEditor(page, 'E2E API + File Dashboard')
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.script-draft')).toContainText('[]')
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
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await resultResponse
    await expect(page.locator('.result-table tbody tr')).toHaveCount(10)
  })
})
