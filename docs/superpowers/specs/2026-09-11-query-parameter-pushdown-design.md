# Python 条件读取与 JDBC 查询下推技术设计

**日期：** 2026-09-11
**状态：** 待评审
**范围：** `mateclaw-dataagent` 通用洞察仪表盘第一阶段

## 1. 目标

建立一条可验证的查询链路：页面参数作为 Python 脚本入参，用户通过平台提供的 `datasets.read(..., filters=...)` 在读取数据集时声明过滤条件；平台将读取请求转换为参数化 JDBC 查询，数据库先完成过滤、字段选择和分页，再将结果交给 Python 做多数据源 Join、清洗和聚合。

```text
页面参数
  → Python datasets.read(filters)
  → QuerySpec
  → JDBC SQL + PreparedStatement
  → 数据库过滤/投影/分页
  → Arrow/Parquet dataRef
  → Python Runner
  → Polars/Pandas 预处理
  → 页面展示
```

## 2. 已确认的边界

- SQL 只对 JDBC 数据源开放；Aloudata 继续使用现有指标、维度和指标视图语义层。
- Python 读取请求中的筛选条件只能追加，不能覆盖用户 SQL 已有条件。
- 首期下推范围为过滤、字段选择、分页和参数绑定；复杂逻辑不从任意 Python 代码中反向提取。
- 同一 JDBC 数据源的 Join 可由用户 SQL 完成；跨数据源 Join 由 Python 完成。
- SQL 参数必须使用绑定变量，禁止字符串拼接。
- 预览使用同步执行和受限结果；正式查询使用异步任务。
- 暂定采用兼容双模式（方案 B）：新 Python Runner 使用固定 Docker 镜像，默认提供 `pandas`、`polars`、`pyarrow`，不支持运行时 `pip install`；旧 `LocalCodeExecutorService` 保持现状并标记为兼容路径。
- 数据源名称全局唯一，不带租户作用域；可见性通过数据源权限控制。
- 当前不引入 DuckDB、Ibis、DataFusion、Calcite 或 Trino。

### 2.1 Python 执行兼容策略（方案 B）

```text
现有 Agent Python：PythonAnalysisTool → LocalCodeExecutorService
新仪表盘脚本：PythonExecutionService → Python Runner
```

开发环境可提供 `local` 模式复用本机 Python；Docker 环境默认使用独立的 `mateclaw-python-runner:<version>` 容器。第一阶段 Runner Manager 在容器内为每个任务创建隔离进程，不要求每个任务都新建 Docker 容器，但任务之间不得共享 Python 进程状态。旧路径的运行时 `pip install` 暂不改动，新路径不接受运行时依赖安装参数。

## 3. 核心模型

### 3.1 页面参数

```json
{
  "name": "start_date",
  "type": "date",
  "value": "2026-01-01",
  "scope": "dashboard"
}
```

参数类型至少包括 `string`、`number`、`boolean`、`date`、`datetime`、`enum` 和 `date_range`。参数值在服务端按声明类型校验，不能直接信任浏览器输入。

### 3.2 Python 数据集读取接口

```json
{
  "datasetId": "orders",
  "columns": ["user_id", "order_date", "amount"],
  "filters": [
    {"column": "order_date", "operator": ">=", "parameter": "start_date"},
    {"column": "order_date", "operator": "<=", "parameter": "end_date"}
  ]
}
```

Python 通过受限 SDK 发起读取请求；页面参数只作为脚本参数传入，不要求用户在产品侧配置参数到字段的绑定。每个 `filters` 条件必须引用数据集 Schema 中已授权的字段。

### 3.3 输入数据集目录与脚本别名

多数据源 Python 节点先从已固化数据集中选择输入，并为每个输入分配脚本别名。`datasetId` 是平台内部稳定 ID，用户脚本不直接填写：

```json
{
  "inputs": [
    {
      "alias": "orders",
      "datasetId": "dataset-001",
      "schema": {
        "columns": [
          {"name": "user_id", "type": "BIGINT"},
          {"name": "order_date", "type": "DATE"},
          {"name": "amount", "type": "DECIMAL"}
        ]
      }
    }
  ]
}
```

Spring/DataAgent 在运行脚本前注入当前任务可用的输入目录，只包含别名、内部 ID、Schema、参数定义和能力信息，不包含事实数据、数据库凭据或未授权数据集。编辑器提供别名/字段自动补全、类型提示、读取模板和受限样本预览。

运行时输入分为两层：

```text
DatasetInputDescriptor（Spring/DataAgent 返回）
  ├── datasetId
  ├── schema
  ├── rowCount / statistics
  ├── dataRef
  └── transport

DatasetInput（Python SDK 包装）
  ├── schema()
  ├── to_polars()
  ├── to_pandas()
  └── iter_batches()
```

Spring 通过 JSON 传递描述符，不传递 Python 函数或数据库连接；Python SDK 根据 `dataRef` 创建批次读取器。

### 3.4 QuerySpec

```json
{
  "datasetId": "orders",
  "datasourceId": "ds-orders",
  "baseSql": "SELECT user_id, order_date, amount FROM orders",
  "select": ["user_id", "order_date", "amount"],
  "filters": [
    {"column": "order_date", "operator": ">=", "parameter": "start_date"},
    {"column": "order_date", "operator": "<=", "parameter": "end_date"}
  ],
  "page": {"limit": 10000, "offset": 0}
}
```

`QuerySpec` 是平台内部契约，由 `datasets.read` 读取请求生成，不允许前端或 Python 直接提交最终 SQL。它必须包含数据源、数据集、授权字段、参数、资源限制和审计摘要。

标准脚本使用输入别名而不是内部 `datasetId`：

```python
from mateclaw import datasets, filters, params

orders = datasets.read(
    input_name="orders",
    columns=["user_id", "order_date", "amount"],
    filters=[
        filters.gte("order_date", params.get("start_date")),
        filters.lte("order_date", params.get("end_date")),
    ],
)
```

`datasets.read` 将读取请求发送给 DataAgent，由 DataAgent 根据别名解析内部 ID，校验 Schema、权限、操作符和资源限制，再生成参数化 JDBC SQL。读取完成后返回已经过滤的数据集输入；Python 中对 DataFrame 的二次过滤不下推。

## 4. SQL 生成与安全校验

### 4.1 处理顺序

1. 根据 `datasetId` 获取已保存的基础 SQL 和数据集 Schema。
2. 使用 JSqlParser 解析为 AST，确认是单条只读查询。
3. 校验表、字段、函数、参数和数据源权限。
4. 将 `datasets.read` 请求中的过滤条件加入 AST；读取条件只能追加，不能替换基础 SQL 条件。
5. 生成带 JDBC 占位符的 SQL 和有序参数列表。
6. 执行资源预算检查和目标数据源 `EXPLAIN`（如果支持）。
7. 使用 PreparedStatement 执行查询。

### 4.2 外层追加与安全改写

默认语义是将用户 SQL 作为受控子查询，再追加页面条件：

```sql
SELECT *
FROM (
  SELECT user_id, order_date, amount
  FROM orders
) AS _mateclaw_source
WHERE order_date >= ?
  AND order_date <= ?
```

为避免 `ORDER BY`、`LIMIT`、分页或特定方言导致语义错误，实际实现必须优先使用 AST 改写；不能安全改写时返回可识别错误，不得退化为字符串拼接。平台不承诺数据库一定把外层条件继续优化到最底层扫描，实际效果通过 `EXPLAIN` 和执行统计确认。

### 4.3 只读与资源限制

- 默认只允许 `SELECT` 和 `WITH`；拒绝 DML、DDL、会话操作和多语句。
- 参数值单独绑定，不允许把参数拼入 SQL 文本。
- 限制最大返回行数、分页大小、查询超时和扫描预算。
- 预览超出预算默认阻断；小结果查询可用 `LIMIT` 预览，不因缺少 `WHERE` 关键字机械阻断。
- 记录原始 SQL、规范化 SQL、参数名（不记录敏感值）、Parser 版本、digest 和执行统计。

## 5. 执行时序

### 5.1 单数据源

```text
页面点击查询
  → 读取页面参数并传入 Python
  → Python 调用 datasets.read(filters)
  → 构造 QuerySpec
  → SQL AST 校验和资源检查
  → JDBC 执行
  → 结果转换为 DatasetBatch/dataRef
  → 组件渲染
```

### 5.2 多数据源

```text
页面点击查询
  → Python 为每个输入数据集调用 datasets.read(filters)
  → 各数据源独立执行过滤/投影/分页
  → 输出受控 DatasetBatch 或 dataRef
  → Python Runner 接收多个输入
  → Polars/Pandas Join、清洗、聚合
  → 输出最终 Schema + dataRef
  → 页面渲染
```

Python 不获得数据库连接、凭据或未授权数据源访问能力。Python 代码中的二次过滤发生在数据返回后，不会自动下推。

## 6. 预览与正式执行

- 预览任务同步执行，返回有限行数和字段示例；默认 JSON 输入不超过 10 MB、10,000 行、100 列、嵌套深度 5，预览时间不超过 30 秒。
- 正式查询异步执行，使用 Arrow/Parquet 和 `dataRef`，支持状态查询、取消、重试和日志读取。
- 预览和正式执行必须复用同一 `QuerySpec`，只调整资源上限和输出模式。
- 默认只保存数据集/脚本定义，不保存最终结果；后续缓存必须绑定 workspace、权限和 TTL。

## 7. 错误处理

| 错误类型 | 示例 | 处理 |
| --- | --- | --- |
| 参数错误 | 日期格式、枚举值或必填参数非法 | 阻止执行，返回字段级错误 |
| 读取请求错误 | 字段不存在、过滤操作符非法或无权限 | 阻止执行，定位到 datasets.read 请求 |
| SQL 错误 | 多语句、写操作、AST 解析失败 | 阻止执行，返回结构化错误 |
| 资源超限 | 扫描量、行数、超时超过预算 | 阻止预览/任务，提示调整筛选 |
| 数据源错误 | 连接失败、数据库返回错误 | 保留数据源错误码和 requestId |
| Python 错误 | Runner 超时、内存超限、脚本异常 | 终止任务，保留日志和代码行定位 |

## 8. 验收标准

### 功能验收

- Python 可以通过 `datasets.read(..., filters=...)` 在读取时指定日期和枚举筛选。
- 页面查询可以生成参数化 JDBC SQL，并展示 SQL 摘要、参数名和下推状态。
- 页面条件不会覆盖基础 SQL 条件。
- 单源查询能完成预览和发布；多源查询能将过滤后的数据交给 Python 并返回最终结果。
- Python 脚本不能直接访问数据库连接或其他 workspace 数据。

### 安全与资源验收

- DML、DDL、多语句和未授权字段无法执行。
- 参数值不会出现在 SQL 字符串拼接结果中。
- 超过 JSON、扫描、行数、内存或超时限制时能明确失败。
- Runner 使用固定 Docker 镜像，任务无运行时 `pip install`。

### 兼容验收

- 旧 Aloudata 仪表盘 Schema 可以继续读取和渲染。
- 旧 Agent Python 路径继续可用，新仪表盘路径不调用旧执行器。

## 9. 实施顺序

1. 定义页面参数、`DatasetReadRequest` 和 `QuerySpec` DTO/Schema。
2. 实现受限 `datasets.read` SDK、JSqlParser 只读校验、AST 条件追加和 PreparedStatement 参数绑定。
3. 实现单源 JDBC 预览、EXPLAIN 和资源预算。
4. 将页面组件接入脚本参数和查询状态。
5. 实现 `DatasetBatch/dataRef` 传输和 Python Runner 调用。
6. 增加多源 Python Join、最终 Schema 和异步正式任务。
7. 补齐权限、审计、取消、重试、缓存和旧 Schema 兼容测试。
