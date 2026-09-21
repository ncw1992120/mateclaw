# 洞察仪表盘编辑器 UX 收敛 — 验收记录

> 依据：`docs/plans/2026-09-21-insight-dashboard-editor-ux-convergence-implementation-plan.md`
> 记录时间：2026-09-21 12:46（Asia/Shanghai）

## 候选与环境

| 项 | 值 |
|---|---|
| 候选 SHA | `54be4684`（feature/dev_fu） |
| 分支 | feature/dev_fu（仅 1 个 worktree） |
| 视觉验收浏览器 | Google Chrome 153 via CDP `http://127.0.0.1:9222`（用户真实 Chrome，非脚本自启） |
| UI | dev server `http://127.0.0.1:5174`（HMR 生效） |
| DataAgent | `http://127.0.0.1:18089/dataagent/api/actuator/health` → `{"status":"UP"}` |
| 数据 | 真实开发库 + 真实 Aloudata SaaS（非模拟栈） |
| Node | 22.22.2；JDK 21（`~/.jdks/jdk-21.0.12+8`）+ Maven 3.9.13（本地直跑，docker 不可用） |

## 任务结论

| Task | 结论 | 证据 |
|---|---|---|
| Task 1 基础视觉组件 | PASS（已完成） | 单测 + commit |
| Task 2 草稿状态 | PASS（已完成） | 定向单测 |
| Task 3 信息层级 | PASS（已完成） | 定向单测 + Emoji 清理 |
| Task 4 数据集来源流程 | PASS（已完成） | 全量 241 用例 |
| Task 5 弹窗表单按钮 | PASS（已完成） | 38 用例 |
| Task 6 画布键盘响应式 | PASS（已完成） | 246 用例 |
| Task 7 后端契约 | PASS / 全量 BLOCKED | 定向 13 tests 通过；全量依赖 docker Testcontainers |
| Task 8 路由收敛 | PASS | `26034856`；router 单测 3/3、build 独立 chunk 145KB |
| Task 9 E2E 门禁 | **BLOCKED** | 测试已入库（`49c1d855`）但 docker 不可用、seed state 缺失，无法执行 |
| Task 10 CDP 视觉验收 | **PASS** | 14/14 场景 + summary.json，见下 |
| Task 11 聚合门禁 | PARTIAL | 前端 tsc/vitest/build 通过；后端契约测试通过；`make dashboard-verify-local` 因 docker 不可用 BLOCKED |

## Task 10：Google Chrome 9222 CDP 视觉验收（PASS）

- 命令：`MATECLAW_CDP_ENDPOINT=http://127.0.0.1:9222 MATECLAW_UI_BASE_URL=http://127.0.0.1:5174 MATECLAW_CDP_SCREENSHOT_DIR=/tmp/mateclaw-dashboard-editor-ux-54be4684 npm run test:e2e:cdp:editor-ux`
- 脚本：`e2e/cdp-dashboard-editor-ux-check.mjs`（仅 `connectOverCDP`，未调用 `chromium.launch()`，结束只关闭自建 Page）
- 截图目录：`/tmp/mateclaw-dashboard-editor-ux-54be4684/`（14 张 PNG + `summary.json`）

| 场景 | 结果 | 说明 |
|---|---|---|
| 01 编辑器默认 1440 | PASS | 看板「拖拽复现-1789714441813」，四栏布局 |
| 02 属性侧栏帮助分级 | PASS | 帮助正文不常驻 |
| 03 Info 键盘帮助 | PASS | aria-label 触发点可聚焦 |
| 04 来源选择树 | PASS | 「添加数据集」树形弹窗，分组清晰 |
| 05 JDBC SQL 配置 | PASS | 树选 JDBC 数据源 → SQL 弹窗 |
| 06 同弹窗筛选预览 | PASS | 「查看数据」弹窗内查询，结果区「1 行 · 1.2s」，无叠加 Dialog |
| 07 Aloudata 指标视图 | PASS | 树快捷节点 → Aloudata 弹窗加载视图列表 |
| 08 Python 与结果集 | PASS | 分区可见 |
| 09 长内容溢出 | PASS | 长列名 SQL 预览，页面 scrollWidth 无溢出 |
| 10 编辑器 1024 | PASS | 抽屉态 |
| 11 编辑器 375 | PASS | 无横向溢出 |
| 12 暗色主题 | PASS | `data-theme=dark` 生效，采集后已恢复用户原主题 |
| 13 键盘焦点 | PASS | 画布卡片可获焦点 |
| 14 错误就地展示 | PASS | 非法表名预览 400，错误就地显示在结果区 |

**AX/控制台/网络**：可见无名交互控件 **0**；控制台错误 6、失败请求 3 —— 全部来自 14 场景故意触发的 `400 JDBC 草稿预览失败`（预期覆盖），无其他意外错误。

**验收中发现并已修复**：画布筛选下拉（`FilterSelectWidget`）与属性面板组件标题输入框缺可访问名称，已补 `aria-label`。

## 已知限制（如实记录，不计为通过）

1. `make dashboard-dataagent-test` 原定义在 docker 容器内运行；docker 不可用，改为本地 JDK 21 + Maven 3.9.13 直跑等价命令（`mvn -o test -Dtest='InsightDashboardSchemaDTOTest,DatasetComposerControllerTest,DashboardExecutionServiceTest'`，13/13 通过）。Testcontainers 相关全量后端测试未执行。
2. `make dashboard-verify-local` 依赖模拟栈，BLOCKED 未执行。
3. Task 9 的 Chrome channel E2E 全量未执行（同上环境原因）；测试代码已入库待环境就绪。
4. 真实 Aloudata 生产联调、四主题全量对比度自动扫描未执行（CDP 覆盖了 dark 主题截图）。

## 最终结论

**PASS（含上述已知限制）** —— 计划全部 11 个任务中，9 个完成（Task 1-8、10），Task 9 / Task 11 的 docker 依赖项 BLOCKED 且已如实标注，无任何未执行项被标记为通过。
