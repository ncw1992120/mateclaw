#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_DIR="${ROOT_DIR}/dev-support/local-simulation"
MODE="local"
if [[ "${1:-}" == "--external" ]]; then
  MODE="external"
elif [[ "${1:-}" == "--simulation" ]]; then
  MODE="simulation"
elif [[ "${1:-}" != "" && "${1:-}" != "--local" ]]; then
  echo "用法：$0 [--local|--simulation|--external]" >&2
  exit 2
fi

require_command() {
  command -v "$1" >/dev/null || { echo "缺少命令：$1" >&2; exit 2; }
}

if [[ "$MODE" != "external" ]]; then
  require_command docker
  require_command curl
  require_command bash
  require_command jq
  [[ -x "${LOCAL_DIR}/scripts/check.sh" ]] || { echo "本地检查入口不可执行" >&2; exit 2; }
  for fixture in orders.csv orders.json orders.parquet orders.xlsx; do
    [[ -s "${LOCAL_DIR}/files/${fixture}" ]] || { echo "fixture 缺失或为空：${fixture}" >&2; exit 2; }
  done
  [[ -s "${LOCAL_DIR}/api/orders-openapi.yaml" ]] || { echo "OpenAPI 定义缺失或为空" >&2; exit 2; }
  grep -q '^openapi: 3\.' "${LOCAL_DIR}/api/orders-openapi.yaml"
  grep -q 'operationId: listOrders' "${LOCAL_DIR}/api/orders-openapi.yaml"
  grep -q 'name: status' "${LOCAL_DIR}/api/orders-openapi.yaml"
  manifest="${LOCAL_DIR}/files/fixtures-manifest.json"
  [[ -s "$manifest" ]] || { echo "fixture manifest 缺失或为空" >&2; exit 2; }
  require_command openssl
  while IFS=$'\t' read -r file_name expected_bytes expected_sha; do
    actual_bytes="$(wc -c < "${LOCAL_DIR}/files/${file_name}" | tr -d ' ')"
    actual_sha="$(openssl dgst -sha256 -r "${LOCAL_DIR}/files/${file_name}" | awk '{print $1}')"
    [[ "$actual_bytes" == "$expected_bytes" && "$actual_sha" == "$expected_sha" ]] || {
      echo "fixture checksum 不匹配：${file_name}" >&2
      exit 2
    }
  done < <(jq -r '.files[] | [.fileName, (.bytes|tostring), .sha256] | @tsv' "$manifest")
  docker compose -f "${LOCAL_DIR}/docker-compose.yml" --env-file "${LOCAL_DIR}/.env.local" config --quiet
  bash -n "${LOCAL_DIR}/scripts/"*.sh "${LOCAL_DIR}/wiremock/init-tls.sh"
  "${LOCAL_DIR}/scripts/check.sh"
  if [[ "$MODE" == "simulation" ]]; then
    simulation_env="${LOCAL_DIR}/.env.aloudata-simulation.example"
    [[ -s "$simulation_env" ]] || { echo "本地 Aloudata 模拟变量模板缺失" >&2; exit 2; }
    grep -q 'ALOU_DATA_TEST_VIEW_NAME=local_sales_view' "$simulation_env"
    grep -q 'ALOU_DATA_TEST_DATASOURCE_ID=9001' "$simulation_env"
    echo "EXTERNAL-PREREQUISITES-SIMULATION-PASS"
  else
    echo "EXTERNAL-PREREQUISITES-LOCAL-PASS"
  fi
  exit 0
fi

[[ "${ALOU_DATA_EXTERNAL_TEST:-}" == "true" ]] || {
  echo "BLOCKED: ALOU_DATA_EXTERNAL_TEST=true 未设置" >&2
  exit 3
}
required_vars=(
  ALOU_DATA_PRODUCT_BASE_URL
  ALOU_DATA_SEMANTIC_BASE_URL
  ALOU_DATA_TENANT_ID
  ALOU_DATA_AUTH_TYPE
  ALOU_DATA_AUTH_VALUE
  ALOU_DATA_TEST_DATASOURCE_ID
  ALOU_DATA_TEST_VIEW_NAME
  ALOU_DATA_TEST_FILTER_COLUMN
  ALOU_DATA_TEST_FILTER_VALUE
)
for variable in "${required_vars[@]}"; do
  [[ -n "${!variable:-}" ]] || { echo "BLOCKED: 缺少 ${variable}" >&2; exit 3; }
done
for variable in ALOU_DATA_PRODUCT_BASE_URL ALOU_DATA_SEMANTIC_BASE_URL; do
  value="${!variable}"
  [[ "$value" =~ ^https://[^[:space:]/]+(:[0-9]+)?(/[^[:space:]]*)?$ ]] || {
    echo "BLOCKED: ${variable} 必须是 HTTPS URL" >&2
    exit 3
  }
done
echo "EXTERNAL-PREREQUISITES-CONFIG-PASS (未执行真实接口查询)"
