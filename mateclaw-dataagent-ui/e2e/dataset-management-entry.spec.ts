import { expect, test } from '@playwright/test'

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}`)
  return value
}

test('从产品入口进入数据集创建并取消返回', async ({ page }) => {
  await page.addInitScript(({ authToken, workspace }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspace))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspace: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  await page.getByRole('button', { name: '数据配置' }).click()
  await page.getByRole('button', { name: '数据集管理' }).click()
  await expect(page).toHaveURL(/\/datasets$/)
  await page.getByRole('button', { name: '新建数据集' }).click()
  await expect(page).toHaveURL(/\/datasets\/new$/)
  await expect(page.getByText('来源类型')).toBeVisible()
  await page.getByRole('combobox').nth(1).selectOption('FILE')
  await expect(page.getByText('选择并上传文件')).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page).toHaveURL(/\/datasets$/)
})
