# MateClaw 本地外部依赖模拟环境

该目录只服务本地开发和自动化测试，不连接生产数据库、对象存储或 Aloudata。

## 启动

```bash
./scripts/start.sh
./scripts/check.sh
```

`start.sh` 会等待 Compose 中声明健康检查的服务就绪后再上传 fixture；如果首次启动较慢，脚本会在 120 秒后明确失败，不会在数据库尚未 ready 时误报 seed 成功。

也可以从项目根目录运行统一前置条件检查：

```bash
./scripts/verify-dashboard-external-prerequisites.sh --local
# 包含 Aloudata tenant/view/filter 占位配置的本地闭环
make dashboard-prerequisites-simulation
```

`api/orders-openapi.yaml` 是本地 API 所有者提供的参数契约样例；数据源配置仍应引用后端登记的 `apiDefinitionId`，不能让脚本自行传入 URL 或 Header。

本地 Aloudata 模拟变量模板为 `.env.aloudata-simulation.example`。启动服务后可执行 `set -a; source .env.aloudata-simulation.example; set +a`，再运行本地 Adapter/脚本探测。该模板使用 HTTP WireMock 和占位认证值，不能用于真实环境；真实 HTTPS 验收仍需配置 JVM truststore。

Adapter 探测可在宿主机可访问 Maven/Docker 时执行（容器内请把地址改为 `host.docker.internal`）：

```bash
set -a; source dev-support/local-simulation/.env.aloudata-simulation.example; set +a
mvn -f mateclaw-dataagent/pom.xml -Dtest=AloudataAnalysisViewExternalIT test -q
```

首次启动会从 `.env.example` 创建未纳入 Git 的 `.env.local`。服务地址：

| 服务 | 宿主机地址 | 默认用途 |
| --- | --- | --- |
| MySQL 8.4 | `127.0.0.1:13306` | JDBC 表和 SQL 数据集 |
| PostgreSQL 15.6 | `127.0.0.1:15432` | JDBC 方言兼容 |
| MinIO API | `127.0.0.1:19000` | 文件对象和 ObjectRef |
| MinIO Console | `127.0.0.1:19001` | 查看本地 bucket |
| WireMock HTTP | `127.0.0.1:18081` | HTTP/API、Aloudata 脱敏接口快速调试 |
| WireMock HTTPS | `127.0.0.1:18443` | HTTPS endpoint、TLS 和参数透传验证（本地临时证书） |
| Python Runner | 仅内部网络 | `/health`、无运行时 `pip install` 和外网访问阻断验证 |

启动脚本会创建 `mateclaw-sim` bucket 并将 `files/` 下的四种 fixture 上传为稳定的 `files/<name>` 对象键。`files/fixtures-manifest.json` 记录字节数、SHA-256、Schema、过滤预期和 Join 键；它只用于本地校验，不上传到 bucket。

订单样例统一为 10 行，包含 3 行 `PAID`、`CANCELLED`/`PENDING`/`SHIPPED` 等其他状态、null、Decimal、中文值和无匹配客户的 Join 键；`check.sh` 会校验行数、manifest 和 SHA-256。

停止并删除所有本地数据：

```bash
./scripts/cleanup.sh
```

如需生成二进制格式样本，使用已安装 PyArrow 的 Python 环境执行：

```bash
python scripts/generate-fixtures.py
```

它会从 `orders.csv` 生成 `orders.parquet` 和 `orders.xlsx`；生成文件只用于本地测试，可按需上传到 MinIO。

## DataAgent 本地连接示例

- MySQL：host `127.0.0.1`、port `13306`、database `mateclaw_sim`、user `mateclaw_sim`；
- PostgreSQL：host `127.0.0.1`、port `15432`、database `mateclaw_sim`、user `mateclaw_sim`；
- ObjectRef：endpoint `http://127.0.0.1:19000`、bucket `mateclaw-sim`；
- HTTP/API：endpoint `https://127.0.0.1:18443/orders`（本地临时证书；`curl` 调试可加 `--insecure`）。生产策略要求正式 CA 时，替换为测试环境证书链；HTTP `18081` 仅用于快速调试；
- Aloudata 模拟：产品层基址 `http://127.0.0.1:18081`、语义层基址 `http://127.0.0.1:18081`，视图名 `local_sales_view`。

本地模拟的认证值只是测试占位符，不能复制到测试或生产环境。身份与权限不在本目录模拟。
