# 设计冻结与验收基线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 形成首期范围、统一术语和可执行验收矩阵，作为后续所有子计划的唯一设计基线。

**Architecture:** 本计划只修改文档，不修改运行时代码。`design.md` 描述产品与总体方案，本计划产出的验收矩阵负责把每条首期需求映射到后续子计划和验证证据。

**Tech Stack:** Markdown、`rg`、Git diff checks。

**Spec:** `docs/策略解读/design.md`

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 2 节（DOC-U01～DOC-U04）。

**当前状态（2026-09-13）：** 文档范围与验收矩阵已冻结并通过设计门禁，已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`；候选提交及后续计划更新已推送到 `origin/feature/dev_fu`。

**本次范围补充：** 本地外部依赖统一由 `dev-support/local-simulation/` 的 Docker Compose 提供；身份与权限完善延期，仅保留已有行为回归。

**开发验证入口：** 设计门禁前先执行 `make dashboard-prerequisites-simulation`，确认本地模拟数据、接口和 Runner 可用；本子计划只验证文档与矩阵，不把模拟服务健康误当作 G0 之外的功能验收。

**本轮复验记录（2026-09-13）：** `make dashboard-prerequisites-simulation`、`bash scripts/verify-dashboard-design.sh` 均通过；设计门禁输出 `DESIGN-PASS`。

## Global Constraints

- 首期包含 JDBC、Aloudata 指标视图、HTTP/API、文件和 Python 多源预处理。
- 首期不包含 JS、湖仓目录、流式数据源、Trino 和 AI 自动执行脚本。
- 产品侧只配置参数作用范围；参数到字段的使用由 `datasets.read` 表达。

## 计划定位

本文件是总体实施计划中的 `G0` 子计划（编号 `00`），不是一个单独的开发 Task。下面的 `Task 1` 是本子计划的具体任务，`Step 1～5` 是该任务的执行步骤。

---

### Task 1: 建立能力与验收矩阵

**Files:**
- Create: `docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md`
- Modify: `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md`
- Modify: `docs/策略解读/design.md`

**Interfaces:**
- Consumes: `design.md` 第 4–12 节。
- Produces: `REQ-DS-*`、`REQ-QUERY-*`、`REQ-RUNNER-*`、`REQ-UI-*` 编号，供 01–09 子计划引用。

- [x] **Step 1: 写入能力矩阵**

矩阵逐项列出 `Schema`、过滤语义、分页、权限、资源限制、输出格式和对应子计划；四类来源不能合并为“其他数据源”。

- [x] **Step 2: 写入验收场景**

至少覆盖单源预览、JDBC+Aloudata Join、API+文件 Join、参数筛选、空结果、超时、资源超限、取消任务和旧路径兼容；权限拒绝仅保留已有行为回归，不在本次新增权限模型。

- [x] **Step 3: 扫描矛盾和占位符**

```bash
rg -n "需要字段映射|手动映射|首期.*JS|首期.*Trino" docs/策略解读/design.md docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md
```

Expected: 无未解释命中；历史方案中的非首期能力明确标记为未来选项。

另有可重复检查入口：`scripts/verify-dashboard-design.sh`，会校验首期范围、JDBC-only SQL 边界、`datasets.read` 时序、Runner 依赖边界以及 01–09 子计划对测试矩阵的引用。

- [x] **Step 4: 校验**

```bash
git diff --check -- docs/策略解读/design.md docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md
```

- [x] **Step 5: 统一交付节点**：`design.md` 与 G0 文件已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`，并已推送到 `origin/feature/dev_fu`。

```bash
git add docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md
git commit -m "docs: freeze dashboard mvp acceptance"
```

## 视觉验收（CDP）

本子计划只产出设计文档和验收矩阵，没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。页面相关的视觉用例和 CDP 证据统一见测试矩阵 VIS-UI01～VIS-UI08。

## 测试执行与预期结果

```bash
rg -n "首期|第一阶段|datasets\.read|SQL 只|不支持|兼容" \
  docs/策略解读/design.md \
  docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md \
  docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md
```

预期：DOC-U01～DOC-U04 均能定位到唯一且不冲突的设计条款；每个 `REQ-*` 至少关联一个测试 ID，测试 ID 只属于一个明确功能域。

## 验收标准

- DOC-U01～DOC-U04 全部通过，没有未解释的范围冲突。
- 01～09 每个子计划都引用本测试矩阵并列出独立验收标准。
- `git diff --check` 通过，文档中没有真实凭据、占位符或失效相对链接。
