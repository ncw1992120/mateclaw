#!/usr/bin/env bash
#
# DataAgent 后端 + Aloudata 本地 mock 服务 一键重启
#
# 用法：
#   ./restart-dataagent-backend.sh                 # 重启 mock 服务（默认）+ 后端
#   ./restart-dataagent-backend.sh mock            # 只重启本地 mock 服务（不动后端）
#   ./restart-dataagent-backend.sh stop-mock       # 只停止本地 mock 服务
#   ./restart-dataagent-backend.sh help            # 查看用法
#
# 环境变量：
#   ALOUDATA_MOCK=on|embed|off      上游模式：本地 HTTP mock（默认）/ 内置夹具 / 真实 Aloudata
#   ALOUDATA_MOCK_PORT=18081        mock 服务端口
#   ALOUDATA_MOCK_SERVER=...        mock 服务基地址（默认 http://127.0.0.1:<port>）
#   ALOUDATA_MOCK_RESTART=always|keep
#                                   always（默认）每次重启都停旧 mock 再起新的；
#                                   keep 仅在未监听时拉起（保留手工起的 mock 进程）
#   ALOUDATA_MOCK_SERVER=...        （embed / off 模式下忽略）
#   DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD  后端数据库连接
#   SPRING_PROFILES_ACTIVE=...      显式覆盖整组 profile（优先级最高）

set -Eeuo pipefail

PROJECT_ROOT="/Users/srant/IdeaProjects/codex/mateclaw-1"
JAR_PATH="$PROJECT_ROOT/mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar"
BACKEND_PORT="18089"

MOCK_SERVER_SCRIPT="$PROJECT_ROOT/dev-support/local-simulation/scripts/aloudata-mock-server.py"
MOCK_PORT="${ALOUDATA_MOCK_PORT:-18081}"
MOCK_URL="${ALOUDATA_MOCK_SERVER:-http://127.0.0.1:${MOCK_PORT}}"
MOCK_LOG="/tmp/aloudata-mock-server-${MOCK_PORT}.log"
MOCK_PID_FILE="/tmp/aloudata-mock-server-${MOCK_PORT}.pid"

ACTION="${1:-all}"

usage() {
  cat <<'TXT'
用法：
  ./restart-dataagent-backend.sh                 # 重启 mock 服务（默认）+ 后端
  ./restart-dataagent-backend.sh mock            # 只重启本地 mock 服务（不动后端）
  ./restart-dataagent-backend.sh stop-mock       # 只停止本地 mock 服务
  ./restart-dataagent-backend.sh help            # 查看用法

环境变量：
  ALOUDATA_MOCK=on|embed|off      上游模式：本地 HTTP mock（默认）/ 内置夹具 / 真实 Aloudata
  ALOUDATA_MOCK_PORT=18081        mock 服务端口
  ALOUDATA_MOCK_SERVER=...        mock 服务基地址（默认 http://127.0.0.1:<port>）
  ALOUDATA_MOCK_RESTART=always|keep
                                  always（默认）每次重启都停旧 mock 再起新的；
                                  keep 仅在未监听时拉起（保留手工起的 mock 进程）
  ALOUDATA_MOCK_FORCE_KILL=1      端口被非脚本进程（PID 文件丢失/手工起的 mock）占用时强制接管
  DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD  后端数据库连接
  SPRING_PROFILES_ACTIVE=...      显式覆盖整组 profile（优先级最高）
TXT
}

# ---------------------------------------------------------------- mock 服务

mock_listening() {
  lsof -tiTCP:"$MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1
}

# 只有确认监听进程就是我们这个 mock 脚本时才回显 PID，避免误杀占用同端口的其它服务。
# 判据两条：① 与本脚本启动时写的 PID 文件一致；② 命令行含 aloudata-mock-server.py。
# （受限环境里 ps 可能看不到其它进程，所以 PID 文件是主判据。）
mock_own_pids() {
  local pid pid_file_pid=""
  [[ -f "${MOCK_PID_FILE}" ]] && pid_file_pid="$(cat "${MOCK_PID_FILE}" 2>/dev/null || true)"
  for pid in $(lsof -tiTCP:"${MOCK_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
    if [[ -n "${pid_file_pid}" && "${pid}" == "${pid_file_pid}" ]]; then
      echo "${pid}"
      continue
    fi
    # -ww 必须带：macOS 下 ps 默认会截断 command 列，导致匹配不到完整脚本名
    if ps -p "${pid}" -ww -o command= 2>/dev/null | grep -q "aloudata-mock-server.py"; then
      echo "${pid}"
    fi
  done
}

stop_mock_server() {
  if ! mock_listening; then
    echo "本地 mock 服务未在 $MOCK_PORT 监听，无需停止。"
    rm -f "$MOCK_PID_FILE"
    return 0
  fi
  local listen_pids own_pids
  listen_pids="$(lsof -tiTCP:"$MOCK_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  own_pids="$(mock_own_pids || true)"
  # 显式强制：PID 文件丢失或手工起的 mock 服务（ps 不可见时）用它接管端口上的进程
  if [[ "${ALOUDATA_MOCK_FORCE_KILL:-0}" == "1" ]]; then
    echo "ALOUDATA_MOCK_FORCE_KILL=1：强制接管端口 $MOCK_PORT 上的进程。"
    own_pids="$listen_pids"
  fi
  # 监听进程数 > 我们自己的进程数 → 端口上还有别的服务，不动手
  if [[ -z "$own_pids" \
        || "$(printf '%s\n' "$listen_pids" | grep -c . || true)" != "$(printf '%s\n' "$own_pids" | grep -c . || true)" ]]; then
    echo "警告：端口 $MOCK_PORT 被非 mock 脚本进程占用，未执行 kill（请手工确认）。" >&2
    echo "      确认要接管时可执行：ALOUDATA_MOCK_FORCE_KILL=1 ./restart-dataagent-backend.sh mock" >&2
    lsof -nP -iTCP:"$MOCK_PORT" -sTCP:LISTEN >&2 || true
    return 1
  fi
  echo "停止本地 mock 服务（PID $(echo $own_pids | tr '\n' ' ')）..."
  # shellcheck disable=SC2086
  kill $own_pids 2>/dev/null || true
  for _ in {1..10}; do
    mock_listening || { rm -f "$MOCK_PID_FILE"; echo "本地 mock 服务已停止。"; return 0; }
    sleep 0.5
  done
  echo "警告：mock 服务 5 秒内未退出，强制结束。" >&2
  # shellcheck disable=SC2086
  kill -9 $own_pids 2>/dev/null || true
  rm -f "$MOCK_PID_FILE"
  return 0
}

start_mock_server() {
  if [[ ! -f "$MOCK_SERVER_SCRIPT" ]]; then
    echo "警告：未找到 mock 服务脚本 $MOCK_SERVER_SCRIPT" >&2
    return 1
  fi
  if [[ -z "$(command -v python3 || true)" ]]; then
    echo "警告：未找到 python3，无法启动 mock 服务。" >&2
    return 1
  fi
  echo "启动本地 Aloudata mock 服务：$MOCK_URL"
  nohup python3 "$MOCK_SERVER_SCRIPT" --port "$MOCK_PORT" >"$MOCK_LOG" 2>&1 &
  local pid=$!
  echo "$pid" >"$MOCK_PID_FILE"
  # 脱离当前 shell，降低终端退出把 mock 服务带走的概率
  disown 2>/dev/null || true
  for _ in {1..20}; do
    if mock_listening; then
      echo "本地 mock 服务已就绪（PID ${pid}，日志 ${MOCK_LOG}）。"
      return 0
    fi
    sleep 0.5
  done
  echo "警告：mock 服务未能在 $MOCK_PORT 监听，请查看 $MOCK_LOG" >&2
  return 1
}

restart_mock_server() {
  stop_mock_server || true
  start_mock_server
}

# 「只重启 mock」的入口：不需要后端 JAR，也不改 profile
case "$ACTION" in
  mock|restart-mock)
    restart_mock_server || exit 1
    echo "提示：后端进程未重启；若后端是以 ALOUDATA_MOCK=on 启动的，本次重启后即刻生效。"
    exit 0
    ;;
  stop-mock)
    stop_mock_server || exit 1
    exit 0
    ;;
  help|-h|--help)
    usage
    exit 0
    ;;
  all|"") ;;
  *)
    echo "未知参数：$ACTION" >&2
    usage >&2
    exit 1
    ;;
esac

# ---------------------------------------------------------------- 后端

export JAVA_HOME="${JAVA_HOME:-/Users/srant/.jdks/jdk-21.0.12+8/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "错误：找不到 JDK 21：$JAVA_HOME" >&2
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "错误：找不到后端 JAR：$JAR_PATH" >&2
  echo "请先执行后端构建命令。" >&2
  exit 1
fi

# Aloudata 上游临时指向本地 mock（docs/策略解读/mock.md）：
#   默认开启 local-mock —— 后端仍按真实端点声明构建请求（路径/参数/请求方式/校验全一致），
#   只把已构建好的请求发到本地 mock 服务（默认 127.0.0.1:18081），即**只有 ip:port 不同**。
#   重启时默认会**先停旧 mock 服务再起新的**（ALOUDATA_MOCK_RESTART=keep 可改为只在未监听时拉起）。
#   要退回内置夹具（不起 HTTP 服务）：ALOUDATA_MOCK=embed ./restart-dataagent-backend.sh
#   要切回真实上游：ALOUDATA_MOCK=off ./restart-dataagent-backend.sh
#   （也可用 SPRING_PROFILES_ACTIVE=pgsql 显式覆盖整组 profile）
MOCK_SWITCH="${ALOUDATA_MOCK:-on}"
case "$MOCK_SWITCH" in
  on|ON|true|TRUE|1)
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql,local-mock}"
    export ALOUDATA_MOCK_PORT="$MOCK_PORT"
    export ALOUDATA_MOCK_SERVER="$MOCK_URL"
    MOCK_RESTART_MODE="${ALOUDATA_MOCK_RESTART:-always}"
    mock_ready=0
    case "$MOCK_RESTART_MODE" in
      keep|KEEP)
        if mock_listening; then
          echo "本地 mock 服务已在 $MOCK_PORT 监听（ALOUDATA_MOCK_RESTART=keep，保留现有进程）。"
          mock_ready=1
        elif start_mock_server; then
          mock_ready=1
        fi
        ;;
      *)
        if restart_mock_server; then
          mock_ready=1
        fi
        ;;
    esac
    if [[ "$mock_ready" -eq 0 ]]; then
      echo "警告：本地 mock 服务不可用，已退回内置夹具（请求方式/参数仍走真实构建逻辑，但不再发 HTTP）。" >&2
      echo "      排查：cat ${MOCK_LOG}；或 ./restart-dataagent-backend.sh mock 单独重启 mock 服务。" >&2
      unset ALOUDATA_MOCK_SERVER
    fi
    if [[ -n "${ALOUDATA_MOCK_SERVER:-}" ]]; then
      echo "★ Aloudata 上游 = 本地 mock 服务 ${ALOUDATA_MOCK_SERVER} （请求方式/路径/参数与正式一致，只有 ip:port 不同）。"
      echo "  单独重启 mock：./restart-dataagent-backend.sh mock"
      echo "  停止 mock：    ./restart-dataagent-backend.sh stop-mock"
      echo "  日志：$MOCK_LOG"
      echo "  若接口报 503「上游服务不可达」= mock 服务已退出，重跑本脚本或执行上面的 mock 子命令即可恢复。"
    else
      echo "★ Aloudata 上游 = 内置夹具（不发起 HTTP，参数/请求方式仍走真实构建逻辑）。"
    fi
    echo "  切回真实上游：ALOUDATA_MOCK=off 重启；切内置夹具：ALOUDATA_MOCK=embed 重启。"
    ;;
  embed|EMBED|embed-on|2)
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql,local-mock}"
    unset ALOUDATA_MOCK_SERVER
    echo "★ Aloudata 上游 = 内置夹具（不发起 HTTP，参数/请求方式仍走真实构建逻辑）。切回真实上游：ALOUDATA_MOCK=off 重启。"
    ;;
  *)
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql}"
    unset ALOUDATA_MOCK_SERVER
    echo "Aloudata 上游 = 真实环境（未启用 local-mock）。"
    ;;
esac

export DB_HOST="${DB_HOST:-14.22.85.76}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-testdb}"
export DB_USERNAME="${DB_USERNAME:-testdb}"
export DB_PASSWORD="${DB_PASSWORD:-testdb}"

# 洞察/仪表盘当前不依赖 Elasticsearch。
export ES_URIS="${ES_URIS:-http://127.0.0.1:9200}"
export MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED="${MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED:-false}"

# 本地测试不启用领航认证和 Python 执行器。
export MATECLAW_PILOT_ENABLED="${MATECLAW_PILOT_ENABLED:-false}"
export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-false}"

# 没有配置 DashScope 时，使用占位值避免 Spring AI 语音组件阻塞启动。
export SPRING_AI_DASHSCOPE_API_KEY="${SPRING_AI_DASHSCOPE_API_KEY:-configure-in-admin-ui}"
export SPRING_AI_DASHSCOPE_AUDIO_SPEECH_API_KEY="${SPRING_AI_DASHSCOPE_AUDIO_SPEECH_API_KEY:-configure-in-admin-ui}"
export SPRING_AI_DASHSCOPE_AUDIO_TRANSCRIPTION_API_KEY="${SPRING_AI_DASHSCOPE_AUDIO_TRANSCRIPTION_API_KEY:-configure-in-admin-ui}"

# Route A（直连）：无论调用方环境是否带 SOCKS/HTTP 代理，启动时一律剥离，
# 避免 JVM 的 JAVA_TOOL_OPTIONS / JDK_JAVA_OPTIONS / _JAVA_OPTIONS 把数据库连接劫持到死代理。
strip_proxy_opts() {
  local v="$1"
  echo "$v" \
    | sed -E 's/[[:space:]]*-D(socksProxyHost|socksProxyPort|socksNonProxyHosts|http\.proxyHost|http\.proxyPort|https\.proxyHost|https\.proxyPort|http\.nonProxyHosts|ftp\.proxyHost|ftp\.proxyPort)=[^[:space:]]*//g' \
    | sed -E 's/[[:space:]]+/ /g' \
    | sed -E 's/^ | $//g'
}
export JAVA_TOOL_OPTIONS="$(strip_proxy_opts "${JAVA_TOOL_OPTIONS:--Xms256m -Xmx1g -Duser.timezone=Asia/Shanghai}")"
export JDK_JAVA_OPTIONS="$(strip_proxy_opts "${JDK_JAVA_OPTIONS:-}")"
export _JAVA_OPTIONS="$(strip_proxy_opts "${_JAVA_OPTIONS:-}")"
unset MAVEN_OPTS
unset JAVA_OPTS

echo "停止旧的 DataAgent 后端进程..."
old_pids="$(lsof -tiTCP:"$BACKEND_PORT" -sTCP:LISTEN 2>/dev/null || true)"
if [[ -n "$old_pids" ]]; then
  # shellcheck disable=SC2086
  kill $old_pids
  for _ in {1..10}; do
    if ! lsof -tiTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
fi

if lsof -tiTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "错误：端口 $BACKEND_PORT 仍被占用，未启动新后端。" >&2
  exit 1
fi

echo "使用 Java：$(java -version 2>&1 | head -1)"
echo "启动 DataAgent 后端，数据库：$DB_HOST:$DB_PORT/$DB_NAME"
echo "健康检查地址：http://127.0.0.1:$BACKEND_PORT/dataagent/api/actuator/health"

cd "$PROJECT_ROOT"
# 显式锁定 HTTP 端口（命令行参数优先级最高，覆盖任何把 server.port 设成 0/随机的来源）。
exec java -jar "$JAR_PATH" --server.port="$BACKEND_PORT"
