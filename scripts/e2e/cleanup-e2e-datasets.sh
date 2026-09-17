#!/usr/bin/env bash
# 清理 E2E 测试残留的数据集/数据源。
#
# 背景：scripts/e2e/seed-dashboard-mvp.sh 每次运行都会通过真实 API 创建
# 名为 "E2E JDBC Orders Dataset <时间戳>" / "E2E Aloudata Metrics Dataset <时间戳>"
# 等测试数据；verify-dashboard-mvp-cleanup.sh 只删除 state file 里记录的部分 ID，
# 未跑 cleanup 或 state 丢失时会残留。本脚本按 "E2E " 名称前缀统一清理。
#
# 用法：
#   MATECLAW_E2E_TOKEN=xxx MATECLAW_E2E_WORKSPACE_ID=1 ./cleanup-e2e-datasets.sh            # DRY_RUN：仅列出
#   MATECLAW_E2E_TOKEN=xxx MATECLAW_E2E_WORKSPACE_ID=1 DRY_RUN=0 ./cleanup-e2e-datasets.sh  # 实际删除
set -euo pipefail

: "${MATECLAW_E2E_TOKEN:?set MATECLAW_E2E_TOKEN}"
: "${MATECLAW_E2E_WORKSPACE_ID:?set MATECLAW_E2E_WORKSPACE_ID}"
BASE_URL="${MATECLAW_E2E_API_BASE_URL:-http://127.0.0.1:18189/dataagent/api}"
DRY_RUN="${DRY_RUN:-1}"

AUTH_HEADER="Authorization: Bearer ${MATECLAW_E2E_TOKEN}"
WORKSPACE_HEADER="X-Workspace-Id: ${MATECLAW_E2E_WORKSPACE_ID}"

command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }
command -v jq >/dev/null || { echo 'jq is required' >&2; exit 1; }

api() {
  local method="$1" path="$2"
  curl --fail-with-body --silent --show-error -X "$method" \
    -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" "$BASE_URL$path"
}

delete_resource() {
  local path="$1" id="$2" name="$3"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[DRY_RUN] would DELETE $path/$id  ($name)"
  else
    api DELETE "$path/$id" >/dev/null
    echo "[DELETED] $path/$id  ($name)"
  fi
}

echo "== 扫描 E2E 残留数据集（/v1/datasets） =="
datasets_json=$(api GET /v1/datasets)
dataset_total=$(jq '.data | length' <<<"$datasets_json")
matched_datasets=$(jq -r '.data[] | select((.name // "") | startswith("E2E ")) | [.id, .name] | @tsv' <<<"$datasets_json")
if [[ -z "$matched_datasets" ]]; then
  echo "无 E2E 前缀数据集（共 $dataset_total 个数据集）。"
else
  while IFS=$'\t' read -r id name; do
    delete_resource /v1/datasets "$id" "$name"
  done <<<"$matched_datasets"
fi

echo "== 扫描 E2E 残留数据源（/v1/datasources） =="
datasources_json=$(api GET /v1/datasources)
datasource_total=$(jq '.data | length' <<<"$datasources_json")
matched_datasources=$(jq -r '.data[] | select((.name // "") | startswith("E2E ")) | [.id, .name] | @tsv' <<<"$datasources_json")
if [[ -z "$matched_datasources" ]]; then
  echo "无 E2E 前缀数据源（共 $datasource_total 个数据源）。"
else
  while IFS=$'\t' read -r id name; do
    delete_resource /v1/datasources "$id" "$name"
  done <<<"$matched_datasources"
fi

echo "== 完成（DRY_RUN=$DRY_RUN；置 DRY_RUN=0 实际删除） =="
