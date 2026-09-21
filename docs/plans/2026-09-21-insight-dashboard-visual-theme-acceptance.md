# 洞察仪表盘视觉主题实施验收记录

> 候选 SHA：`4d4bcccad82e30f7bc38b041f6f7b993a79301f1`
> 验收日期：2026-09-21
> 范围：`docs/plans/2026-09-21-insight-dashboard-visual-theme-implementation-plan.md` Task 6

## 1. 环境与数据安全

- 前端地址：`http://127.0.0.1:5174`
- 真实浏览器：Google Chrome `153.0.8010.50`
- CDP：`http://127.0.0.1:9222`，通过 `/json/version` 复核
- 后端验证：Java `21.0.12`、Maven `3.9.16`；未使用 Docker
- 数据：复用用户已有浏览器会话进入真实“策略解读”仪表盘；CDP 脚本只创建并关闭自己的 Page，不关闭用户 Tab，不保存 Cookie/JWT/密码，不点击保存
- 样本限制：当前“策略解读”中存在空数据组件，未出现可渲染 ECharts 实例；因此图表数据渲染和有数据组合卡片只由单测/代码路径覆盖，不能从本次截图推导运行时视觉通过

## 2. CDP 9222 视觉验收

执行命令：

```bash
pnpm test:e2e:cdp:theme
```

结果：`PASS（真实 Chrome CDP 连接与视觉采集）`

- 进入洞察列表并定位真实“策略解读”仪表盘：通过
- 打开“主题外观”面板：通过，AX 快照包含 10 个可访问 radio 预设
- 10 套预设：`blue`、`indigo`、`teal`、`amber`、`dark-data`、`rose`、`coral`、`orange`、`gold`、`burgundy` 均完成点击和截图
- 视口矩阵：`375`、`768`、`1024`、`1440` 均完成截图
- 截图数量：16 张
- 页面横向溢出：4 个视口均为 `false`
- 控制台 error：0
- 失败请求或非预期 HTTP 错误：0
- 证据目录：`/tmp/mateclaw-dashboard-theme-4d4bccca/`

截图命名包括：`01-insight-list.png`、`02-theme-panel-1440.png`、10 张 `03-preset-*.png` 和 4 张 `04-viewport-*.png`。

### 未纳入 PASS 的项

- 当前样本无可渲染图表数据，`chartPresent=false`；不能据此宣称 ECharts 系列色板在真实有数据图表中已视觉通过。
- 本次 CDP 脚本不保存用户仪表盘，未执行会改写用户数据的复制/保存链路；保存、正式预览与复制由定向单测、DTO round-trip 和 Playwright 回归脚本覆盖。

## 3. 自动化验证

### 前端

```bash
pnpm exec vitest run
```

结果：`58 files passed, 302 tests passed`。

```bash
pnpm build
```

结果：通过，包含 `vue-tsc --noEmit` 与 Vite 构建。

### 后端

使用 Java 21、Maven 3.9.16 执行主题 Schema 定向测试：

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=InsightDashboardSchemaDTOTest,InsightDashboardThemeServiceTest test
```

结果：`8 tests, 0 failures`。

### 独立 Playwright E2E

```bash
pnpm exec playwright test e2e/dashboard-theme.spec.ts
```

结果：`BLOCKED`。本机缺少 Playwright Chromium 可执行文件：
`/Users/srant/Library/Caches/ms-playwright/chromium_headless_shell-1243/...`。

没有执行自动下载，也没有把独立 Playwright 浏览器当作 CDP 9222 视觉证据。具备浏览器二进制后，应在隔离测试仪表盘上重新执行该命令，并补充保存、正式预览、刷新和复制的运行证据。

## 4. 结论

Task 1～5 已完成并分别提交；Task 6 的 CDP 真实浏览器视觉采集、全量前端回归、后端定向测试和验收记录已完成。独立 Playwright E2E 因本地浏览器二进制缺失保持 `BLOCKED`，图表/组合卡片真实有数据视觉项因当前样本为空保持限制，不将其写成 PASS。
