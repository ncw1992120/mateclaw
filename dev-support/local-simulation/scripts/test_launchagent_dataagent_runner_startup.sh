#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT_DIR/dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "LaunchAgent 启动器必须存在。" >&2
  exit 1
fi

runner_line="$(grep -nF 'bash "$RUNNER_RESTART_SCRIPT" runner' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
java_line="$(grep -nF 'exec "$JAVA_BIN" -jar "$JAR_PATH"' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$runner_line" || -z "$java_line" ]] || (( runner_line >= java_line )); then
  echo "LaunchAgent 必须先确保 Python Runner 就绪，再 exec DataAgent。" >&2
  exit 1
fi

if ! grep -Fq 'MATECLAW_RUNNER_URL="${MATECLAW_RUNNER_URL:-http://127.0.0.1:18090}"' "$SCRIPT"; then
  echo "LaunchAgent 必须将 Python Runner 指向本机 18090。" >&2
  exit 1
fi

if ! grep -Fq 'MATECLAW_DATASET_READ_BASE_URL="${MATECLAW_DATASET_READ_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}/dataagent/api}"' "$SCRIPT"; then
  echo "LaunchAgent 必须配置 Runner 回读数据的本机 DataAgent 地址。" >&2
  exit 1
fi

if grep -Eq 'mvn |clean package|docker compose.*build' "$SCRIPT"; then
  echo "LaunchAgent 启动器不得 rebuild。" >&2
  exit 1
fi

echo "LaunchAgent Python Runner 启动顺序与本机地址检查通过。"
