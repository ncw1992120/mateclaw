#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
checker="$repo_root/scripts/verify-dashboard-external-prerequisites.sh"
[[ -x "$checker" ]] || { echo "外部前置检查脚本不可执行" >&2; exit 2; }

run_config_check() {
  local product_url="$1" semantic_url="$2"
  ALOU_DATA_EXTERNAL_TEST=true \
  ALOU_DATA_PRODUCT_BASE_URL="$product_url" \
  ALOU_DATA_SEMANTIC_BASE_URL="$semantic_url" \
  ALOU_DATA_TENANT_ID=test-tenant \
  ALOU_DATA_AUTH_TYPE=UID \
  ALOU_DATA_AUTH_VALUE=placeholder \
  ALOU_DATA_TEST_DATASOURCE_ID=9001 \
  ALOU_DATA_TEST_VIEW_NAME=local_sales_view \
  ALOU_DATA_TEST_FILTER_COLUMN=region \
  ALOU_DATA_TEST_FILTER_VALUE=east \
  "$checker" --external
}

grep -q 'EXTERNAL-PREREQUISITES-CONFIG-PASS' < <(run_config_check 'https://product.example:443' 'http://semantic.example:80')
grep -q 'EXTERNAL-PREREQUISITES-CONFIG-PASS' < <(run_config_check 'https://product.example' 'https://semantic.example:443')

set +e
run_config_check 'http://product.example' 'http://semantic.example:80' >/tmp/mateclaw-external-prereq-negative.out 2>&1
exit_code=$?
set -e
[[ "$exit_code" == 3 ]] || { echo "产品层 HTTP 未被拒绝，退出码=$exit_code" >&2; cat /tmp/mateclaw-external-prereq-negative.out >&2; exit 1; }
grep -q 'ALOU_DATA_PRODUCT_BASE_URL 必须是 HTTPS URL' /tmp/mateclaw-external-prereq-negative.out
rm -f /tmp/mateclaw-external-prereq-negative.out
echo 'EXTERNAL-PREREQUISITES-CONTRACT-PASS'
