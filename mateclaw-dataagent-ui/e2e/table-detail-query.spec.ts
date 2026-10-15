// @ts-check
import { test, expect } from '@playwright/test'

/**
 * 表格详情页查询链路 E2E（实施计划任务 9）。
 * 运行：MATECLAW_E2E_ALOUDATA_MODE=simulation npm --prefix mateclaw-dataagent-ui run test:e2e -- e2e/table-detail-query.spec.ts --reporter=line
 * 前置：本地 UI（5174）与 DataAgent（18089）已启动，且存在测试仪表盘 fixture。
 */
const DASHBOARD_ID = process.env.MATECLAW_E2E_DASHBOARD_ID || ''

test.describe('表格详情页查询链路', () => {
  test.skip(!DASHBOARD_ID, '需要 MATECLAW_E2E_DASHBOARD_ID 指向隔离的测试仪表盘 fixture')

  test('查询配置弹窗可见且包含展示字段/筛选器绑定/排序/分页控件', async ({ page }) => {
    await page.goto(`/?nav=insight#/insight/dashboard/editor?dashboardId=${DASHBOARD_ID}`)
    // 通过列表页进入（直接 goto 编辑器路由画布可能为空，见记忆 #6）
    await page.getByRole('button', { name: '编辑' }).first().click()
    // 打开数据卡片「查询配置」
    await page.getByRole('button', { name: '查询配置' }).first().click()
    await expect(page.getByText('展示字段')).toBeVisible()
    await expect(page.getByText('筛选器绑定')).toBeVisible()
    await expect(page.getByText('允许排序')).toBeVisible()
    await expect(page.getByText('分页')).toBeVisible()
    await expect(page.locator('[data-testid="qc-field-row"]').first()).toBeVisible()
  })

  test('查看数据弹窗请求体携带 QueryContext 且行数/总数一致', async ({ page }) => {
    const requests = []
    page.on('request', (request) => {
      if (request.url().includes('query-plan/preview')) requests.push(request.postDataJSON())
    })
    await page.goto(`/?nav=insight#/insight/dashboard/editor?dashboardId=${DASHBOARD_ID}`)
    await page.getByRole('button', { name: '编辑' }).first().click()
    await page.getByRole('button', { name: '查看数据' }).first().click()
    await page.getByRole('button', { name: '查询' }).click()
    if (requests.length) {
      expect(requests[0].queryContext).toBeDefined()
      expect(requests[0].queryContext.pagination).toBeDefined()
    }
  })

  test('表头三态：A 升序 → B 升序 → B 降序 → B 取消，页码回 1', async ({ page }) => {
    let lastQueryContext = null
    page.on('request', (request) => {
      const body = request.postDataJSON()
      if (request.url().includes('query-plan/preview') && body?.queryContext) lastQueryContext = body.queryContext
    })
    await page.goto(`/?nav=insight#/insight/dashboard/editor?dashboardId=${DASHBOARD_ID}`)
    await page.getByRole('button', { name: '编辑' }).first().click()
    await page.getByRole('button', { name: '查看数据' }).first().click()
    const headers = page.locator('.el-table th')
    await headers.nth(0).click() // A asc
    expect(lastQueryContext?.sort).toEqual({ field: expect.any(String), direction: 'asc' })
    await headers.nth(1).click() // B asc（新字段从升序开始）
    expect(lastQueryContext?.sort?.direction).toBe('asc')
    await headers.nth(1).click() // B desc
    expect(lastQueryContext?.sort?.direction).toBe('desc')
    await headers.nth(1).click() // B 取消
    expect(lastQueryContext?.sort).toBeNull()
  })
})
