#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)/docs/策略解读/restart-dataagent-backend.sh"

if ! grep -Fqx 'MOCK_SWITCH="${ALOUDATA_MOCK:-on}"' "$SCRIPT"; then
  echo "重启脚本默认必须使用本地 Aloudata HTTP mock（ALOUDATA_MOCK 默认 on）。" >&2
  exit 1
fi

if ! grep -Fq 'on|ON|true|TRUE|1)' "$SCRIPT" \
  || ! grep -Fq 'export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql,local-mock}"' "$SCRIPT"; then
  echo "显式开启 ALOUDATA_MOCK=on 时必须仍可启用 local-mock。" >&2
  exit 1
fi

if ! grep -Fq 'embed|EMBED|embed-on|2)' "$SCRIPT" \
  || ! grep -Fq 'export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql,local-mock}"' "$SCRIPT"; then
  echo "显式开启 ALOUDATA_MOCK=embed 时必须仍可使用内置夹具。" >&2
  exit 1
fi

if ! grep -Fq 'ensure_profile_active local-mock' "$SCRIPT"; then
  echo "on/embed 模式必须在用户显式传入 pgsql 等 profile 时仍强制启用 local-mock。" >&2
  exit 1
fi

profile_helpers="$(sed -n '/^ensure_profile_active()/,/^}/p; /^ensure_profile_inactive()/,/^}/p' "$SCRIPT")"
active_profile="$(SPRING_PROFILES_ACTIVE=pgsql bash -c "$profile_helpers
ensure_profile_active local-mock
printf '%s' \"\$SPRING_PROFILES_ACTIVE\"")"
if [[ "$active_profile" != "pgsql,local-mock" ]]; then
  echo "已有 pgsql profile 时应追加 local-mock，实际为：$active_profile" >&2
  exit 1
fi

real_profile="$(SPRING_PROFILES_ACTIVE=pgsql,local-mock bash -c "$profile_helpers
ensure_profile_inactive local-mock
printf '%s' \"\$SPRING_PROFILES_ACTIVE\"")"
if [[ "$real_profile" != "pgsql" ]]; then
  echo "真实模式必须移除遗留 local-mock profile，实际为：$real_profile" >&2
  exit 1
fi

echo "Aloudata 默认本地 HTTP mock 与显式模式开关检查通过。"
