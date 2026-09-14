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

test('从产品入口创建文件数据集并进入预览', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })

  await page.goto('/?nav=config')
  await page.getByRole('button', { name: '数据配置' }).click()
  await page.getByRole('button', { name: '数据集管理' }).click()
  await page.getByRole('button', { name: '新建数据集' }).click()

  const datasetName = `E2E File Dataset ${Date.now()}`
  await page.locator('#dataset-name').fill(datasetName)
  await page.locator('#dataset-source-type').selectOption('FILE')
  await page.locator('input[type="file"]').setInputFiles({
    name: 'e2e-orders.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from('id,region,amount\n1,east,120.5\n2,west,80.0\n'),
  })
  await expect(page.getByRole('button', { name: '完成，开始数据处理' })).toBeEnabled()

  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/dataagent/api/v1/datasets'))
  await page.getByRole('button', { name: '完成，开始数据处理' }).click()
  const createResponse = await createResponsePromise
  const created = await createResponse.json() as { data?: { id?: string } }
  const datasetId = created.data?.id
  await expect(page.getByText('字段大纲')).toBeVisible()
  await expect(page.locator('.data-table')).toContainText('120.5')
  await expect(page.locator('.data-table')).toContainText('east')

  if (datasetId) {
    await request.delete(`/dataagent/api/v1/datasets/${datasetId}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace },
    })
  }
})

test('从洞察产品入口创建仪表盘并绑定脚本数据集', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })

  await page.goto('/?nav=insight')
  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/dataagent/api/v1/insight/dashboards'))
  await page.getByRole('button', { name: '新建仪表盘' }).click()
  const createResponse = await createResponsePromise
  const created = await createResponse.json() as { data?: { id?: string } }
  const dashboardId = created.data?.id
  expect(dashboardId).toBeTruthy()

  await page.getByRole('heading', { name: '未命名仪表盘' }).click()
  await page.locator('.toolbar-name-input').fill(`E2E UI Dashboard ${Date.now()}`)
  await page.locator('.toolbar-name-input').press('Enter')
  await page.locator('.palette-item').filter({ hasText: '数据表格' }).dragTo(page.locator('.dashboard-canvas'))
  await expect(page.locator('.dataset-input-panel')).toContainText('当前目标组件：')
  await page.locator('.dataset-input-panel').getByRole('button', { name: '添加', exact: true }).click()
  const datasetSelect = page.locator('.dataset-input-panel .dataset-input-row').last().locator('.el-select').first()
  await datasetSelect.click()
  await page.getByRole('option', { name: /E2E HTTP Orders Dataset/ }).click()
  await page.locator('.dataset-input-panel .alias-input input').last().fill('api_orders')
  await page.locator('.dataset-input-panel textarea').fill('rows = datasets.read(input_name="api_orders")\nresult = rows.to_polars().to_dicts()')
  await expect(page.locator('.dataset-input-panel')).toContainText('结果绑定组件')

  const updateResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'PUT' && response.url().includes(`/dataagent/api/v1/insight/dashboards/${dashboardId}`))
  await page.getByRole('button', { name: '保存' }).click()
  await updateResponsePromise
  await expect(page.locator('.dataset-input-panel .alias-input input').last()).toHaveValue('api_orders')

  if (dashboardId) {
    await request.delete(`/dataagent/api/v1/insight/dashboards/${dashboardId}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace },
    })
  }
})
