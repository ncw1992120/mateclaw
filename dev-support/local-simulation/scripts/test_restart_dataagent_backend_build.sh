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

if ! grep -Fq 'CURRENT_BRANCH="$(git -C "$PROJECT_ROOT" branch --show-current)"' "$SCRIPT"; then
  echo "后端重启必须直接验证并使用当前 feature/dev_fu 工作区。" >&2
  exit 1
fi
if ! grep -Fq '"$PROJECT_ROOT/mateclaw-dataagent/pom.xml" clean package -DskipTests' "$SCRIPT"; then
  echo "Maven clean package 必须针对当前工作区执行。" >&2
  exit 1
fi
if ! grep -Fq 'git -C "$cwd" rev-parse --show-toplevel' "$SCRIPT"; then
	echo "只能重启当前本地仓库启动的 DataAgent 进程。" >&2
  exit 1
fi

if grep -Eq 'MATECLAW_RUNNER_URL|18090|uvicorn|runner\.app:app|restart_python_runner' "$SCRIPT"; then
  echo "DataAgent 本地启动链路不得配置或启动独立 Python Runner。" >&2
  exit 1
fi

if ! grep -Fq 'MATECLAW_PYTHON_WORKER_HOME=' "$SCRIPT"; then
  echo "本地启动必须将随 DataAgent 管理的 Python Worker 源码位置明确配置。" >&2
  exit 1
fi

if ! grep -Fq 'export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-true}"' "$SCRIPT"; then
  echo "本地启动默认必须启用 DataAgent Python Worker。" >&2
  exit 1
fi

if ! grep -Fq 'export MATECLAW_DATASET_READ_BASE_URL=' "$SCRIPT"; then
  echo "DataAgent 必须通过 application.yml 使用的变量配置 Worker 数据回读地址。" >&2
  exit 1
fi

if ! grep -Fq 'BACKEND_HEALTH_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"' "$SCRIPT"; then
  echo "DataAgent 启动健康检查必须与根 context-path 配置一致。" >&2
  exit 1
fi

if ! grep -Fq 'export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-true}"' "$SCRIPT"; then
  echo "本地重启默认必须启用 DataAgent Python Worker。" >&2
  exit 1
fi

if ! grep -Fq 'java -jar "$JAR_PATH"' "$SCRIPT"; then
  echo "DataAgent 必须以前台方式运行，使日志直接输出到当前终端。" >&2
  exit 1
fi

if ! grep -Fq 'DEPLOY_COMMIT="$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || true)"' "$SCRIPT"; then
  echo "部署提交号必须从当前工作区安全解析。" >&2
  exit 1
fi

if ! grep -Fq 'echo "本次部署源码：本地 feature/dev_fu 工作区（HEAD ${DEPLOY_COMMIT:-未知}' "$SCRIPT"; then
  echo "部署日志不得因 DEPLOY_COMMIT 未设置而在 set -u 下中断。" >&2
  exit 1
fi

echo "DataAgent 重启脚本 clean + package 顺序检查通过。"
