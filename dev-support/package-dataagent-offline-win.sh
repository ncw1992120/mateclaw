#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="${DATAAGENT_JAR:-$ROOT_DIR/mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar}"
OUTPUT_PATH="${DATAAGENT_OFFLINE_PACKAGE_OUTPUT:-$ROOT_DIR/mateclaw-dataagent/target/dataagent-offline-win-x64.zip}"
UV_CMD="${UV_CMD:-uv}"
PYTHON_CMD="${PYTHON_COMMAND:-python3}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "未找到 DataAgent JAR：$JAR_PATH（请先构建 DataAgent）" >&2
  exit 1
fi
command -v "$UV_CMD" >/dev/null || { echo "未找到 uv：$UV_CMD" >&2; exit 1; }
command -v "$PYTHON_CMD" >/dev/null || { echo "未找到 Python：$PYTHON_CMD" >&2; exit 1; }
command -v zip >/dev/null || { echo "未找到 zip 命令" >&2; exit 1; }

PACKAGE_TMP="$(mktemp -d "${TMPDIR:-/tmp}/dataagent-offline-win.XXXXXX")"
trap 'rm -rf "$PACKAGE_TMP"' EXIT
PACKAGE_ROOT="$PACKAGE_TMP/DataAgent"
WORKER_ROOT="$PACKAGE_ROOT/python-worker"
WORKER_PROJECT="$ROOT_DIR/mateclaw-dataagent/python-worker"
WHEELHOUSE="$WORKER_ROOT/wheelhouse"
mkdir -p "$WHEELHOUSE" "$(dirname "$OUTPUT_PATH")"
cp "$JAR_PATH" "$PACKAGE_ROOT/mateclaw-dataagent-1.0.0-SNAPSHOT.jar"
cp -R "$WORKER_PROJECT/src" "$WORKER_ROOT/src"

"$UV_CMD" export --locked --offline --project "$WORKER_PROJECT" \
  --format requirements.txt --no-dev --no-emit-project --no-header \
  --output-file "$WORKER_ROOT/requirements.txt"
"$PYTHON_CMD" -m pip download \
  --requirement "$WORKER_ROOT/requirements.txt" \
  --dest "$WHEELHOUSE" \
  --only-binary=:all: \
  --platform win_amd64 \
  --python-version 3.12 \
  --implementation cp \
  --abi cp312

(
  cd "$PACKAGE_TMP"
  zip -qr "$OUTPUT_PATH" DataAgent
)
unzip -t "$OUTPUT_PATH" >/dev/null
echo "Windows x64 Python 3.12 离线包已生成：$OUTPUT_PATH"
