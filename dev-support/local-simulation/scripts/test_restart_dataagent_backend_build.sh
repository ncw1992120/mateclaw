#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)/docs/策略解读/restart-dataagent-backend.sh"

build_line="$(grep -nF 'clean package -DskipTests' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
jar_check_line="$(grep -nF 'if [[ ! -f "$JAR_PATH" ]]' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
launch_line="$(grep -nF 'java -jar "$JAR_PATH"' "$SCRIPT" | head -1 | cut -d: -f1 || true)"

if [[ -z "$build_line" || -z "$jar_check_line" || -z "$launch_line" ]]; then
  echo "启动脚本必须 clean + package DataAgent，并检查、运行新生成的 JAR。" >&2
  exit 1
fi

if (( build_line >= jar_check_line || build_line >= launch_line )); then
  echo "DataAgent clean + package 必须在检查/运行 JAR 之前执行。" >&2
  exit 1
fi

echo "DataAgent 重启脚本 clean + package 顺序检查通过。"
