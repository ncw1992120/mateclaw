# Dashboard Multi-Dataset Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让仪表盘组件按数据源类型显示正确的配置，并在选择两个及以上数据集时渐进展开 Python 多数据源预处理。

**Architecture:** 保留旧 Aloudata `metrics/dimensions` 绑定作为兼容路径；新增统一数据集输入绑定，数据集选择器按 Aloudata/JDBC/接口/文件分组展示。组件属性面板负责单源类型配置，`DatasetInputPanel` 负责多源输入、别名、输入预览和 Python 草稿，最终结果仍通过现有 Dashboard execution API 绑定到组件。

**补充实现：** Base Script 由系统区域和用户区域组成；系统区域可按已声明参数自动生成 `datasets.read(filters=...)`，用户区域在重新生成时保持原样。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Pinia、Vitest、Playwright。

**Spec:** `docs/策略解读/design.md` 第 4、5.5、5.6 节及本轮已确认的“方案 A + 方案 B”交互设计。

## Global Constraints

- Aloudata 一期只支持选择已有指标视图；旧指标/维度字段仅作兼容。
- JDBC SQL 只对 JDBC 数据源开放，使用标准 SQL；不得把 SQL 配置显示给 Aloudata/API/文件数据源。
- HTTP/API 只能选择已登记 API 定义；文件大类下按 Excel、CSV、TXT、JSON、Parquet 展示格式。
- Python 仅由用户维护草稿或平台插入读取模板，不提供 AI 自动生成；读取过滤条件继续由 `datasets.read(..., filters=...)` 声明并由服务端下推。
- 未跟踪的 `generated_cljd_zcl.sql`、`mateclaw-python-runner/examples/` 和 `mateclaw-python-runner/tests/test_strategy_readout.py` 不得纳入提交。

### Task 1: Extend frontend binding types and source classification

**Files:**
- Modify: `mateclaw-dataagent-ui/src/types/index.ts`
- Create: `mateclaw-dataagent-ui/src/utils/data-binding.ts`
- Test: `mateclaw-dataagent-ui/src/utils/__tests__/data-binding.spec.ts`

- [x] Write failing tests for source classification and grouped dataset labels; legacy fields remain covered by the existing component serialization tests.
- [x] Run the focused Vitest file and verify it fails because the classifier is absent.
- [x] Add `DataInputBinding`, `PythonTransformConfig` types and pure helpers `classifyDatasourceType`, `datasetCategoryLabel`, `groupDatasets`, and `groupDatasources`.
- [x] Run the focused Vitest file and the existing utility tests; verify all pass.

### Task 2: Progressive multi-dataset input interaction

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`
- Test: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`

- [x] Add failing tests proving the Python section is hidden for zero/one input, appears for two inputs, dataset options are grouped by source category, and removing an input preserves the script draft.
- [x] Run the focused tests and verify the expected failures.
- [x] Implement grouped dataset options, an explicit `添加数据集` action, and progressive copy/visibility. Keep input preview and descriptor loading per input.
- [x] Add a visible “single source / multi-source” state hint and retain the existing execution/preview API; do not add a second execution path.
- [x] Run focused tests and existing DatasetInputPanel tests; verify all pass.

### Task 3: Source-aware component property panel

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`
- Test: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/PropertyPanel.spec.ts`

- [x] Add failing tests for JDBC selection showing SQL controls, Aloudata selection showing metric controls, and API/file selection not showing SQL or Aloudata metric controls.
- [x] Run the focused tests and verify they fail against the current unconditional metric UI.
- [x] Add source-type computed state and a source-aware binding mode. JDBC uses a SQL textarea; Aloudata keeps the existing metrics/dimensions compatibility controls; API/file show unified dataset guidance.
- [x] Ensure changing source clears incompatible fields and emits a serializable binding while preserving old fields for legacy Aloudata components.
- [x] Extend the component preview contract with a guarded JDBC SQL branch that reuses AST validation, the read-only connection pool, a 30-second timeout and the existing chart/table/KPI renderer.
- [x] Run focused tests, all UI unit tests, and the production build.

### Task 4: Visual and runtime verification

**Files:**
- Modify: `mateclaw-dataagent-ui/e2e/dataset-management-entry.spec.ts` only if a regression fixture is required
- Modify: `docs/superpowers/plans/2026-09-11-dashboard-overall-implementation-plan.md`
- Modify: `docs/superpowers/plans/07-dataset-management-ui.md`
- Modify: `docs/superpowers/plans/09-dashboard-runtime-integration.md`
- Modify: `docs/superpowers/evidence/dashboard-mvp-acceptance.md`

- [x] Run UI unit tests and production build.
- [x] Run local simulation Playwright with one worker and assert a JDBC card shows SQL while an Aloudata card shows its own controls.
- [x] Run the CDP visual script and capture editor AX/screenshot evidence for one-source and two-source states.
- [x] Update plans with exact results and document the remaining JDBC card-preview integration gate.
- [ ] Run `git diff --check`, stage named files only, commit in Chinese, and push after local/remote SHA parity verification.
