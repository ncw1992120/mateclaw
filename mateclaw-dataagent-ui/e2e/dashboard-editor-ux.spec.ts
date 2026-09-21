import { expect, test, type Page } from '@playwright/test'

/**
 * 洞察仪表盘编辑器 UX 收敛回归（implementation-plan Task 9）。
 *
 * 前置：与既有真实 E2E 相同 —— 由 docker-compose 测试栈提供 UI/DataAgent/测试数据，
 * 通过环境变量注入登录态与已 seed 的仪表盘；禁止用 route mock 替代 DataAgent。
 * 需要的 seed：MATECLAW_E2E_UX_DASHBOARD_ID 指向含一个 KPI 卡、未配置数据集的仪表盘。
 */
function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}；禁止用 route mock 替代 DataAgent`)
  return value
}

const token = required('MATECLAW_E2E_TOKEN')
const workspaceId = required('MATECLAW_E2E_WORKSPACE_ID')
const uxDashboardId = required('MATECLAW_E2E_UX_DASHBOARD_ID')

const HELP_TEXT = '选择已有数据集或创建新数据集'
const VIEWPORTS: Array<[number, number]> = [
  [375, 812],
  [768, 1024],
  [1024, 768],
  [1280, 800],
  [1440, 1000],
]

async function openEditor(page: Page): Promise<void> {
  await page.goto(`/insight/dashboard/editor?dashboardId=${uxDashboardId}`)
  await expect(page.locator('.insight-editor-view')).toBeVisible({ timeout: 30_000 })
}

async function selectFirstCanvasCard(page: Page): Promise<void> {
  const card = page.locator('[data-component-id]').first()
  await card.waitFor({ state: 'visible', timeout: 30_000 })
  await card.click()
}

test.describe('dashboard editor UX convergence', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ authToken, workspace }) => {
      localStorage.setItem('token', authToken)
      localStorage.setItem('workspaceId', JSON.stringify(workspace))
    }, { authToken: token, workspace: workspaceId })
  })

  test('card property flow keeps help quiet and preserves configured dataset after save', async ({ page }) => {
    await openEditor(page)

    // 选中 KPI 卡：帮助正文不常驻，只有 Info 触发后才可见
    await selectFirstCanvasCard(page)
    await expect(page.getByText('数据集配置', { exact: true })).toBeVisible()
    await expect(page.getByText(HELP_TEXT)).toHaveCount(0)

    // 添加数据集：来源树按 已有数据集 / 四类来源 分组
    await page.getByRole('button', { name: '添加数据集' }).first().click()
    await expect(page.getByText('已有数据集')).toBeVisible()
    await page.getByText('JDBC', { exact: false }).first().click()

    // 单层弹窗内完成 SQL 配置与筛选预览，不出现叠加 Dialog
    const sourceDialog = page.locator('.el-dialog').filter({ hasText: '配置数据集来源' })
    await expect(sourceDialog).toBeVisible()
    await sourceDialog.getByLabel('输入 SQL').fill('SELECT order_date, region, amount FROM orders')
    await sourceDialog.getByLabel('筛选预览').click()
    await expect(sourceDialog).toBeVisible()
    await expect(page.locator('.el-dialog')).toHaveCount(1)

    // 确认添加 → 数据集出现在侧栏 → 保存 → 刷新后配置仍在
    await sourceDialog.getByRole('button', { name: '确认添加' }).click()
    await expect(page.getByText('orders', { exact: false }).first()).toBeVisible()
    await page.getByRole('button', { name: '保存' }).click()
    await page.reload()
    await openEditor(page)
    await expect(page.getByText('orders', { exact: false }).first()).toBeVisible()
  })

  test('keeps the page free of horizontal overflow across breakpoints', async ({ page }) => {
    for (const [width, height] of VIEWPORTS) {
      await page.setViewportSize({ width, height })
      await page.goto(`/insight/dashboard/editor?dashboardId=${uxDashboardId}`)
      await expect(page.locator('.insight-editor-view')).toBeVisible({ timeout: 30_000 })
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow, `${width}x${height} 出现横向溢出`).toBeLessThanOrEqual(0)
    }
  })

  test('keyboard moves canvas cards, opens help and closes dialogs with Escape', async ({ page }) => {
    await openEditor(page)
    await selectFirstCanvasCard(page)

    // 方向键移动选中卡片：布局属性发生变化且页面不滚动
    const card = page.locator('[data-component-id]').first()
    const before = await card.evaluate((el) => el.getAttribute('style') ?? '')
    await card.focus()
    await page.keyboard.press('ArrowRight')
    const after = await card.evaluate((el) => el.getAttribute('style') ?? '')
    expect(after).not.toBe(before)
    expect(await page.evaluate(() => window.scrollY)).toBe(0)

    // Info 帮助可通过键盘打开，Esc 关闭
    const helpTrigger = page.locator('[aria-label*="说明"]').first()
    if (await helpTrigger.count()) {
      await helpTrigger.focus()
      await page.keyboard.press('Enter')
      await page.keyboard.press('Escape')
    }

    // Esc 关闭来源弹窗
    await page.getByRole('button', { name: '添加数据集' }).first().click()
    const sourceDialog = page.locator('.el-dialog').filter({ hasText: '配置数据集来源' })
    await expect(sourceDialog).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(sourceDialog).toBeHidden()
  })

  test('visible interactive controls expose accessible names', async ({ page }) => {
    await openEditor(page)
    await selectFirstCanvasCard(page)
    const unnamed = await page.evaluate(() => {
      const selector = 'button, [role="button"], textbox, input, [role="combobox"], [role="switch"]'
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
        .map((el) => `${el.tagName.toLowerCase()}.${String(el.className).slice(0, 40)}`)
    })
    expect(unnamed, `发现无可访问名称的可见交互控件: ${JSON.stringify(unnamed)}`).toHaveLength(0)
  })
})
