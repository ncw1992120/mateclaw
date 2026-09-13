import { expect, test } from '@playwright/test'

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}；禁止用 route mock 替代 DataAgent`)
  return value
}

const token = required('MATECLAW_E2E_TOKEN')
const workspaceId = required('MATECLAW_E2E_WORKSPACE_ID')
const dashboardId = required('MATECLAW_E2E_COMPATIBILITY_DASHBOARD_ID')
required('MATECLAW_E2E_ERROR_DASHBOARD_ID')
required('MATECLAW_E2E_CANCEL_DASHBOARD_ID')
required('MATECLAW_E2E_TIMEOUT_DASHBOARD_ID')
required('MATECLAW_E2E_RESOURCE_DASHBOARD_ID')

test.describe('dashboard errors and legacy compatibility', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('keeps the legacy dashboard readable', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E Legacy Compatibility Dashboard' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /预览/ }).click()
    await expect(page.locator('body')).not.toContainText('invalid dashboard schema')
    await page.getByRole('button', { name: /编辑/ }).first().click()
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    // The script panel can render before the legacy schema has populated the
    // editor. Wait for the dashboard title so the screenshot is not captured
    // against the transient empty editor state.
    await expect(page.locator('.toolbar-title')).toHaveText('E2E Legacy Compatibility Dashboard')
    await expect(page).toHaveScreenshot('dashboard-compatibility.png', { fullPage: true })
  })

  test('displays a real script execution error and keeps retry available', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E Script Error Dashboard' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
    await expect(page.locator('.execution-alert')).toContainText('e2e expected script failure')
  })

  test('cancels a real running script and exposes retry', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E Cancellation Dashboard' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    const cancel = page.getByRole('button', { name: '取消执行' })
    await expect(cancel).toBeVisible({ timeout: 15_000 })
    await cancel.click()
    await expect(page.locator('.execution-alert')).toContainText('执行已取消', { timeout: 30_000 })
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })

  test('displays a real script timeout and keeps retry available', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E Script Timeout Dashboard' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toBeVisible({ timeout: 30_000 })
    await expect(page.locator('.execution-alert')).toContainText('timed out', { timeout: 15_000 })
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })

  test('enforces a real stdout resource limit and keeps retry available', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: 'E2E Resource Limit Dashboard' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    await expect(page.getByText('脚本数据集输入')).toBeVisible()
    await page.getByRole('button', { name: '最终结果预览' }).click()
    await expect(page.locator('.execution-alert')).toBeVisible({ timeout: 30_000 })
    await expect(page.locator('.execution-alert')).toContainText('OUTPUT_LIMIT', { timeout: 15_000 })
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })
})
