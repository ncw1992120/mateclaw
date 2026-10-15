#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.local"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "${ROOT_DIR}/.env.example" "$ENV_FILE"
  echo "已创建 ${ENV_FILE}，请按需调整本地端口和密码。"
fi

COMPOSE=(docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE")
"${COMPOSE[@]}" up -d

# Poll only long-running services with healthchecks before validating fixtures.
containers=(mateclaw-local-sim-mysql mateclaw-local-sim-postgres mateclaw-local-sim-wiremock)
deadline=$((SECONDS + 120))
ready=0
while (( SECONDS < deadline )); do
  ready=1
  for container in "${containers[@]}"; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    [[ "$status" == "healthy" ]] || ready=0
  done
  if (( ready == 1 )); then break; fi
  sleep 2
done
if (( ready != 1 )); then
  echo "本地模拟服务在 120 秒内未全部 healthy" >&2
  "${COMPOSE[@]}" ps
  exit 1
fi
echo "本地模拟环境已启动：MySQL 13306、PostgreSQL 15432、WireMock 18081/18443。文件数据集样例保留在 files/，可由 DataAgent 本地存储根目录读取。"
