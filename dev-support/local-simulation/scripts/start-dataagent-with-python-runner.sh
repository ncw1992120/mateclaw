#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
JAR_PATH="$PROJECT_ROOT/mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar"
BACKEND_PORT="${SERVER_PORT:-18089}"

export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-true}"
export PYTHON_COMMAND="${PYTHON_COMMAND:-$PROJECT_ROOT/mateclaw-python-runner/.venv/bin/python}"
export MATECLAW_PYTHON_WORKER_HOME="${MATECLAW_PYTHON_WORKER_HOME:-$PROJECT_ROOT/mateclaw-python-runner}"
export MATECLAW_DATASET_READ_BASE_URL="${MATECLAW_DATASET_READ_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "错误：未找到已构建的 DataAgent JAR：$JAR_PATH（此启动器不会执行 rebuild）。" >&2
  exit 1
fi

if [[ ! -x "$PYTHON_COMMAND" ]]; then
  echo "错误：找不到 Python Worker 解释器：$PYTHON_COMMAND（请设置 PYTHON_COMMAND）" >&2
  exit 1
fi
if [[ ! -f "$MATECLAW_PYTHON_WORKER_HOME/src/runner/worker.py" ]]; then
  echo "错误：未找到 Python Worker 源码：$MATECLAW_PYTHON_WORKER_HOME/src/runner/worker.py" >&2
  exit 1
fi

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="$(command -v java || true)"
fi
if [[ -z "$JAVA_BIN" || ! -x "$JAVA_BIN" ]]; then
  echo "错误：找不到 Java 可执行文件，请检查 JAVA_HOME 或 PATH。" >&2
  exit 1
fi

echo "启动 DataAgent（不 rebuild）：$JAR_PATH"
exec "$JAVA_BIN" -jar "$JAR_PATH" --server.port="$BACKEND_PORT"
