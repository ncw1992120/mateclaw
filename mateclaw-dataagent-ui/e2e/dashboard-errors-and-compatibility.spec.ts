import { expect, test, type Page } from '@playwright/test'

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

function cardByName(page: Page, name: string) {
  return page.locator('.card-name').filter({ hasText: name }).first().locator('xpath=ancestor::div[contains(@class,"dashboard-card")]')
}

async function openPythonPreview(page: Page, name: string): Promise<void> {
  await page.goto(`/insight/dashboard/editor?dashboardId=${name}`)
  await expect(page.locator('.insight-editor-view')).toBeVisible({ timeout: 30_000 })
  const card = page.locator('[data-component-id]').first()
  await expect(card).toBeVisible({ timeout: 30_000 })
  await card.click()
  await expect(page.getByText('数据集配置', { exact: true })).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: /编辑 Python 脚本|展开编辑/ }).first().click()
  const dialog = page.getByRole('dialog', { name: '编辑 Python 脚本' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '筛选预览' }).click()
  await expect(page.locator('[aria-label="数据预览"]')).toBeVisible({ timeout: 120_000 })
}

test.describe('dashboard errors and legacy compatibility', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('keeps the legacy dashboard readable', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = cardByName(page, 'E2E Legacy Compatibility Dashboard')
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    // The script panel can render before the legacy schema has populated the
    // editor. Wait for the dashboard title so the screenshot is not captured
    // against the transient empty editor state.
    await expect(page.locator('.toolbar-title')).toHaveText('E2E Legacy Compatibility Dashboard')
    await expect(page).toHaveScreenshot('dashboard-compatibility.png', { fullPage: true, maxDiffPixelRatio: 0.02 })
  })

  test('displays a real script execution error in the preview dialog', async ({ page }) => {
    await openPythonPreview(page, required('MATECLAW_E2E_ERROR_DASHBOARD_ID'))
    await expect(page.locator('.insight-dialog--preview .el-alert')).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('.insight-dialog--preview .el-alert')).toContainText(/e2e expected script failure|执行失败|脚本异常/)
  })

  test('cancellation remains covered by the Runner/DataAgent integration layer', async ({ page }) => {
    test.skip(true, '现行 Python 编辑器预览流程不暴露取消执行按钮；取消语义由 Runner/DataAgent 集成测试覆盖')
  })

  test('displays a real script timeout in the preview dialog', async ({ page }) => {
    await openPythonPreview(page, required('MATECLAW_E2E_TIMEOUT_DASHBOARD_ID'))
    await expect(page.locator('.insight-dialog--preview .el-alert')).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('.insight-dialog--preview .el-alert')).toContainText(/timed out|超时|执行失败/)
  })

  test('enforces a real stdout resource limit in the preview dialog', async ({ page }) => {
    await openPythonPreview(page, required('MATECLAW_E2E_RESOURCE_DASHBOARD_ID'))
    await expect(page.locator('.insight-dialog--preview .el-alert')).toBeVisible({ timeout: 120_000 })
    await expect(page.locator('.insight-dialog--preview .el-alert')).toContainText(/OUTPUT_LIMIT|输出超过限制|执行失败/)
  })
})
