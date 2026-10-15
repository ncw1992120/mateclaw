import { expect, test } from '@playwright/test'

test.describe('洞察仪表盘主题外观', () => {
  test('编辑主题草稿、保存并进入正式预览', async ({ page }) => {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: /策略解读/ }).first()
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /编辑/ }).click()
    await expect(page.locator('.insight-editor-view')).toBeVisible()
    await page.getByRole('button', { name: '主题外观' }).click()
    await expect(page.locator('.theme-presets')).toBeVisible()
    await page.locator('[data-preset-id="teal"]').click()
    await expect(page.locator('.theme-preview-section')).toContainText('青碧')
    await page.getByRole('button', { name: /保存/ }).click()
    await expect(page.getByText(/保存成功/)).toBeVisible()
    await page.getByRole('button', { name: /预览/ }).click()
    await expect(page.locator('.dashboard-canvas')).toBeVisible()
    await expect(page.locator('.dashboard-canvas')).toHaveCSS('--insight-primary', '#0F766E')
  })
})
