# 表格详情页查询链路 — 发布前验证报告（任务 10）

- 日期：2026-09-22
- 分支：`feature/dev_fu`
- 范围：实施计划《2026-09-22-表格详情页查询链路实施计划.md》任务 1–10

## 逐项验证结果

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 后端依赖安装（父 POM / mateclaw-sdk，JDK 21） | PASS | 离线仓已有父 POM 与 SDK；全部编译/测试在 JDK 21（~/.jdks/jdk-21.0.12+8）下执行 |
| DataAgent 全量测试 `mvn -o -f mateclaw-dataagent/pom.xml test` | PASS（275 用例：0 失败；3 个 Docker 环境错误见下） | QueryPlannerTest 14、QueryPushdownContractTest 8、ScriptDatasetInputControllerTest 6、ScriptTaskPreparationServiceTest 3、ResultSetQueryServiceTest 5、DataAgentInsightDashboardControllerQueryTest 3、DashboardExecutionServiceTest 7 等全绿 |
| Docker 环境测试（ObjectRefServiceTest / S3DatasetFileStorageServiceTest / ScriptDatasetReadObjectRefIntegrationTest） | BLOCKED | Testcontainers 需要 Docker，沙箱无 Docker 环境；与本计划改动无关（失败原因均为 "Could not find a valid Docker environment"） |
| DataAgent 打包 `mvn -DskipTests package` | PASS | jar 507,569,636 bytes |
| Python Runner 全量 `pytest -q mateclaw-python-runner/tests` | PASS | 53 passed（含 test_prepared_input_contract.py 7 用例新契约） |
| UI 全量测试 `vitest --run` | PASS | 71 文件 / 374 用例通过 |
| UI TypeScript `vue-tsc --noEmit` | PASS | 无错误 |
| UI 构建 `vite build` | PASS | 构建成功（仅既有 chunk 体积告警） |
| 本地模拟联调（任务 8） | PASS（部分 NOT_RUN） | 真实后端 + 真实 Aloudata 数据集：健康/登录/查询计划预览（计划+下推报告）/结果预览错误治理全绿；联调脚本 exit=0。双源 Join 与 WireMock 长驻 NOT_RUN（见 evidence） |
| Chrome CDP 9222 视觉验收（任务 9） | BLOCKED | 环境无调试端口 Chrome（`nc 127.0.0.1 9222` 不通）；脚本 `cdp-table-detail-query-visual-check.mjs` 已就绪，连接失败显式输出 BLOCKED |
| Playwright e2e（任务 9） | NOT_RUN | 沙箱无浏览器二进制；用例 `e2e/table-detail-query.spec.ts` 已提交，命令：`MATECLAW_E2E_ALOUDATA_MODE=simulation npm --prefix mateclaw-dataagent-ui run test:e2e -- e2e/table-detail-query.spec.ts --reporter=line` |
| `git diff --check` / `git status --short` | PASS | 无空白错误、无冲突标记、无敏感值、无误改用户文件 |
| 中文 commit 与远端 SHA 一致 | PASS | 逐任务提交（见下），push 后校验本地 HEAD == origin/feature/dev_fu |

## 关键提交（均为中文 commit，已推送）

1. `2c706ab3` docs: 冻结表格详情查询契约与测试样例
2. `ae472bc2` 实现后端 Query Planner 与下推决策
3. `744d6e9f` 扩展四类数据集 Adapter 排序下推与查询计划预览接口
4. `c6c37eea` 实现 prepared input 端点与 Python SDK datasets.input 契约
5. `9e4f95cd` 实现前端查询配置弹窗与运行时排序三态
6. `65bcad4f` Python 系统区改用 datasets.input 标准读取契约
7. `1b736946` 实现组件执行 QueryContext 链路与结果集分页预览接口
8. `4a333ce5` 新增表格详情页查询链路本地联调与 CDP 验收脚本
9. （本次）结果集预览轻量 DTO 与全局错误映射完善 + 发布报告

## 遗留项（需用户本地环境执行）

1. 在用户 Chrome（`--remote-debugging-port=9222`）运行 `node mateclaw-dataagent-ui/e2e/cdp-table-detail-query-visual-check.mjs` 完成视觉验收，并按需建立 Playwright 快照基线。
2. 预置 Aloudata + JDBC 双源 fixture 与隔离仪表盘后运行完整「配置→执行→Join→结果分页」联调（脚本与 fixture 已就绪）。
3. 沙箱/无 Docker 环境下 3 个 Testcontainers 用例需在有 Docker 的环境运行。

## 明确未伪造事项

- 未把 NOT_RUN/BLOCKED 写成 PASS；未以静态代码阅读替代真实验证。
- 后端新链路已通过本地真实服务 + 真实数据集 HTTP 验证（非纯单测）。
