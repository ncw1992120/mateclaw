#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_ROOT="/Users/srant/IdeaProjects/codex/mateclaw-1"
JAR_PATH="$PROJECT_ROOT/mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar"
BACKEND_PORT="18089"

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
#   本地 mock 服务不存在时自动拉起（dev-support/local-simulation/scripts/aloudata-mock-server.py）。
#   要退回内置夹具（不起 HTTP 服务）：ALOUDATA_MOCK=embed ./restart-dataagent-backend.sh
#   要切回真实上游：ALOUDATA_MOCK=off ./restart-dataagent-backend.sh
#   （也可用 SPRING_PROFILES_ACTIVE=pgsql 显式覆盖整组 profile）
MOCK_SWITCH="${ALOUDATA_MOCK:-on}"
case "$MOCK_SWITCH" in
  on|ON|true|TRUE|1)
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-pgsql,local-mock}"
    export ALOUDATA_MOCK_PORT="${ALOUDATA_MOCK_PORT:-18081}"
    export ALOUDATA_MOCK_SERVER="${ALOUDATA_MOCK_SERVER:-http://127.0.0.1:${ALOUDATA_MOCK_PORT}}"
    MOCK_SERVER_SCRIPT="$PROJECT_ROOT/dev-support/local-simulation/scripts/aloudata-mock-server.py"
    if ! lsof -tiTCP:"$ALOUDATA_MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      if [[ -f "$MOCK_SERVER_SCRIPT" ]]; then
        echo "启动本地 Aloudata mock 服务：$ALOUDATA_MOCK_SERVER"
        nohup python3 "$MOCK_SERVER_SCRIPT" --port "$ALOUDATA_MOCK_PORT" \
          >"/tmp/aloudata-mock-server-${ALOUDATA_MOCK_PORT}.log" 2>&1 &
        for _ in {1..20}; do
          if lsof -tiTCP:"$ALOUDATA_MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1; then break; fi
          sleep 0.5
        done
      else
        echo "警告：未找到 mock 服务脚本 $MOCK_SERVER_SCRIPT，将退回内置夹具。" >&2
        unset ALOUDATA_MOCK_SERVER
      fi
    fi
    if [[ -n "${ALOUDATA_MOCK_SERVER:-}" ]]; then
      echo "★ Aloudata 上游 = 本地 mock 服务 ${ALOUDATA_MOCK_SERVER} （请求方式/路径/参数与正式一致，只有 ip:port 不同）。"
      echo "  停止 mock 服务：lsof -tiTCP:${ALOUDATA_MOCK_PORT} -sTCP:LISTEN | xargs kill"
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

