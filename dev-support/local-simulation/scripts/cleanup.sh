#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.local"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "未找到 ${ENV_FILE}，无需清理。"
  exit 0
fi

docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "$ENV_FILE" down -v --remove-orphans
echo "本地模拟容器、网络和数据卷已清理。"
