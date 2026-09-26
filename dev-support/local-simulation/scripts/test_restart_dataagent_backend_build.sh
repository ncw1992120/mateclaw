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

runner_restart_line="$(grep -nFx 'restart_python_runner || exit 1' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
runner_url_line="$(grep -nF 'MATECLAW_RUNNER_URL' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$runner_restart_line" || -z "$runner_url_line" ]]; then
  echo "启动脚本必须配置并重启本地 Python Runner。" >&2
  exit 1
fi

if (( runner_restart_line <= jar_check_line || runner_restart_line >= launch_line )); then
  echo "Python Runner 必须在 DataAgent 构建成功后、启动前重启。" >&2
  exit 1
fi

if ! grep -Fq 'uvicorn' "$SCRIPT" || ! grep -Fq '18090' "$SCRIPT"; then
  echo "Python Runner 应复用本地环境并监听配置的端口。" >&2
  exit 1
fi

if grep -Eq 'docker compose.*(build|up --build)' "$SCRIPT"; then
  echo "本地 Python Runner 重启不得触发 Docker rebuild。" >&2
  exit 1
fi

if ! grep -Fq 'export MATECLAW_DATASET_READ_BASE_URL=' "$SCRIPT"; then
  echo "DataAgent 必须通过 application.yml 使用的变量配置 Runner 数据回读地址。" >&2
  exit 1
fi

echo "DataAgent 重启脚本 clean + package 顺序检查通过。"
