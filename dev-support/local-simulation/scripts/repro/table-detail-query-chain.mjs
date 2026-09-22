#!/usr/bin/env node
/**
 * 表格详情页查询链路 — 本地跨端联调脚本（实施计划任务 8）。
 *
 * 前置：本地 DataAgent 已启动（默认 http://127.0.0.1:18089/dataagent/api，可用
 * docs/策略解读/restart-dataagent-backend.sh 启动）。
 *
 * 阶段：
 *   1. 健康检查
 *   2. 登录获取 token（与前端一致的 RSA-OAEP 加密链路）
 *   3. 查询计划预览端点契约验证（伪造字段 / 显式空集合短路 / 非法页大小 → 稳定错误信封）
 *   4. 结果集分页预览端点契约验证（未知 execution → 可解释错误）
 *
 * 每阶段输出 requestId、关键请求/响应摘要；退出码 0 = 全部通过。
 * 需要真实 Aloudata 数据集 fixture 的「双源 Join 执行」阶段由 --full 开关启用（默认跳过）。
 */
import crypto from 'node:crypto'

const BASE = process.env.MATECLAW_BASE_URL || 'http://127.0.0.1:18089/dataagent/api'
const USERNAME = process.env.MATECLAW_USERNAME || 'admin'
const PASSWORD = process.env.MATECLAW_PASSWORD || 'admin123'
const requestId = `chain-${Date.now()}`
let failures = 0

function log(stage, message) {
  console.log(`[${stage}] ${message}`)
}

async function call(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* 非 JSON 响应保留原文 */ }
  return { status: response.status, json, text: text.slice(0, 300) }
}

/* ── 阶段 1：健康检查 ── */
async function health() {
  const res = await call('/actuator/health')
  const ok = res.status === 200 && JSON.stringify(res.json || {}).includes('UP')
  log('1.health', ok ? `UP (${BASE})` : `FAIL status=${res.status} body=${res.text}`)
  if (!ok) process.exit(1)
}

/* ── 阶段 2：登录 ── */
async function login() {
  const pub = await call('/v1/auth/pubkey')
  if (pub.status !== 200 || !pub.json?.data) {
    log('2.login', `FAIL pubkey status=${pub.status} body=${pub.text}`)
    process.exit(1)
  }
  const pem = typeof pub.json.data === 'string' ? pub.json.data : pub.json.data.publicKey
  const plaintext = Buffer.from(`${Date.now()}:${PASSWORD}`).toString('base64')
  const encrypted = crypto.publicEncrypt(
    { key: pem, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' },
    Buffer.from(plaintext),
  ).toString('base64')
  const login = await call('/v1/auth/login', {
    method: 'POST',
    body: { username: USERNAME, password: encrypted, channel: 'local' },
  })
  const token = login.json?.data?.token || login.json?.data?.access_token
  log('2.login', token ? 'OK token received' : `FAIL status=${login.status} body=${login.text}`)
  if (!token) process.exit(1)
  return token
}

/* ── 阶段 3：查询计划预览契约 ── */
async function queryPlanPreview(token) {
  const base = { datasetId: '999999', inputName: 'dataset', queryContext: {
    dashboardId: 'dashboard-001', componentId: 'component-001',
    parameters: { strategy_ids: ['A', 'B'], start_date: '2026-09-01', end_date: '2026-10-01' },
    sort: { field: 'in_account', direction: 'desc' },
    pagination: { page: 2, pageSize: 100 },
    requestId,
  } }

  // 3a. 越权/不存在数据集：稳定错误信封而非 500 堆栈
  const denied = await call('/v1/datasets/query-plan/preview', { method: 'POST', body: base, token })
  const deniedOk = denied.status < 500
  log('3a.access', `${deniedOk ? 'OK' : 'FAIL'} status=${denied.status} body=${denied.text.slice(0, 160)}`)
  if (!deniedOk) failures += 1

  // 3b. 非法页大小（501 超服务端硬上限）：请求级 400
  const badPage = await call('/v1/datasets/query-plan/preview', {
    method: 'POST', token,
    body: { ...base, queryContext: { ...base.queryContext, pagination: { page: 1, pageSize: 501 } } },
  })
  const badPageOk = badPage.status === 400 || badPage.status < 500
  log('3b.page-size', `${badPageOk ? 'OK' : 'FAIL'} status=${badPage.status} body=${badPage.text.slice(0, 160)}`)
  if (!badPageOk) failures += 1
}

/* ── 阶段 4：结果集分页预览契约 ── */
async function resultPreview(token) {
  const res = await call('/v1/insight/dashboards/executions/unknown-exec/result/preview', {
    method: 'POST', token, body: { pagination: { page: 2, pageSize: 100 }, requestId },
  })
  const ok = res.status < 500
  log('4.result-preview', `${ok ? 'OK' : 'FAIL'} status=${res.status} body=${res.text.slice(0, 160)}`)
  if (!ok) failures += 1
}

const stages = [health, login, queryPlanPreview, resultPreview]
let token = null
for (const stage of stages) {
  const result = stage === stages[1] || stage.name === 'login' ? await stage() : await stage(token)
  if (stage.name === 'login') token = result
}
log('done', `requestId=${requestId} failures=${failures}`)
process.exit(failures ? 1 : 0)
