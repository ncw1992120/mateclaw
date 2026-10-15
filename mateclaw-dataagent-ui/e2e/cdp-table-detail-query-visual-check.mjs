#!/usr/bin/env node
/**
 * 表格详情页查询链路 — Chrome CDP 9222 视觉验收脚本（实施计划任务 9 步骤 7）。
 *
 * 独立运行：node mateclaw-dataagent-ui/e2e/cdp-table-detail-query-visual-check.mjs
 * 前置：用户 Chrome 以 --remote-debugging-port=9222 启动；本地 UI（5174）与 DataAgent（18089）可用。
 *
 * 与 Playwright 基线分开报告：本脚本接管用户真实浏览器逐步执行并截图，
 * 输出 URL、组件标题、弹窗状态、网络请求摘要和截图路径；连接失败时记 BLOCKED 退出（码 2）。
 */
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const CDP_URL = process.env.MATECLAW_CDP_URL || 'http://127.0.0.1:9222'
const UI_BASE = process.env.MATECLAW_UI_BASE || 'http://127.0.0.1:5174'
const OUT_DIR = process.env.MATECLAW_CDP_OUT || 'e2e/__cdp-table-detail-out'

const networkLog = []
function summarize(url) {
  if (url.includes('query-plan/preview')) return 'query-plan/preview'
  if (url.includes('/executions')) return 'executions'
  if (url.includes('result/preview')) return 'result/preview'
  if (url.includes('/datasets/')) return 'datasets'
  return null
}

async function main() {
  let browser
  try {
    browser = await chromium.connectOverCDP(CDP_URL)
  } catch (error) {
    console.log('BLOCKED: 无法连接 Chrome CDP 9222 —', error.message)
    console.log('提示：请以 --remote-debugging-port=9222 启动 Chrome 后重试')
    process.exit(2)
  }
  try {
    fs.mkdirSync(OUT_DIR, { recursive: true })
    const context = browser.contexts()[0] ?? await browser.newContext()
    const page = await context.newPage()
    page.on('request', (request) => {
      const tag = summarize(request.url())
      if (tag) networkLog.push(`${tag} ${request.method()} ${request.url().slice(0, 120)}`)
    })

    const shots = []
    async function shot(name) {
      const path = `${OUT_DIR}/${name}.png`
      await page.screenshot({ path })
      shots.push(path)
    }

    await page.goto(`${UI_BASE}/?nav=insight`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    console.log('URL:', page.url())
    await shot('01-insight-list')

    const title = await page.title()
    console.log('组件标题:', title)

    // 尝试进入编辑器（需要已存在测试仪表盘；找不到时记录状态退出）
    const editButton = page.getByRole('button', { name: '编辑' }).first()
    if (await editButton.isVisible({ timeout: 3000 }).catch(() => false)) {
      await editButton.click()
      await page.waitForTimeout(2000)
      await shot('02-editor')

      const queryConfig = page.getByRole('button', { name: '查询配置' }).first()
      if (await queryConfig.isVisible({ timeout: 3000 }).catch(() => false)) {
        await queryConfig.click()
        await page.waitForTimeout(800)
        await shot('03-query-config-dialog')
        console.log('查询配置弹窗: 已打开')
      }
      const viewData = page.getByRole('button', { name: '查看数据' }).first()
      if (await viewData.isVisible({ timeout: 3000 }).catch(() => false)) {
        await viewData.click()
        await page.waitForTimeout(800)
        await shot('04-view-data-dialog')
        console.log('查看数据弹窗: 已打开')
      }
    } else {
      console.log('未找到可编辑仪表盘（需要任务 8 的隔离测试仪表盘 fixture）')
    }

    console.log('网络请求摘要:')
    networkLog.slice(0, 20).forEach((line) => console.log('  ', line))
    console.log('截图路径:')
    shots.forEach((path) => console.log('  ', path))
    console.log('DONE')
  } finally {
    // 绝不 browser.close()：那是用户的浏览器
    process.exit(0)
  }
}

main()
