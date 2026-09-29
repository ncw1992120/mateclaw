# DataAgent 本地部署

本文记录 DataAgent 后端和 DataAgent UI 的本地最小启动方式，适用于本地测试问数、洞察和 Aloudata 数据源连接。

## 一、环境要求

- JDK 21
- Maven 3.9+
- Node.js 和 npm
- Python 3.12（洞察 Python 卡片执行）
- Python Worker 固定依赖（首次在联网环境执行 `uv sync --locked --no-dev`，或配置 `PYTHON_COMMAND` 指向含依赖的 Python 3.12 环境）
- 可访问的 PostgreSQL 数据库

当前本地服务端口：

| 服务 | 地址 |
| --- | --- |
| DataAgent 后端 | `http://127.0.0.1:18089/dataagent/api` |
| DataAgent UI | `http://127.0.0.1:5174` |

## 二、构建后端

在项目根目录执行：

```bash
cd /Users/srant/IdeaProjects/codex/mateclaw-1

mvn -pl mateclaw-plugin-api,mateclaw-server -am install -DskipTests
mvn -f mateclaw-sdk/pom.xml clean install -DskipTests
mvn -f mateclaw-dataagent/pom.xml clean package -DskipTests
```

构建产物：

```text
mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar
```

## 三、启动 DataAgent 后端

建议在独立终端中执行：

```bash
cd /Users/srant/IdeaProjects/codex/mateclaw-1

export SPRING_PROFILES_ACTIVE=pgsql
export DB_HOST=14.22.85.76
export DB_PORT=5432
export DB_NAME=qarvis_metric
export DB_USERNAME=qarvis_metric
export DB_PASSWORD='请填写数据库密码'

# 当前主要测试洞察功能，不依赖 Elasticsearch
export ES_URIS=http://127.0.0.1:9200
export MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false

export MATECLAW_PILOT_ENABLED=false
export PYTHON_EXECUTOR_ENABLED=true
export PYTHON_COMMAND="/绝对路径/python3.12"
export MATECLAW_PYTHON_WORKER_HOME="/Users/srant/IdeaProjects/codex/mateclaw-1/mateclaw-python-runner"
export JAVA_TOOL_OPTIONS='-Xms256m -Xmx1g -Duser.timezone=Asia/Shanghai'

java -jar mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar
```

数据库密码只通过环境变量传入，不要写入 Git 代码或配置文件。
Python Worker 是 DataAgent 启动的本机子进程，不需要单独启动 HTTP 服务或占用 18090 端口。Worker 源码默认位于项目中的 `mateclaw-python-runner`；上面的 `MATECLAW_PYTHON_WORKER_HOME` 只在自定义路径时需要配置。

### Windows 内网离线部署包

联网构建机上先完成 DataAgent JAR 构建，再运行：

```bash
bash dev-support/package-dataagent-offline-win.sh
```

生成 `mateclaw-dataagent/target/dataagent-offline-win-x64.zip`，包含 JAR、Worker 源码、锁定依赖清单和 Python 3.12 x64 wheels。将 ZIP 拷贝到 Windows 并解压后，在 CMD 中先安装离线依赖：

```cmd
py -3.12 -m pip install --no-index --find-links python-worker\wheelhouse -r python-worker\requirements.txt
```

然后在同一个 CMD 窗口设置数据库及 Worker 运行参数，并启动：

```cmd
set SPRING_PROFILES_ACTIVE=pgsql
set DB_HOST=你的数据库地址
set DB_PORT=5432
set DB_NAME=你的数据库名
set DB_USERNAME=你的用户名
set DB_PASSWORD=你的密码
set PYTHON_EXECUTOR_ENABLED=true
set PYTHON_COMMAND=C:\Python312\python.exe
set MATECLAW_PYTHON_WORKER_HOME=%CD%\python-worker
set MATECLAW_DATASET_READ_BASE_URL=http://127.0.0.1:18089
java -jar mateclaw-dataagent-1.0.0-SNAPSHOT.jar
```

此方案需要先解压安装离线 wheels；服务启动期间不会访问 PyPI 或在线安装包。

## 四、检查后端状态

另开终端执行：

```bash
curl http://127.0.0.1:18089/dataagent/api/actuator/health
```

正常结果：

```json
{"status":"UP"}
```

如果只使用洞察和仪表盘功能，可以关闭 Elasticsearch。此时必须保留：

```bash
export MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false
```

否则 Spring Boot 可能因为无法连接本地 Elasticsearch 而将健康检查报告为 `DOWN`。

## 五、启动 DataAgent UI

另开一个终端执行：

```bash
cd /Users/srant/IdeaProjects/codex/mateclaw-1/mateclaw-dataagent-ui

npm install
npm run dev -- --host 127.0.0.1
```

浏览器访问：

```text
http://127.0.0.1:5174
```

## 六、停止服务

停止 DataAgent 后端：

```bash
lsof -tiTCP:18089 -sTCP:LISTEN | xargs kill
```

停止 DataAgent UI：

```bash
lsof -tiTCP:5174 -sTCP:LISTEN | xargs kill
```

查看服务是否仍在监听：

```bash
lsof -nP -iTCP:18089 -sTCP:LISTEN
lsof -nP -iTCP:5174 -sTCP:LISTEN
```

没有输出表示对应端口没有服务监听。

## 七、Aloudata 连接配置

如果 Aloudata 产品服务使用 HTTPS 标准端口，配置为：

```text
产品服务地址： https://demo.can.aloudata.com
产品服务端口： 443
```

语义服务配置为：

```text
语义服务地址： http://semantic.demo.can.aloudata.com
语义服务端口： 80
```

DataAgent 会将地址和端口拼接成实际请求 URL。不要把 HTTPS 服务的端口 `8083` 填到产品服务端口中，除非 Aloudata 环境明确支持该端口的 TLS 握手。
