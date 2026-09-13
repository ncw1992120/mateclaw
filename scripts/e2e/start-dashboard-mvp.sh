#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE=(docker compose -f "${ROOT_DIR}/docker-compose.test.yml")

# The local simulation stack intentionally uses the same published ports as
# the disposable E2E stack. Fail before creating partial E2E containers when
# it is still running; callers can restore it after E2E cleanup.
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^mateclaw-local-sim-'; then
  echo '本地模拟服务正在占用 E2E 端口；请先执行 dev-support/local-simulation/scripts/cleanup.sh，再启动 E2E。' >&2
  exit 2
fi

# E2E data is disposable. Remove only the test stack's containers/volumes so
# repeated seed runs cannot collide with stale dataset names or credentials.
"${COMPOSE[@]}" down -v --remove-orphans
"${COMPOSE[@]}" up -d --build

api_url="${MATECLAW_E2E_API_BASE_URL:-http://127.0.0.1:18189/dataagent/api}"
ui_url="${MATECLAW_UI_BASE_URL:-http://127.0.0.1:15174}"
deadline=$((SECONDS + 180))
api_ready=0
ui_ready=0
while (( SECONDS < deadline )); do
  api_ready=0
  ui_ready=0
  if curl --fail --silent --connect-timeout 2 --max-time 5 "${api_url}/actuator/health" >/dev/null 2>&1; then api_ready=1; fi
  if curl --fail --silent --connect-timeout 2 --max-time 5 "${ui_url}/" >/dev/null 2>&1; then ui_ready=1; fi
  if (( api_ready == 1 && ui_ready == 1 )); then
    echo "Dashboard MVP E2E services are ready: DataAgent=${api_url}, UI=${ui_url}"
    exit 0
  fi
  sleep 2
done

echo "Dashboard MVP E2E services did not become ready within 180 seconds" >&2
"${COMPOSE[@]}" ps
exit 1
