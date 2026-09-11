# 页面参数绑定与 JDBC 查询下推技术设计

**日期：** 2026-09-11
**状态：** 待评审
**范围：** `mateclaw-dataagent` 通用洞察仪表盘第一阶段

## 1. 目标

建立一条可验证的查询链路：用户在页面选择时间、类型等筛选条件后，平台将参数绑定到各输入数据集字段，生成参数化 JDBC 查询；数据库先完成过滤、字段选择和分页，再将结果交给 Python 做多数据源 Join、清洗和聚合。

```text
页面参数
  → 数据集字段绑定
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
- 页面筛选条件只能追加，不能覆盖用户 SQL 已有条件。
- 首期下推范围为过滤、字段选择、分页和参数绑定；复杂逻辑不从任意 Python 代码中反向提取。
- 同一 JDBC 数据源的 Join 可由用户 SQL 完成；跨数据源 Join 由 Python 完成。
- SQL 参数必须使用绑定变量，禁止字符串拼接。
- 预览使用同步执行和受限结果；正式查询使用异步任务。
- Python Runner 使用固定 Docker 镜像，默认提供 `pandas`、`polars`、`pyarrow`，不支持运行时 `pip install`。
- 旧 `LocalCodeExecutorService` 保持兼容，标记为后续安全迁移范围。
- 数据源名称全局唯一，不带租户作用域；可见性通过数据源权限控制。
- 当前不引入 DuckDB、Ibis、DataFusion、Calcite 或 Trino。

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

### 3.2 数据集字段绑定

```json
{
  "parameter": "start_date",
  "datasetId": "orders",
  "column": "order_date",
  "operator": ">=",
  "required": true
}
```

同一页面参数可绑定到多个数据集的不同字段，例如 `business_type` 分别绑定到 Doris 的 `order_type` 和 MySQL 的 `user_category`。绑定必须引用数据集 Schema 中已授权的字段。

### 3.3 QuerySpec

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

`QuerySpec` 是平台内部契约，不允许前端直接提交最终 SQL。它必须包含数据源、数据集、授权字段、参数、资源限制和审计摘要。

## 4. SQL 生成与安全校验

### 4.1 处理顺序

1. 根据 `datasetId` 获取已保存的基础 SQL 和数据集 Schema。
2. 使用 JSqlParser 解析为 AST，确认是单条只读查询。
3. 校验表、字段、函数、参数和数据源权限。
4. 将页面绑定条件加入 AST；页面条件只能追加，不能替换基础 SQL 条件。
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
  → 读取页面参数
  → 加载数据集绑定
  → 构造 QuerySpec
  → SQL AST 校验和资源检查
  → JDBC 执行
  → 结果转换为 DatasetBatch/dataRef
  → 组件渲染
```

### 5.2 多数据源

```text
页面点击查询
  → 为每个输入数据集构造 QuerySpec
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
| 绑定错误 | 参数绑定字段不存在或无权限 | 阻止保存或执行，定位到绑定配置 |
| SQL 错误 | 多语句、写操作、AST 解析失败 | 阻止执行，返回结构化错误 |
| 资源超限 | 扫描量、行数、超时超过预算 | 阻止预览/任务，提示调整筛选 |
| 数据源错误 | 连接失败、数据库返回错误 | 保留数据源错误码和 requestId |
| Python 错误 | Runner 超时、内存超限、脚本异常 | 终止任务，保留日志和代码行定位 |

## 8. 验收标准

### 功能验收

- 页面日期和枚举筛选可以绑定到一个或多个数据集字段。
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

1. 定义参数、绑定和 `QuerySpec` DTO/Schema。
2. 实现 JSqlParser 只读校验、AST 条件追加和 PreparedStatement 参数绑定。
3. 实现单源 JDBC 预览、EXPLAIN 和资源预算。
4. 将页面组件接入参数绑定和查询状态。
5. 实现 `DatasetBatch/dataRef` 传输和 Python Runner 调用。
6. 增加多源 Python Join、最终 Schema 和异步正式任务。
7. 补齐权限、审计、取消、重试、缓存和旧 Schema 兼容测试。
