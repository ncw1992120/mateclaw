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

if ! grep -Fq 'launchctl bootout "$LAUNCHD_DOMAIN/$LAUNCH_AGENT_LABEL"' "$SCRIPT"; then
  echo "启动脚本必须 bootout 本机 DataAgent LaunchAgent，防止 KeepAlive 重启真实 profile。" >&2
  exit 1
fi

if ! grep -Fq 'trap restore_dataagent_launch_agent EXIT' "$SCRIPT" \
  || ! grep -Fq 'launchctl bootstrap "$LAUNCHD_DOMAIN" "$LAUNCH_AGENT_PLIST"' "$SCRIPT"; then
  echo "启动脚本退出时必须恢复此前暂停的 LaunchAgent。" >&2
  exit 1
fi

echo "DataAgent 重启脚本 LaunchAgent 接管与恢复检查通过。"
