#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
design="$repo_root/docs/策略解读/design.md"
acceptance="$repo_root/docs/superpowers/specs/2026-09-11-dashboard-mvp-acceptance.md"
matrix="$repo_root/docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md"
plans="$repo_root/docs/superpowers/plans"
overall="$plans/2026-09-11-dashboard-overall-implementation-plan.md"
prerequisites="$plans/2026-09-13-dashboard-external-prerequisites.md"
compose_test="$repo_root/docker-compose.test.yml"
seed="$repo_root/scripts/e2e/seed-dashboard-mvp.sh"
export_env="$repo_root/scripts/e2e/export-dashboard-mvp-env.sh"
cdp_visual="$repo_root/mateclaw-dataagent-ui/e2e/cdp-dashboard-visual-check.mjs"

fail() { printf 'DESIGN-FAIL: %s\n' "$1" >&2; exit 1; }
has() { rg -q -- "$1" "$2"; }

[[ -f "$design" && -f "$acceptance" && -f "$matrix" ]] || fail "required design or acceptance document is missing"
[[ -f "$compose_test" && -f "$seed" && -x "$export_env" ]] || fail "E2E Compose or seed/export script is missing"
[[ -f "$cdp_visual" ]] || fail "CDP visual verification script is missing"
[[ -f "$overall" ]] || fail "overall implementation plan is missing"
[[ -f "$prerequisites" ]] || fail "external prerequisites plan is missing"
has '2026-09-13-dashboard-external-prerequisites.md' "$overall" || fail "overall plan does not reference external prerequisites"

has '第一阶段范围状态：已冻结' "$design" || fail "design is not marked frozen"
has '首期支持 JDBC、Aloudata 指标视图、HTTP/API 和文件数据源' "$design" || fail "first-phase source scope is missing"
has 'JS、湖仓目录、流式数据源和 Trino 等能力不在首期范围' "$design" || fail "first-phase exclusions are missing"
has 'datasets\.read' "$design" || fail "datasets.read contract is missing"
has 'datasets.*由 Python Runner.*注入' "$design" || fail "design sample does not document Runner-injected datasets client"
if has 'from mateclaw import datasets' "$design"; then
  fail "design sample must not import datasets module instead of using Runner-injected client"
fi
if has 'from mateclaw import datasets' "$plans/08-python-runner-and-sdk.md"; then
  fail "Runner sub-plan sample must not import datasets module instead of using injected client"
fi
if has 'from mateclaw import datasets' "$repo_root/docs/superpowers/specs/2026-09-11-query-parameter-pushdown-design.md"; then
  fail "pushdown spec sample must not import datasets module instead of using injected client"
fi
if ! has 'SQL.*仅对 JDBC 数据源开放' "$design" && ! has 'JDBC 数据源才支持用户配置标准 SQL' "$design"; then
  fail "JDBC-only SQL boundary is missing"
fi
has '新 Runner 不支持运行时.*pip install' "$design" || fail "Runner dependency boundary is missing"
has 'CAT-U06' "$matrix" || fail "unified adapter read coverage CAT-U06 is missing"
has 'CAT-U07' "$matrix" || fail "missing-adapter rejection coverage CAT-U07 is missing"
if ! has '已绑定候选 SHA' "$overall" && ! has '尚未绑定候选 SHA' "$overall"; then
  fail "overall plan must distinguish worktree evidence from candidate SHA evidence"
fi
if ! has '候选 SHA 验收' "$overall" && ! has '完整候选 SHA 验收仍待提交后重跑' "$overall"; then
  fail "overall plan must record the current candidate verification state"
fi

has 'https://e2e-http:8443' "$seed" || fail "E2E HTTP source is not registered as HTTPS"
has 'E2E ECharts Binding Dashboard' "$seed" || fail "E2E ECharts binding dashboard seed is missing"
has 'echartsDashboardId' "$export_env" || fail "E2E ECharts dashboard export is missing"
has 'MATECLAW_E2E_DASHBOARD_ID' "$export_env" || fail "generic CDP dashboard export is missing"
has 'chart-container canvas' "$repo_root/mateclaw-dataagent-ui/e2e/dashboard-multi-source.spec.ts" || fail "E2E ECharts canvas assertion is missing"
has 'https-port 8443' "$compose_test" || fail "E2E WireMock HTTPS port is missing"
has 'MATECLAW_DATASET_HTTP_ALLOW_TLS_TEST_ENDPOINT: "true"' "$compose_test" || fail "E2E DataAgent TLS fixture mode is missing"
has '"test:e2e:cdp": "node e2e/cdp-dashboard-visual-check.mjs"' "$repo_root/mateclaw-dataagent-ui/package.json" || fail "CDP npm verification entry is missing"
if has 'MATECLAW_DATASET_HTTP_ALLOW_INSECURE_TEST_ENDPOINT' "$compose_test"; then
  fail "E2E Compose must not enable the insecure HTTP endpoint exception"
fi

for plan in "$plans"/{00-design-freeze-and-acceptance,01-dataset-catalog-contract,02-aloudata-analysis-view-adapter,03-jdbc-adapter,04-http-api-adapter,05-file-adapter,06-object-ref-transport,07-dataset-management-ui,08-python-runner-and-sdk,09-dashboard-runtime-integration}.md; do
  [[ -f "$plan" ]] || fail "sub-plan is missing: $(basename "$plan")"
  has '2026-09-11-dashboard-mvp-test-and-acceptance.md' "$plan" || fail "sub-plan does not reference the test matrix: $(basename "$plan")"
done

printf 'DESIGN-PASS: frozen scope, SQL boundary, datasets.read timing, dependency boundary, and 01-09 matrix references verified\n'
