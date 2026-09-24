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

echo "Aloudata 默认本地 HTTP mock 与显式模式开关检查通过。"
