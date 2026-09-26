#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT_DIR/docs/策略解读/restart-dataagent-backend.sh"

suspend_line="$(grep -nF 'if ! suspend_dataagent_launch_agent; then' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
kill_line="$(grep -nF 'kill $own_old_pids' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$suspend_line" || -z "$kill_line" ]] || (( suspend_line >= kill_line )); then
  echo "停止旧 DataAgent 前必须先暂停受 KeepAlive 管理的 LaunchAgent。" >&2
  exit 1
fi

precheck_line="$(grep -nF 'if [[ "${DB_PRECHECK:-on}" != "skip" ]]' "$SCRIPT" | head -1 | cut -d: -f1 || true)"
if [[ -z "$precheck_line" ]] || (( suspend_line >= precheck_line )); then
  echo "mock 模式必须在数据库预检前暂停旧 LaunchAgent，防止预检失败时仍调用真实 Aloudata。" >&2
  exit 1
fi

if ! grep -Fq 'launchctl bootout "$LAUNCHD_DOMAIN/$LAUNCH_AGENT_LABEL"' "$SCRIPT"; then
  echo "启动脚本必须 bootout 本机 DataAgent LaunchAgent，防止 KeepAlive 重启真实 profile。" >&2
  exit 1
fi

if ! grep -Fq 'trap restore_dataagent_launch_agent EXIT' "$SCRIPT" \
  || ! grep -Fq 'launchctl bootstrap "$LAUNCHD_DOMAIN" "$LAUNCH_AGENT_PLIST"' "$SCRIPT"; then
  echo "启动脚本退出时必须恢复此前暂停的 LaunchAgent。" >&2
  exit 1
fi

if ! grep -Fq 'if [[ "$MOCK_MODE" == "1" ]]; then' "$SCRIPT" \
  || ! grep -Fq '保持 LaunchAgent 停止，避免回退到真实 Aloudata' "$SCRIPT"; then
  echo "mock 启动失败或停止时不得恢复真实 Aloudata 的 LaunchAgent。" >&2
  exit 1
fi

if ! grep -Fq -- '--spring.profiles.active="$SPRING_PROFILES_ACTIVE"' "$SCRIPT"; then
  echo "后端启动命令必须显式传入最终 profile，确保 local-mock 生效。" >&2
  exit 1
fi

restore_function="$(sed -n '/^restore_dataagent_launch_agent()/,/^}/p' "$SCRIPT")"
mock_restore_output="$(
  RESTORE_FUNCTION="$restore_function" LAUNCH_AGENT_SUSPENDED=1 MOCK_MODE=1 LAUNCHD_DOMAIN=gui/test \
    LAUNCH_AGENT_PLIST=/tmp/test.plist LAUNCH_AGENT_LABEL=test-agent \
    bash -c 'launchctl() { printf "CALL:%s\\n" "$*"; }; trap() { :; }; eval "$RESTORE_FUNCTION"; restore_dataagent_launch_agent'
)"
if grep -Fq 'CALL:bootstrap' <<< "$mock_restore_output"; then
  echo "mock 模式退出后不得重新加载真实 profile 的 LaunchAgent。" >&2
  exit 1
fi

real_restore_output="$(
  RESTORE_FUNCTION="$restore_function" LAUNCH_AGENT_SUSPENDED=1 MOCK_MODE=0 LAUNCHD_DOMAIN=gui/test \
    LAUNCH_AGENT_PLIST=/tmp/test.plist LAUNCH_AGENT_LABEL=test-agent \
    bash -c 'launchctl() { printf "CALL:%s\\n" "$*"; }; trap() { :; }; eval "$RESTORE_FUNCTION"; restore_dataagent_launch_agent'
)"
if ! grep -Fq 'CALL:bootstrap gui/test /tmp/test.plist' <<< "$real_restore_output"; then
  echo "非 mock 模式退出后应保留 LaunchAgent 恢复行为。" >&2
  exit 1
fi

echo "DataAgent 重启脚本 LaunchAgent 接管与恢复检查通过。"
