#!/usr/bin/env bash
set -euo pipefail

: "${MATECLAW_E2E_TOKEN:?set MATECLAW_E2E_TOKEN}"
: "${MATECLAW_E2E_WORKSPACE_ID:?set MATECLAW_E2E_WORKSPACE_ID}"
BASE_URL="${MATECLAW_E2E_API_BASE_URL:-http://127.0.0.1:18189/dataagent/api}"
STATE_FILE="${MATECLAW_E2E_STATE_FILE:-/tmp/mateclaw-dashboard-mvp-state-${MATECLAW_E2E_WORKSPACE_ID}.json}"
AUTH_HEADER="Authorization: Bearer ${MATECLAW_E2E_TOKEN}"
WORKSPACE_HEADER="X-Workspace-Id: ${MATECLAW_E2E_WORKSPACE_ID}"

test -f "$STATE_FILE" || { echo "state file not found: $STATE_FILE" >&2; exit 1; }
command -v jq >/dev/null || { echo 'jq is required' >&2; exit 1; }

# A global admin may pass the coarse workspace interceptor, but the resource
# service must still reject a dashboard whose persisted workspace differs from
# the request context. Keep this as a real API check before cleanup removes it.
security_dashboard_id=$(jq -er '.multiSourceDashboardId // .apiFileDashboardId // empty' "$STATE_FILE")
if [[ -n "$security_dashboard_id" ]]; then
  security_code=$(curl --silent --show-error -o /dev/null -w '%{http_code}' \
    -H "$AUTH_HEADER" -H 'X-Workspace-Id: 999999999' \
    "$BASE_URL/v1/insight/dashboards/$security_dashboard_id")
  if [[ "$security_code" != "403" ]]; then
    echo "cross-workspace dashboard read was not denied: HTTP $security_code" >&2
    exit 1
  fi
  echo "Cross-workspace dashboard read denied with HTTP 403."
else
  echo 'No dashboard in state; cross-workspace dashboard check cannot run.' >&2
  exit 1
fi

delete_resource() {
  local path="$1" id="$2"
  [[ -n "$id" && "$id" != "null" ]] || return 0
  curl --fail-with-body --silent --show-error -X DELETE \
    -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" "$BASE_URL$path/$id" >/dev/null
}

while read -r id; do
  delete_resource /v1/insight/dashboards "$id"
done < <(jq -r '.multiSourceDashboardId,.apiFileDashboardId,.compatibilityDashboardId,.errorDashboardId,.cancelDashboardId,.timeoutDashboardId,.resourceDashboardId,.largeDashboardId' "$STATE_FILE")

while read -r id; do
  delete_resource /v1/datasets "$id"
done < <(jq -r '.fileDatasetId,.httpDatasetId,.jdbcDatasetId' "$STATE_FILE")

while read -r id; do
  delete_resource /v1/datasources "$id"
done < <(jq -r '.httpDatasourceId,.jdbcDatasourceId' "$STATE_FILE")

while read -r id; do
  code=$(curl --silent --show-error -o /dev/null -w '%{http_code}' \
    -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" "$BASE_URL/v1/insight/dashboards/$id")
  if [[ "$code" == "200" ]]; then
    echo "dashboard still readable after cleanup: $id" >&2
    exit 1
  fi
done < <(jq -r '.multiSourceDashboardId,.apiFileDashboardId,.compatibilityDashboardId,.errorDashboardId,.cancelDashboardId,.timeoutDashboardId,.resourceDashboardId,.largeDashboardId' "$STATE_FILE")

echo "Cleanup verified for dashboards, datasets and datasources."
echo "Uploaded file object retained because no public delete API exists yet: $(jq -r '.fileObjectId' "$STATE_FILE")"
