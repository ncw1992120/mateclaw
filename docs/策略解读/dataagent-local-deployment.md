# DataAgent 本地部署

本文记录 DataAgent 后端和 DataAgent UI 的本地最小启动方式，适用于本地测试问数、洞察和 Aloudata 数据源连接。

## 一、环境要求

- JDK 21
- Maven 3.9+
- Node.js 和 npm
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
export PYTHON_EXECUTOR_ENABLED=false
export JAVA_TOOL_OPTIONS='-Xms256m -Xmx1g -Duser.timezone=Asia/Shanghai'

java -jar mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar
```

数据库密码只通过环境变量传入，不要写入 Git 代码或配置文件。

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
