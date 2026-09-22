#!/usr/bin/env bash
#
# DataAgent 后端 + Aloudata 本地 mock 服务 一键重启
#
# 用法：
#   ./restart-dataagent-backend.sh                 # 重启 mock 服务（默认）+ 后端
#   bash restart-dataagent-backend.sh mock         # 只重启本地 mock 服务（不动后端）
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
#   DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD  开发环境后端数据库连接
#   SPRING_PROFILES_ACTIVE=...      显式覆盖整组 profile（优先级最高）

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
JAR_PATH="$PROJECT_ROOT/mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar"
BACKEND_PORT="18089"
BACKEND_HEALTH_URL="http://127.0.0.1:${BACKEND_PORT}/dataagent/api/actuator/health"

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
  bash restart-dataagent-backend.sh mock         # 只重启本地 mock 服务（不动后端）
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
  DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD  开发环境后端数据库连接
  DB_PRECHECK=on|skip             启动前数据库连通性预检（默认 on，连不上直接报错退出）
  SPRING_PROFILES_ACTIVE=...      显式覆盖整组 profile（优先级最高）
TXT
}

# ---------------------------------------------------------------- mock 服务

mock_listening() {
  lsof -tiTCP:"$MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1
}

# 只有确认命令行确实是本项目 mock 脚本时才回显 PID，避免 PID 文件过期或复用导致误杀其它服务。
mock_own_pids() {
  local pid command
  for pid in $(lsof -tiTCP:"${MOCK_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
    # -ww 必须带：macOS 下 ps 默认会截断 command 列，导致匹配不到完整脚本名
    command="$(ps -p "${pid}" -ww -o command= 2>/dev/null || true)"
    if [[ "$command" == *"aloudata-mock-server.py"* ]]; then
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

java_major_version() {
  local java_home="$1" version major
  version="$("$java_home/bin/java" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')"
  major="${version%%.*}"
  if [[ "$major" == "1" ]]; then
    version="${version#1.}"
    major="${version%%.*}"
  fi
  printf '%s' "$major"
}

java_home_is_17_or_newer() {
  local java_home="$1" major
  [[ -x "$java_home/bin/java" ]] || return 1
  major="$(java_major_version "$java_home")"
  [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 17 ))
}

JAVA_HOME="${JAVA_HOME:-}"
if ! java_home_is_17_or_newer "$JAVA_HOME"; then
  JAVA_HOME=""
fi
if [[ -z "$JAVA_HOME" && -x /usr/libexec/java_home ]]; then
  candidate_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if java_home_is_17_or_newer "$candidate_java_home"; then
    JAVA_HOME="$candidate_java_home"
  fi
fi
if [[ -z "$JAVA_HOME" ]]; then
  for candidate_java_home in \
    "$HOME"/.jdks/*/Contents/Home \
    "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
    /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    if java_home_is_17_or_newer "$candidate_java_home"; then
      JAVA_HOME="$candidate_java_home"
      break
    fi
  done
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if ! java_home_is_17_or_newer "$JAVA_HOME"; then
  echo "错误：找不到 Java 17+，请设置 JAVA_HOME。当前值：${JAVA_HOME:-未设置}" >&2
  echo "当前系统 java：$(command -v java || echo 未找到)" >&2
  java -version 2>&1 || true
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

# ---------------------------------------------------------------- DB 连通性预检
# 后端启动后会立刻查库（Security 加载用户），网络不可用时不会马上失败，
# 而是卡满 Hikari 的 30s connectionTimeout，再抛
#   SQLTransientConnectionException: Connection is not available, request timed out after 30005ms
#   Caused by: PSQLException: 尝试连线已失败。
# 与其干等超时后看一堆堆栈猜原因，不如启动前先探一次数据库。
# 逃生开关：DB_PRECHECK=skip ./restart-dataagent-backend.sh
db_port_reachable() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    # macOS 的 nc 用 -G（超时），GNU netcat 用 -w
    nc -z -G 5 "$host" "$port" >/dev/null 2>&1 && return 0
    nc -z -w 5 "$host" "$port" >/dev/null 2>&1 && return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$host" "$port" <<'PY' >/dev/null 2>&1
import socket, sys
s = socket.create_connection((sys.argv[1], int(sys.argv[2])), timeout=5)
s.close()
PY
    return $?
  fi
  # 没有任何探测工具，不拦启动
  return 0
}

db_ready() {
  db_port_reachable "$DB_HOST" "$DB_PORT" || return 1
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$DB_PASSWORD" psql -X -w \
      -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
      -c 'select 1' >/dev/null 2>&1
  else
    echo "提示：未找到 psql，仅完成数据库 TCP 端口检查。" >&2
    return 0
  fi
}

if [[ "${DB_PRECHECK:-on}" != "skip" ]]; then
  printf '检查数据库连通性：%s:%s/%s ... ' "$DB_HOST" "$DB_PORT" "$DB_NAME"
  if ! db_ready; then
    echo "失败"
    cat >&2 <<TXT

错误：无法连接并验证数据库 ${DB_HOST}:${DB_PORT}/${DB_NAME}。
      后端启动很可能失败（Hikari 等 30s 超时后抛「尝试连线已失败」）。

排查顺序：
  1) 网络：开发库 ${DB_HOST} 通常要求办公网或 VPN，先确认 VPN 已连接、没切 Wi-Fi/热点
  2) 地址：当前使用 ${DB_HOST}/${DB_PORT}/${DB_NAME}（用户 ${DB_USERNAME}）
     若你的 shell 里 export 过 DB_HOST/DB_PORT 同名变量，会覆盖脚本默认值
  3) 手工验证：
       nc -z -G 5 ${DB_HOST} ${DB_PORT} && echo OK
       PGPASSWORD='你的密码' psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USERNAME} -d ${DB_NAME} -c 'select 1'
  4) 走跳板/端口转发等特殊情况，确认可达后强启：DB_PRECHECK=skip $0
TXT
    exit 1
  fi
  echo "可达"
fi

# 洞察/仪表盘当前不依赖 Elasticsearch。
export ES_URIS="${ES_URIS:-http://127.0.0.1:9200}"
export MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED="${MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED:-false}"

# 本地测试不启用领航认证和 Python 执行器。
export MATECLAW_PILOT_ENABLED="${MATECLAW_PILOT_ENABLED:-false}"
export PYTHON_EXECUTOR_ENABLED="${PYTHON_EXECUTOR_ENABLED:-false}"

# 本地 Python Runner 不走 Docker 服务名：DataAgent 提交脚本、Runner 回读数据
# 都必须通过宿主机回环地址访问，避免默认的 python-runner:8080 / mateclaw-dataagent
# 在本机开发环境中解析失败。
export MATECLAW_RUNNER_URL="${MATECLAW_RUNNER_URL:-http://127.0.0.1:18090}"
export MATECLAW_RUNNER_DATASET_READ_BASE_URL="${MATECLAW_RUNNER_DATASET_READ_BASE_URL:-http://127.0.0.1:18089/dataagent/api}"

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
backend_own_pids() {
  local pid command
  for pid in $(lsof -tiTCP:"${BACKEND_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
    command="$(ps -p "$pid" -ww -o command= 2>/dev/null || true)"
    if [[ "$command" == *"$JAR_PATH"* ]]; then
      echo "$pid"
    fi
  done
}

pid_count() {
  printf '%s\n' "$1" | awk 'NF { count++ } END { print count + 0 }'
}

old_pids="$(lsof -tiTCP:"$BACKEND_PORT" -sTCP:LISTEN 2>/dev/null || true)"
own_old_pids="$(backend_own_pids || true)"
if [[ -n "$old_pids" ]]; then
  if [[ -z "$own_old_pids" || "$(pid_count "$old_pids")" != "$(pid_count "$own_old_pids")" ]]; then
    echo "错误：端口 $BACKEND_PORT 被非本项目 DataAgent 进程占用，未执行 kill。" >&2
    lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >&2 || true
    ps -p $old_pids -ww -o pid=,command= >&2 || true
    exit 1
  fi
  # shellcheck disable=SC2086
  kill $own_old_pids
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
echo "健康检查地址：$BACKEND_HEALTH_URL"

cd "$PROJECT_ROOT"
# 显式锁定 HTTP 端口（命令行参数优先级最高，覆盖任何把 server.port 设成 0/随机的来源）。
if ! command -v curl >/dev/null 2>&1; then
  echo "错误：未找到 curl，无法执行后端健康检查。" >&2
  exit 1
fi

java -jar "$JAR_PATH" --server.port="$BACKEND_PORT" &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true; exit 143' INT TERM

for _ in {1..60}; do
  if curl --fail --silent --show-error --connect-timeout 1 --max-time 3 "$BACKEND_HEALTH_URL" >/dev/null 2>&1; then
    echo "DataAgent 后端已就绪（PID ${backend_pid}）。"
    wait "$backend_pid"
    exit $?
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "错误：DataAgent 后端进程已退出，未通过健康检查。" >&2
    wait "$backend_pid" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

echo "错误：DataAgent 后端在 60 秒内未通过健康检查：$BACKEND_HEALTH_URL" >&2
kill "$backend_pid" 2>/dev/null || true
wait "$backend_pid" 2>/dev/null || true
exit 1
