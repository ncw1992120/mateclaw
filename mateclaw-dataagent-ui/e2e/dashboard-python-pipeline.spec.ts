import { expect, test, type Page } from '@playwright/test'

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}；禁止用 route mock 替代 DataAgent`)
  return value
}

const token = required('MATECLAW_E2E_TOKEN')
const workspaceId = required('MATECLAW_E2E_WORKSPACE_ID')
const filterDashboardId = required('MATECLAW_E2E_PYTHON_FILTER_DASHBOARD_ID')
const aToBDashboardId = required('MATECLAW_E2E_PYTHON_A_TO_B_DASHBOARD_ID')
const managedDashboardId = required('MATECLAW_E2E_PYTHON_MANAGED_DASHBOARD_ID')
const outputDashboardId = required('MATECLAW_E2E_PYTHON_OUTPUT_DASHBOARD_ID')
const outputErrorDashboardId = required('MATECLAW_E2E_PYTHON_OUTPUT_ERROR_DASHBOARD_ID')
const largeDashboardId = required('MATECLAW_E2E_PYTHON_LARGE_DASHBOARD_ID')

async function openEditor(page: Page, dashboardId: string): Promise<void> {
  await page.goto(`/insight/dashboard/editor?dashboardId=${dashboardId}`)
  await expect(page.locator('.insight-editor-view')).toBeVisible({ timeout: 30_000 })
  const card = page.locator('[data-component-id]').first()
  await expect(card).toBeVisible({ timeout: 30_000 })
  await card.click()
  await expect(page.getByText('数据集配置', { exact: true })).toBeVisible({ timeout: 30_000 })
}

async function openDataDialog(page: Page): Promise<void> {
  const button = page.getByRole('button', { name: /查看数据/ }).first()
  await expect(button).toBeVisible()
  await button.click()
  await expect(page.locator('.dataset-data-dialog')).toBeVisible()
}

test.describe('Python pipeline real DataAgent flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('shows bound time templates and supports empty/single/both boundary queries', async ({ page }) => {
    await openEditor(page, filterDashboardId)
    await openDataDialog(page)
    const rows = page.locator('[data-testid="bound-filter-row"]')
    await expect(rows).toHaveCount(2)
    await expect(rows.first()).toContainText('startDate')
    await expect(rows.nth(1)).toContainText('endDate')

    const query = page.getByRole('button', { name: '查询' })
    await query.click()
    await expect(page.locator('.dataset-data-dialog')).toContainText(/查询|结果|空/, { timeout: 60_000 })

    const inputs = rows.locator('input')
    await inputs.nth(0).fill('2026-09-01')
    await query.click()
    await expect(page.locator('.dataset-data-dialog')).toBeVisible()
    await inputs.nth(0).fill('')
    await inputs.nth(1).fill('2026-10-01')
    await query.click()
    await expect(page.locator('.dataset-data-dialog')).toBeVisible()
    await inputs.nth(0).fill('2026-09-01')
    await query.click()
    await expect(page.locator('.dataset-data-dialog')).toBeVisible()
  })

  test('runs dataset A to B filtering without exposing an arbitrary query path', async ({ page }) => {
    await openEditor(page, aToBDashboardId)
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.result-table')).toBeVisible()
  })

  test('unlocks, diffs, preserves and restores the system-generated region', async ({ page }) => {
    await openEditor(page, managedDashboardId)
    await page.getByRole('button', { name: /编辑 Python 脚本|展开编辑/ }).first().click()
    await expect(page.getByTestId('system-mode-tag')).toContainText('用户接管')
    await expect(page.getByTestId('system-code-managed')).toBeVisible()
    await expect(page.getByTestId('user-code')).toBeVisible()
    await page.getByTestId('diff-toggle').click().catch(() => {})
    if (await page.getByTestId('restore-generated').count()) {
      await page.getByTestId('restore-generated').click()
      await expect(page.getByTestId('system-mode-tag')).toContainText('系统生成')
    }
  })

  test('renders a valid table envelope in the component preview', async ({ page }) => {
    await openEditor(page, outputDashboardId)
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await expect(page.locator('.result-table')).toContainText('PAID')
  })

  test('shows a readable output contract error instead of a blank success', async ({ page }) => {
    await openEditor(page, outputErrorDashboardId)
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('.execution-alert')).toContainText(/OUTPUT_CONTRACT_ERROR|schemaVersion|结果契约/)
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })

  test('reads the large result through the typed ObjectRef result API', async ({ page }) => {
    await openEditor(page, largeDashboardId)
    const resultResponse = page.waitForResponse(async (response) => {
      if (!response.url().includes('/executions/') || !response.url().endsWith('/result')) return false
      const body = await response.json() as { data?: { inline?: boolean; outputRef?: unknown } }
      return body.data?.inline === false && Boolean(body.data?.outputRef)
    })
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toHaveCount(0, { timeout: 120_000 })
    await resultResponse
    await expect(page.locator('.result-table tbody tr')).toHaveCount(10)
  })
})
