#!/usr/bin/env bash
set -euo pipefail

: "${MATECLAW_E2E_WORKSPACE_ID:?set MATECLAW_E2E_WORKSPACE_ID}"
STATE_FILE="${MATECLAW_E2E_STATE_FILE:-/tmp/mateclaw-dashboard-mvp-state-${MATECLAW_E2E_WORKSPACE_ID}.json}"

command -v jq >/dev/null || { echo 'jq is required' >&2; exit 1; }
test -f "$STATE_FILE" || { echo "state file not found: $STATE_FILE" >&2; exit 1; }

# Only resource IDs are emitted. Authentication remains in the caller's
# environment and is never read from or written to the state file.
jq -er '
  def export($name; $value): "export " + $name + "=" + (($value // "") | tostring | @sh);
  export("MATECLAW_E2E_MULTI_SOURCE_DASHBOARD_ID"; .multiSourceDashboardId),
  export("MATECLAW_E2E_API_FILE_DASHBOARD_ID"; .apiFileDashboardId),
  export("MATECLAW_E2E_ECHARTS_DASHBOARD_ID"; .echartsDashboardId),
  export("MATECLAW_E2E_COMPATIBILITY_DASHBOARD_ID"; .compatibilityDashboardId),
  export("MATECLAW_E2E_ERROR_DASHBOARD_ID"; .errorDashboardId),
  export("MATECLAW_E2E_CANCEL_DASHBOARD_ID"; .cancelDashboardId),
  export("MATECLAW_E2E_TIMEOUT_DASHBOARD_ID"; .timeoutDashboardId),
  export("MATECLAW_E2E_RESOURCE_DASHBOARD_ID"; .resourceDashboardId),
  export("MATECLAW_E2E_LARGE_RESULT_DASHBOARD_ID"; .largeDashboardId)
' "$STATE_FILE"
