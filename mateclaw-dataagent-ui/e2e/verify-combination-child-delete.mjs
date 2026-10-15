import { chromium } from '@playwright/test'
import fs from 'node:fs'

const BASE = 'http://localhost:5174'
const OUT = '/tmp/insight-verify'
fs.mkdirSync(OUT, { recursive: true })

const log = (...a) => console.log('[verify]', ...a)
const results = []
function check(name, ok, extra = '') {
  results.push({ name, ok, extra })
  log(`${ok ? 'PASS' : 'FAIL'} - ${name}${extra ? ' | ' + extra : ''}`)
}

let browser
try {
  browser = await chromium.launch({ channel: 'chrome', headless: true })
} catch {
  browser = await chromium.launch({ headless: true })
}
const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } })
page.setDefaultTimeout(30_000)

/** 校验一个组合卡片可视子组件：头部可见 → 头内删除钮；头部不可见 → 悬浮删除钮 */
async function verifyChildrenDelete(comboRoot, label) {
  const children = comboRoot.locator('[data-child]')
  const n = await children.count()
  log(`${label}: ${n} 个可视子组件`)
  let withHead = 0, withFloat = 0, bad = 0
  for (let i = 0; i < n; i++) {
    const child = children.nth(i)
    const title = (await child.locator('.cc-child-title').first().textContent().catch(() => ''))?.trim() ?? ''
    const type = await child.getAttribute('data-child')
    const headCount = await child.locator('.cc-child-head').count()
    const floatCount = await child.locator('.cc-child-del--float').count()
    const headDel = await child.locator('.cc-child-head .cc-child-del').count()
    const ok = (headCount > 0 && headDel > 0 && floatCount === 0) || (headCount === 0 && floatCount > 0)
    if (headCount > 0) withHead++
    if (floatCount > 0) withFloat++
    if (!ok) bad++
    log(`  - [${type}] "${title}" head=${headCount} headDel=${headDel} float=${floatCount} ${ok ? 'OK' : '<<< 缺删除按钮'}`)
  }
  check(`${label}: 所有可视子组件均有删除入口`, bad === 0 && n > 0, `head 型=${withHead} 悬浮型=${withFloat} 异常=${bad}`)
}

try {
  // 1. login (local account)
  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.getByRole('button', { name: '本地账号登录' }).click()
  const inputs = page.locator('input:visible')
  await inputs.nth(0).fill('admin')
  await inputs.nth(1).fill('admin23')
  await page.getByRole('button', { name: '登 录', exact: true }).click()
  await page.waitForURL((u) => !String(u).includes('/login'), { timeout: 20_000 })
  log('logged in')

  // 2. insight dashboard list：标题精确为「策略解读」的卡片
  await page.goto(`${BASE}/?nav=insight`, { waitUntil: 'load' })
  await page.waitForTimeout(2000)
  const cards = page.locator('[class*="dashboard-card"]')
  const cardCount = await cards.count()
  log(`列表卡片数: ${cardCount}`)
  let target = null
  for (let i = 0; i < cardCount; i++) {
    const t = (await cards.nth(i).innerText().catch(() => '')).trim()
    log(`  card[${i}]: ${t.replace(/\n/g, ' / ').slice(0, 60)}`)
  }
  // 取标题行恰为「策略解读」的卡片（排除 副本/ba se）
  for (let i = 0; i < cardCount; i++) {
    const headings = cards.nth(i).locator('h3, h4, .title, [class*="title"]')
    const hn = await headings.count()
    for (let j = 0; j < hn; j++) {
      const txt = ((await headings.nth(j).textContent().catch(() => '')) ?? '').trim()
      if (txt === '策略解读') { target = cards.nth(i); break }
    }
    if (target) break
  }
  if (!target) target = page.locator('[class*="dashboard-card"]', { hasText: '策略解读' }).first()
  await target.getByRole('button', { name: /编辑/ }).first().click()
  log('clicked 编辑')

  // 3. 等待编辑器画布渲染
  await page.waitForSelector('[data-component-id]', { timeout: 30_000 })
  await page.waitForTimeout(2000)
  await page.screenshot({ path: `${OUT}/editor-opened.png` })

  // 4. 顶层组合卡片「策略解读」：canvas 删除按钮（title-bar-hidden 悬浮）
  const topItem = page.locator('.grid-item-content').first()
  const topDelete = topItem.locator('.grid-item-delete')
  check('顶层组合卡片有画布删除按钮', await topDelete.count() > 0,
    `toolbar class=${await topItem.locator('.grid-item-toolbar').first().getAttribute('class')}`)

  // 5. 顶层组合的「策略视角」页签
  const comboRoot = page.locator('.combination-card').first()
  await comboRoot.locator('.cc-tab', { hasText: '策略视角' }).first().click()
  await page.waitForTimeout(1500)
  await verifyChildrenDelete(comboRoot, '「策略解读」策略视角')

  // 6. 嵌套组合卡片（data-child 内的 .combination-card）：切「计划贡献表」页签后校验其子组件
  const nestedCombo = comboRoot.locator('[data-child]').filter({ has: page.locator('.combination-card') }).first()
  await nestedCombo.locator('.combination-card').first().locator('.cc-tab', { hasText: '计划贡献表' }).first().click()
  await page.waitForTimeout(1000)
  await verifyChildrenDelete(nestedCombo.locator('.combination-card').first(), '嵌套组合「计划贡献表」')

  await page.screenshot({ path: `${OUT}/strategy-tab.png` })
  log('screenshot saved')
} catch (e) {
  check('流程异常', false, String(e).slice(0, 300))
  try { await page.screenshot({ path: `${OUT}/error.png` }) } catch {}
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n===== 结果: ${results.length - failed.length}/${results.length} 通过 =====`)
for (const r of failed) console.log('FAIL:', r.name, r.extra)
process.exit(failed.length ? 1 : 0)
