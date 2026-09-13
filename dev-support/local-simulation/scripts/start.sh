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

# `docker compose up --wait` also observes one-shot TLS/MinIO init services and
# may report their expected exit(0) as a failed wait. Poll only long-running
# services with healthchecks before starting the fixture seed.
containers=(mateclaw-local-sim-mysql mateclaw-local-sim-postgres mateclaw-local-sim-minio mateclaw-local-sim-wiremock mateclaw-local-sim-python-runner)
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
"${COMPOSE[@]}" --profile seed run --rm minio-seed
echo "本地模拟环境已启动：MySQL 13306、PostgreSQL 15432、MinIO 19000/19001、WireMock 18081/18443。"
