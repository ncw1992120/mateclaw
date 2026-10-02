#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.local"
[[ -f "$ENV_FILE" ]] || { echo "请先运行 scripts/start.sh" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE" config --quiet
docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE" ps
curl --fail --silent "http://127.0.0.1:${WIREMOCK_PORT:-18081}/__admin/health" >/dev/null
curl --fail --silent --insecure "https://127.0.0.1:${WIREMOCK_HTTPS_PORT:-18443}/__admin/health" >/dev/null
orders_json="$(curl --fail --silent "http://127.0.0.1:${WIREMOCK_PORT:-18081}/orders")"
paid_json="$(curl --fail --silent "http://127.0.0.1:${WIREMOCK_PORT:-18081}/orders?status=PAID")"
secure_paid_json="$(curl --fail --silent --insecure "https://127.0.0.1:${WIREMOCK_HTTPS_PORT:-18443}/orders?status=PAID")"
grep -q '"id":1005' <<<"$orders_json"
grep -q '"id":1004' <<<"$paid_json"
grep -q '"id":1004' <<<"$secure_paid_json"
aloudata_tree="$(curl --fail --silent "http://127.0.0.1:${WIREMOCK_PORT:-18081}/anymetrics/api/v1/analysisview/treeList")"
aloudata_result="$(curl --fail --silent "http://127.0.0.1:${WIREMOCK_PORT:-18081}/semantic/api/v1.1/analysisView/query")"
aloudata_metrics="$(curl --fail --silent -X POST "http://127.0.0.1:${WIREMOCK_PORT:-18081}/semantic/api/v1.1/metrics/query" -H 'Content-Type: application/json' -d '{"viewName":"local_sales_view"}')"
grep -q 'local_sales_view' <<<"$aloudata_tree"
grep -q '"analysisView"' <<<"$aloudata_result"
grep -q '"data"' <<<"$aloudata_metrics"
mysql_rows="$(docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE" exec -T -e MYSQL_PWD="${MYSQL_PASSWORD}" mysql mysql -N -B -u"${MYSQL_USER}" "${MYSQL_DATABASE}" -e 'SELECT COUNT(*) FROM orders;')"
postgres_rows="$(docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE" exec -T postgres psql -At -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c 'SELECT COUNT(*) FROM orders;')"
[[ "$mysql_rows" == "10" ]]
[[ "$postgres_rows" == "10" ]]
python3 - "${ROOT_DIR}/files/fixtures-manifest.json" "${ROOT_DIR}/files" <<'PY'
import hashlib
import json
import pathlib
import sys

manifest_path = pathlib.Path(sys.argv[1])
files_root = pathlib.Path(sys.argv[2])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("rowCount") != 10:
    raise SystemExit(f"fixture manifest rowCount unexpected: {manifest.get('rowCount')}")
entries = manifest.get("files", [])
if {entry.get("fileName") for entry in entries} != {"orders.csv", "orders.json", "orders.parquet", "orders.xlsx"}:
    raise SystemExit("fixture manifest file set mismatch")
for entry in entries:
    path = files_root / entry["fileName"]
    raw = path.read_bytes()
    if len(raw) != entry["bytes"]:
        raise SystemExit(f"fixture byte length mismatch: {path.name}")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != entry["sha256"]:
        raise SystemExit(f"fixture sha256 mismatch: {path.name}")
    expected = entry.get("filter", {})
    if expected.get("field") != "status" or expected.get("value") != "PAID" or expected.get("expectedRows") != 3:
        raise SystemExit(f"fixture filter contract mismatch: {path.name}")
print("fixture manifest checksum/schema contract passed")
PY
echo "本地模拟环境健康检查通过（文件 fixture 已按 manifest 校验 SHA-256 与大小）。"
