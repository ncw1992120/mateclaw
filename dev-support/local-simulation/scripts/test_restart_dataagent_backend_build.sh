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
if ! grep -Fq 'mateclaw_same_git_repository "$PROJECT_ROOT" "$cwd"' "$SCRIPT"; then
  echo "只能重启来自同一 Git 仓库其他工作树的 DataAgent 进程。" >&2
  exit 1
fi

runner_restart_line="$(grep -nFx '  restart_python_runner || exit 1' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
runner_url_line="$(grep -nF 'MATECLAW_RUNNER_URL' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$runner_restart_line" || -z "$runner_url_line" ]]; then
  echo "启动脚本必须配置并重启本地 Python Runner。" >&2
  exit 1
fi

if ! grep -Fq 'if python_runner_enabled; then' "$SCRIPT"; then
  echo "Python Runner 未启用时，启动脚本不得无条件重启 Runner。" >&2
  exit 1
fi

runner_enabled_helper="$(sed -n '/^python_runner_enabled()/,/^}/p' "$SCRIPT")"
disabled_result="$(PYTHON_EXECUTOR_ENABLED=false bash -c "$runner_enabled_helper
if python_runner_enabled; then echo enabled; else echo disabled; fi")"
enabled_result="$(PYTHON_EXECUTOR_ENABLED=true bash -c "$runner_enabled_helper
if python_runner_enabled; then echo enabled; else echo disabled; fi")"
if [[ "$disabled_result" != "disabled" || "$enabled_result" != "enabled" ]]; then
  echo "Python Runner 启动条件没有正确遵循 PYTHON_EXECUTOR_ENABLED。" >&2
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

if ! grep -Fq 'BACKEND_HEALTH_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"' "$SCRIPT"; then
  echo "DataAgent 启动健康检查必须与根 context-path 配置一致。" >&2
  exit 1
fi

if ! grep -Fq 'export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-true}"' "$SCRIPT"; then
  echo "本地重启默认必须启用 Python Executor，以便启动 Python Runner。" >&2
  exit 1
fi

if ! grep -Fq 'tail -n +1 -F "$PYTHON_RUNNER_LOG"' "$SCRIPT"; then
  echo "Python Runner 日志必须实时输出到当前终端。" >&2
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
