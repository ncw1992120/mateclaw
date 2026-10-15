# 洞察仪表盘编辑器 UX 收敛 — 验收记录

> 依据：`docs/plans/2026-09-21-insight-dashboard-editor-ux-convergence-implementation-plan.md`
> 记录时间：2026-09-21 12:46（Asia/Shanghai）

## 候选与环境

| 项 | 值 |
|---|---|
| 候选 SHA | `6f2e7ea1`（feature/dev_fu；前置会话修复 `7d822d38`） |
| 分支 | feature/dev_fu（仅 1 个 worktree） |
| 视觉验收浏览器 | Google Chrome 153 via CDP `http://127.0.0.1:9222`（用户真实 Chrome，非脚本自启） |
| UI | dev server `http://127.0.0.1:5174`（HMR 生效） |
| DataAgent | `http://127.0.0.1:18089/dataagent/api/actuator/health` → `{"status":"UP"}` |
| 数据 | 本地模拟 DataAgent/JDBC/Aloudata 数据与真实开发库兼容路径 |
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
| Task 8 路由收敛 | PASS | `26034856` + `761c6ade`；router 单测 3/3、build 独立 chunk 148.99 kB；列表页不再保留编辑模式 |
| Task 9 E2E 门禁 | **PASS** | 本机 UI/DataAgent + 本地 seed + Chrome CDP 9222；4/4 通过 |
| Task 10 CDP 视觉验收 | **PASS** | 14/14 场景 + summary.json，见下 |
| Task 11 聚合门禁 | **PASS（本机范围）** | 前端 `vue-tsc` 0 错、vitest 49 文件 / 271 用例、build 通过；后端契约 13/13；E2E 4/4；CDP 14/14 |

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
| 06 同弹窗筛选预览 | PASS | 「筛选预览」工作台内查询，结果区「1 行 · 1.2s」，无叠加 Dialog |
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

## Task 9：本机真实 E2E 门禁（PASS）

执行环境：UI `http://127.0.0.1:5174`、DataAgent `http://127.0.0.1:18089`（health=`UP`）、Google Chrome CDP `9222`；使用本机 JDK 21/Maven 构建的 DataAgent 和本地 seed Dashboard，不启动 Docker。

执行命令：

```bash
MATECLAW_E2E_TOKEN="$TOKEN" \
MATECLAW_E2E_WORKSPACE_ID=1 \
MATECLAW_E2E_UX_DASHBOARD_ID="$DASHBOARD_ID" \
MATECLAW_E2E_BROWSER_CHANNEL=chrome \
MATECLAW_E2E_ALOUDATA_MODE=simulation \
npm run test:e2e -- e2e/dashboard-editor-ux.spec.ts --reporter=line
```

结果：**4 passed**。

覆盖内容：

1. KPI 卡片选择 → 添加数据集 → Aloudata/JDBC/接口/文件来源树 → JDBC SQL →「筛选预览」→ 保存 → 刷新后重新选中卡片，数据集仍可见。
2. 375/768/1024/1280/1440 五种视口无横向溢出。
3. 方向键移动画布卡片、Info 帮助键盘触发、Esc 关闭「添加数据集」弹窗。
4. 可见交互控件均有可访问名称。

动态 JWT、workspace 与 Dashboard ID 只写入 `/tmp/mateclaw-dashboard-e2e-state.json`，未进入证据或 Git。

## 已知限制（如实记录，不计为本机门禁失败）

1. `make dashboard-dataagent-test` 原定义在 Docker 容器内运行；本轮按约束不启动 Docker，改为本地 JDK 21 + Maven 3.9.13 直跑等价命令（`mvn -Dtest='InsightDashboardSchemaDTOTest,DatasetComposerControllerTest,DashboardExecutionServiceTest' test`，13/13 通过）。Testcontainers 相关全量后端测试未执行。
2. `make dashboard-verify-local` 依赖 compose 模拟栈，本轮不启动 Docker，未执行；不影响本机 UI/DataAgent 垂直链路结论。
3. Task 9 已用本地 seed 状态执行 Chrome channel E2E 4/4；动态 JWT 与 Dashboard ID 仅存在 `/tmp/mateclaw-dashboard-e2e-state.json`，未归档到 Git。
4. 真实 Aloudata 生产联调、四主题全量对比度自动扫描未执行（CDP 覆盖 dark 主题截图）。

## 最终结论

**PASS（本机验证范围，含上述已知限制）** —— 11 个任务均已完成；前端、后端契约、本地真实 E2E 与 Chrome CDP 视觉证据均通过。Docker-only 的 Testcontainers 全量套件与 compose 验证按用户约束未执行，未被伪装为通过。
