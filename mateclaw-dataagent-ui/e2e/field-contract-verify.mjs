/**
 * 字段名与展示名契约 · 真机端到端验证（免登录 editor-preview + mock 后端）
 * =====================================================================
 * 契约：docs/策略解读/字段名与展示名契约-实施计划.md（字段名=不可变技术主键，展示名=可改的表现层标签，
 * 二者收敛到唯一字段注册表 DatasetConfig.fields；所有引用只存字段名）。
 *
 * 覆盖的验收项：
 *   T9  老配置（fieldMappings.target=展示名、filters 存展示名、kpiMetrics.fieldKey=展示名）读入惰性归一，
 *       且 kpiMetrics 现值权威（fieldKey→字段名、展示名/单位迁入注册表）；
 *   T3  字段名称弹窗按注册表铺行、字段名/描述只读、展示名唯一性重复即标红并禁用保存；
 *   T4  指标配置与字段名称弹窗读写同一份注册表（改一处、另一处立即同值）；
 *   T8  保存回写 pipeline：fieldMappings.source=字段名 / target=展示名、filters[].field 与绑定筛选器均为字段名。
 *
 * 用法：
 *   1) 启 dev server（任选端口）：node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5188 --strictPort
 *   2) BASE=http://127.0.0.1:5188 node e2e/field-contract-verify.mjs
 *
 * 注意（踩过的坑）：
 *   - 必须放在本目录下跑，否则解析不到 playwright 包；
 *   - mock 兜底必须返回数组（数据源树/数据集列表按数组消费，返回 {} 会造假报错）；
 *   - `.mc-row` / `.fm-row` 里的 `input` 首个是 el-switch 的隐藏 checkbox；
 *     断言文本框请用 `input.el-input__inner`（并注意 readonly 属性值为空字符串，是 falsy）。
 */

import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://127.0.0.1:5188'
const CHROME =
  '/Users/srant/Library/Caches/ms-playwright/chromium-1140/chrome-mac/Chromium.app/Contents/MacOS/Chromium'

const styles = () => ({
  name: { size: 14, family: 'system', color: '#646a73', bold: 'normal' },
  value: { size: 28, family: 'system', color: '#1f2329', bold: 'bold' },
  unit: { size: 15, family: 'system', color: '#646a73', bold: 'normal' },
  helper: { size: 12, family: 'system', color: '#8f959e', bold: 'normal' },
})

/** 老契约仪表盘：展示名散落存储在 fieldMappings.target / filters / kpiMetrics 里 */
function legacySchemaJson() {
  return JSON.stringify({
    version: '1.1',
    pages: [
      {
        id: 'page_0',
        name: '卡片配置',
        order: 0,
        components: [
          {
            id: 'card-kpi-1',
            type: 'kpi',
            title: '策略下发概览',
            position: { x: 0, y: 0, w: 12, h: 8 },
            multiKpi: true,
            kpiMetrics: [
              {
                fieldKey: '策略 ID', // 旧契约：fieldKey 存的是展示名
                displayName: '策略编号', // 用户最后改过的名字 → 迁移时权威
                unit: '个',
                helperText: '较上期',
                visible: true,
                x: 0,
                y: 0,
                w: 284,
                h: 88,
                styles: styles(),
              },
            ],
            config: {
              datasetPipeline: {
                datasetInputs: [
                  {
                    datasetId: '9',
                    inputName: 'orders',
                    displayName: '订单',
                    sourceType: 'JDBC_SQL',
                    sourceConfig: { datasourceId: '1', sql: 'select 1' },
                    fieldMappings: [
                      { source: 'strategy_id', target: '策略 ID' },
                      { source: 'delivery_count', target: '下发次数' },
                    ],
                    filters: [{ field: '策略 ID', op: '=', value: 'S1' }],
                  },
                ],
                scriptFilterBindings: [
                  { filterComponentId: 'filter-0', inputNames: ['orders'], fieldMappings: { orders: '策略 ID' } },
                ],
                script: '',
                parameters: [],
                executionPolicy: {},
              },
            },
          },
          {
            id: 'filter-0',
            type: 'filter',
            title: '策略类型',
            position: { x: 0, y: 0, w: 6, h: 2 },
          },
        ],
      },
    ],
    datasetInputs: [],
    scriptFilterBindings: [],
    parameters: [],
    executionPolicy: {},
  })
}

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ' | ' + detail : ''}`)
}

async function main() {
  const browser = await chromium.launch({ executablePath: CHROME })
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
  const page = await context.newPage()

  const consoleErrors = []
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text())
  })
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message + '\nSTACK: ' + (e.stack || '(no stack)')))

  /** 运行期保存的 pipeline（用于断言保存写回） */
  let savedPipeline = null

  await page.route('**/dataagent/api/**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()
    const json = (data) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok', data }) })

    if (/\/v1\/insight\/dashboards\/[^/?]+$/.test(url) && method === 'GET') {
      return json({ id: '77', name: '契约验证盘', description: '', ownerName: 'admin', ownerId: 'null', schemaJson: legacySchemaJson() })
    }
    if (/\/v1\/insight\/dashboards/.test(url) && method === 'PUT') {
      try {
        const body = JSON.parse(route.request().postData() || '{}')
        const schema = typeof body.schemaJson === 'string' ? JSON.parse(body.schemaJson) : body.schemaJson
        const comp = schema?.pages?.[0]?.components?.find((c) => c.type !== 'filter')
        savedPipeline = comp?.config?.datasetPipeline ?? null
      } catch (e) {
        savedPipeline = { parseError: String(e) }
      }
      return json({ id: '77' })
    }
    if (/\/v1\/insight\/dashboards\/?$/.test(url) && method === 'GET') return json([])
    // 兜底：列表类接口期望数组（数据源树 / 数据集列表），返回对象会造假报错
    return json([])
  })

  await page.goto(`${BASE}/insight/dashboard/editor-preview?dashboardId=77`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3500)

  // ---- 选中画布上的 KPI 卡片，唤出属性面板 ----
  const widget = page.locator('.vgl-item', { hasText: '策略下发概览' }).first()
  const hasWidget = (await widget.count()) > 0
  check('画布渲染出 KPI 组件', hasWidget)
  if (!hasWidget) {
    await page.screenshot({ path: '/tmp/field-contract-verify/fail-canvas.png' })
    console.log(await page.content().then((h) => h.slice(0, 1200)))
    await browser.close()
    return
  }
  // 点卡片左上角空白处（避开卡片内部按钮/热区）
  await widget.click({ position: { x: 30, y: 12 } })
  await page.waitForTimeout(1400)
  const panel = page.locator('.attr-panel')
  check('属性面板出现', (await panel.count()) > 0)
  if ((await panel.count()) === 0) {
    await page.screenshot({ path: '/tmp/field-contract-verify/fail-panel.png' })
    await browser.close()
    return
  }

  // ---- 归一告警不应误报（老引用都能反解） ----
  const warnText = await panel.innerText()
  check('未识别引用告警未误报', !warnText.includes('无法识别'), warnText.slice(0, 200).replace(/\n/g, ' / '))

  // ---- T4：指标配置展示迁移后的注册表值 ----
  await panel.getByRole('button', { name: '配置指标' }).click()
  await page.waitForTimeout(800)
  const metricRow = page.locator('.el-dialog:visible .mc-row').first()
  check('指标配置弹窗打开', (await metricRow.count()) > 0)
  let metricName = ''
  if ((await metricRow.count()) > 0) {
    // 只取可见文本框（el-switch 的隐藏 checkbox 也是 input，必须排除）
    const mi = metricRow.locator('input.el-input__inner')
    metricName = await mi.nth(0).inputValue()
    const metricField = await mi.nth(1).inputValue()
    const metricUnit = await mi.nth(2).inputValue()
    check('T9 fieldKey 归一为字段名', metricField === 'strategy_id', `指标值列=${metricField}`)
    check('T9 展示名迁移以 kpiMetrics 现值为权威', metricName === '策略编号', `展示列名=${metricName}`)
    check('T9 单位随迁移保留', metricUnit === '个', `单位=${metricUnit}`)
  }

  // ---- T4 → T3 双入口一致性：在指标配置改展示名 ----
  if ((await metricRow.count()) > 0) {
    await metricRow.locator('input.el-input__inner').nth(0).fill('策略编号（改）')
    await page.waitForTimeout(600)
  }
  await page.locator('.el-dialog:visible').getByRole('button', { name: '关闭', exact: true }).click()
  await page.waitForTimeout(600)

  // ---- T3：字段名称弹窗读同一份注册表 ----
  await panel.getByRole('button', { name: '字段名称' }).first().click()
  await page.waitForTimeout(1200)
  const fmRows = page.locator('.el-dialog:visible .fm-row')
  const fmCount = await fmRows.count()
  check('字段名称弹窗按注册表铺出 2 行', fmCount === 2, `行数=${fmCount}`)
  if (fmCount >= 1) {
    const first = fmRows.first().locator('input.el-input__inner')
    const [fName, fDesc, fDisplay] = [await first.nth(0).inputValue(), await first.nth(1).inputValue(), await first.nth(2).inputValue()]
    check('字段名只读列为字段名', fName === 'strategy_id', fName)
    check('T4→T3 双入口一致（指标配置改名后字段名称同值）', fDisplay === '策略编号（改）', `展示名=${fDisplay}`)
    const ro1 = await first.nth(0).getAttribute('readonly')
    const ro2 = await first.nth(1).getAttribute('readonly')
    check('字段名 / 字段描述列只读', ro1 !== null && ro2 !== null, `readonly=${ro1},${ro2}`)
    // 后端未下发描述时（本用例 pipeline 里没有 description）描述列回退展示名，与既有口径一致
    check('字段描述列无后端描述时回退展示名', fDesc === '策略编号（改）', fDesc)
  }

  // ---- 决策 1：展示名唯一性拦截 ----
  if (fmCount === 2) {
    const rows = page.locator('.el-dialog:visible .fm-row')
    await rows.nth(1).locator('input.el-input__inner').nth(2).fill('策略编号（改）')
    await page.waitForTimeout(600)
    const invalidCount = await page.locator('.el-dialog:visible .fm-invalid').count()
    const saveDisabled = await page
      .locator('.el-dialog:visible')
      .getByRole('button', { name: '确定', exact: true })
      .isDisabled()
    check('展示名重复 → 行标红 + 保存禁用', invalidCount >= 2 && saveDisabled, `红行=${invalidCount} 禁用=${saveDisabled}`)
    // 改回合法值再保存
    await rows.nth(1).locator('input.el-input__inner').nth(2).fill('下发次数')
    await page.waitForTimeout(600)
  }

  // ---- T8：保存后回写 pipeline（source=字段名 / target=展示名，filters 为字段名）----
  await page.locator('.el-dialog:visible').getByRole('button', { name: '确定', exact: true }).click()
  await page.waitForTimeout(1500)
  // 触发一次保存
  const saveBtn = page.locator('.editor-toolbar').getByRole('button', { name: /保存/ }).first()
  if ((await saveBtn.count()) > 0) {
    await saveBtn.click()
    await page.waitForTimeout(1500)
  }
  check('保存请求带回 datasetPipeline', !!savedPipeline, savedPipeline ? '' : '（未捕获到 PUT）')
  if (savedPipeline) {
    const input = (savedPipeline.datasetInputs || [])[0] || {}
    const fm = input.fieldMappings || []
    check(
      'T8 fieldMappings.source=字段名 / target=展示名',
      fm[0]?.source === 'strategy_id' && fm[0]?.target === '策略编号（改）',
      JSON.stringify(fm),
    )
    const flt = (input.filters || [])[0] || {}
    check('T9 filters[].field 已物理归一为字段名', flt.field === 'strategy_id', JSON.stringify(input.filters))
    const binding = (savedPipeline.scriptFilterBindings || [])[0] || {}
    check('T9 绑定筛选器存字段名', binding.fieldMappings?.orders === 'strategy_id', JSON.stringify(binding.fieldMappings))
  }

  if (consoleErrors.length) console.log('\n=== 控制台错误明细 ===\n' + consoleErrors.join('\n---\n'))
  check('无控制台错误', consoleErrors.length === 0, consoleErrors[0]?.split('\n')[0] || '')

  await page.screenshot({ path: '/tmp/field-contract-verify/final.png', fullPage: false })
  await browser.close()

  const failed = results.filter((r) => !r.ok)
  console.log(`\n== ${results.length - failed.length}/${results.length} 通过 ==`)
  process.exit(failed.length ? 1 : 0)
}

main().catch((e) => {
  console.error('SCRIPT ERROR', e)
  process.exit(2)
})
