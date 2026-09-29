#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT_DIR/dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "LaunchAgent 启动器必须存在。" >&2
  exit 1
fi

java_line="$(grep -nF 'exec "$JAVA_BIN" -jar "$JAR_PATH"' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$java_line" ]]; then
  echo "LaunchAgent 启动器必须以前台 exec 方式运行 DataAgent。" >&2
  exit 1
fi

if grep -Eq 'MATECLAW_RUNNER_URL|18090|mateclaw-local-sim-python-runner|runner\.app:app|RUNNER_RESTART_SCRIPT' "$SCRIPT"; then
  echo "LaunchAgent 不得依赖独立 Python Runner 服务或端口。" >&2
  exit 1
fi

if ! grep -Fq 'MATECLAW_PYTHON_WORKER_HOME=' "$SCRIPT"; then
  echo "LaunchAgent 必须配置随 DataAgent 一起运行的本地 Python Worker 目录。" >&2
  exit 1
fi

if ! grep -Fq '$PROJECT_ROOT/mateclaw-dataagent/python-worker' "$SCRIPT"; then
  echo "LaunchAgent 必须将 Worker 路径指向 DataAgent 模块内部。" >&2
  exit 1
fi

if grep -Fq 'mateclaw-python-runner' "$SCRIPT"; then
  echo "LaunchAgent 不得再依赖已删除的 Python Runner 源码目录。" >&2
  exit 1
fi

if ! grep -Fq 'MATECLAW_DATASET_READ_BASE_URL="${MATECLAW_DATASET_READ_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"' "$SCRIPT"; then
  echo "LaunchAgent 必须配置 Runner 回读数据的本机 DataAgent 地址。" >&2
  exit 1
fi

if grep -Eq 'mvn |clean package|docker compose.*build' "$SCRIPT"; then
  echo "LaunchAgent 启动器不得 rebuild。" >&2
  exit 1
fi

echo "LaunchAgent 本地 Worker 配置与单 DataAgent 启动检查通过。"
