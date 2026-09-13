# 通用洞察仪表盘能力设计

> **第一阶段范围状态：已冻结（2026-09-11）**。首期支持 JDBC、Aloudata 指标视图、HTTP/API 和文件数据源，以及 Python 多数据源预处理；JS、湖仓目录、流式数据源和 Trino 等能力不在首期范围。

## 1. 文档定位

沉淀可复用的：

- 数据源与数据集能力；
- 组件、组合卡片和容器能力；
- 页面、页签和视角能力；
- 参数、事件、动作和组件联动能力；
- SQL、Python、JS 数据处理能力；
- 查询下推、预览、调试和资源保护能力。

## 2. 产品原则

### 2.1 面向所有用户

用户心智应接近：

```text
拖组件 → 选数据 → 配置字段 → 配置筛选 → 连接联动 → 预览 → 发布
```

用户不必理解 Vue、Hook、事件总线或底层数据库执行细节。需要编写简单 SQL 或脚本的用户可以借助外部大模型生成草稿，但平台不负责自动生成或直接执行 AI 输出；所有内容都必须经过用户确认、平台校验、预览和权限控制。复杂能力通过可视化配置、合理默认值、即时校验、数据预览和明确错误提示暴露。

### 2.2 通用化而非业务特例

不得把某个具体业务名称、指标名称、固定布局坐标和固定联动逻辑硬编码进通用组件。业务名称和指标只能存在于数据集、指标视图或仪表盘配置中。

### 2.3 展示、控制、操作分离

组件分为三类：

- 展示组件：KPI、图表、数据表格、AI 分析等；
- 控制组件：筛选器、时间筛选、视角选择器、变量选择器等；
- 操作组件：查询、重置、刷新、导出、导航、打开详情和业务动作等。

操作类能力应抽象为通用 Action Card，不为当前原型单独增加按钮逻辑。

## 3. 数据源与数据集模型

### 3.1 四层抽象

数据能力分为四层，避免把连接信息、业务对象和组件查询混在一起：

```text
Connection 连接
    ↓
Datasource 数据源
    ↓
Dataset 数据集
    ↓
Query 组件查询
```

#### Connection

保存连接所需的信息，例如 JDBC URL、账号、密码、连接池和网络配置。敏感信息必须加密保存，不能写入仪表盘配置或日志。

#### Datasource

表示一种外部数据访问能力，例如 Aloudata、JDBC 或 HTTP API，并声明该数据源支持的查询能力。

#### Dataset

表示用户真正使用的数据对象，例如数据库表、指标视图、API 数据集、上传文件、SQL 数据集或 Python 处理结果。

#### Query

表示某个仪表盘组件或 Python 任务对数据集的使用方式，包括字段、聚合、排序、分页、临时筛选和参数传递。

### 3.2 数据源能力声明

数据源不能只保存 `type`，还应声明能力，由前端根据能力展示配置项：

```json
{
  "type": "jdbc",
  "dialect": "mysql",
  "capabilities": {
    "filterPushdown": true,
    "projectionPushdown": true,
    "aggregationPushdown": true,
    "joinPushdown": true,
    "pagination": true,
    "explain": true
  }
}
```

## 4. 数据源类型

### 4.1 Aloudata 指标平台

Aloudata 应拆成两种数据能力：

#### 既有指标/维度查询（兼容保留）

现有 Agent/仪表盘中的指标、维度查询能力继续兼容，但不纳入第一阶段新的统一数据集创建入口。第一阶段的 Aloudata Adapter 和管理 UI 只支持选择、查询已有指标视图，不新增自由选择指标、维度并保存为数据集的产品流程。

#### 指标视图

指标视图应作为一等数据集类型，而不是普通接口。指标视图可以预先定义：

- 指标和维度集合；
- 指标口径；
- 默认筛选条件；
- 时间字段和时间粒度；
- 权限范围；
- 可用的展示字段。

用户在仪表盘中只需要选择指标视图，再选择要展示的字段和样式。

内部模型仍可区分既有查询和本期指标视图，避免兼容代码混入新数据集契约：

```text
AloudataMetricQuery
AloudataMetricView
```

指标视图仍需支持仪表盘参数和临时筛选透传，但只能映射为已声明的 Aloudata 时间条件、维度筛选或指标结果筛选，不允许提交任意 Aloudata 查询 JSON。

第一阶段只支持在 MateClaw 中选择和查询 Aloudata 已有指标视图，不支持通过 MateClaw 创建、修改或删除指标视图。

#### Aloudata 指标视图接入方式

指标视图采用专用 `AloudataAnalysisViewAdapter`，不直接当作普通 JDBC 表或通用 HTTP 数据集处理：

```text
AnyMetrics API
  ├── 指标视图列表/树
  └── 指标视图详情与权限
Semantic API
  ├── 指标视图结果查询
  └── 指标查询（用于部署版本不支持视图临时筛选时）
```

MateClaw 的 Aloudata 数据源连接保存默认 `tenantId`，数据集保存 `aloudataViewId` 和 `aloudataViewName`。`tenantId` 是 Aloudata 查询上下文，不等于 MateClaw 数据源名称，也不要求数据源名称带租户作用域。AnyMetrics 地址用于目录和元数据，Semantic 地址用于指标结果查询；两类地址、认证信息和版本兼容性由 Adapter 管理。

指标视图转换为统一 `DatasetInputDescriptor` 时，字段必须标注指标/维度角色、类型、时间能力和可用筛选操作。Python `datasets.read` 的读取请求由 DataAgent 校验后转换为 Aloudata 查询参数，当前结果返回受限批次；脚本大结果再转换为受控 `dataRef`。MateClaw 负责数据源和指标视图的使用权限校验，Aloudata 继续负责其内部指标权限校验。结果查询统一走 Semantic API；Aloudata JDBC 虚拟表方式作为兼容接入保留，不作为指标视图的默认主链路。分页、最大行数和超时由 MateClaw 统一限制。

`analysisView/query` 当前登记的参数只有 `viewName`、分页和结果类型，不能预设它支持 `datasets.read` 的临时时间或维度筛选。无临时筛选时可以直接使用指标视图结果接口；存在临时筛选时，Adapter 必须先根据实际 Aloudata 版本验证能力。若该接口不支持筛选，则从指标视图详情中提取指标、维度、时间约束、默认筛选和排序，服务端构造等价的 Semantic `metrics/query`，再以结构化方式合并本次允许的筛选条件。无法完整、无歧义地转换时应拒绝该筛选并返回能力错误，不允许先全量读取再在 JVM 或 Python 中伪装为下推。

现有实现可复用 `AloudataApiClient` 的配置驱动端点和认证 Header 注入机制。数据库中的 Aloudata API 端点配置已包含 `analysis_view_query_by_name`、`analysis_view_query_data` 和 `metrics_query`；因此不新增独立 HTTP 客户端，优先在 `AloudataService` 旁增加指标视图专用服务、视图查询编译器和响应映射。正式接入前必须用真实 Aloudata 环境验证目录、详情、结果、分页、`tenantId`、错误码，以及至少一个维度或时间筛选确实在远端生效。

已使用一套真实 Aloudata 环境完成只读联调：`analysisview/treeList` 和 `analysisview/queryByName` 均能返回指标视图目录/详情；Semantic `analysisView/query` 接口可达，但无权访问某个视图时返回 `SM_02_0038` 及“用户&资源没有权限”错误。Adapter 必须将此类响应映射为结构化的 `VIEW_ACCESS_DENIED`，在数据集目录和预览中显示申请权限提示，不得当作空结果或重试为成功。

从长期产品模型看，Aloudata 可以存在两种数据获取模式：

```text
Aloudata
  ├── 指标&维度
  └── 指标视图
```

“指标&维度”是既有兼容能力；“指标视图”用于直接选择已经定义好指标口径、维度和权限范围的指标视图。第一阶段的新数据集选择器只显示“指标视图”，不得因为底层已有 `metrics_query` 就扩大为自由指标建模功能；`metrics_query` 仅可由 Adapter 在保持视图语义时作为受控执行后端。

### 4.2 JDBC 数据源

MySQL、Doris、StarRocks 等数据库在产品界面上可以分别展示，但底层应统一为 JDBC 数据源，通过 Dialect 和能力声明处理差异：

```text
JDBC Datasource
  ├── MySQL Dialect
  ├── Doris Dialect
  ├── StarRocks Dialect
  ├── PostgreSQL Dialect
  └── 其他数据库 Dialect
```

每种 Dialect 负责：

- SQL 方言；
- 类型映射；
- 分页语法；
- 时间和字符串处理；
- EXPLAIN 语句及结果解析；
- 分区、索引或排序键相关提示。

不能因为 Doris、StarRocks 兼容 MySQL 协议，就把它们完全当作 MySQL 处理。

#### JDBC 数据源名称

每个 JDBC 数据源必须配置一个数据源名称，名称在整个 MateClaw 系统内全局唯一。数据源名称没有租户作用域；某个租户是否能看到或使用某个数据源，由独立的数据源访问权限控制。

名称规则：

- 必填；
- 长度 1～64 个字符；
- 支持中文及其他 Unicode 字母、英文字母、数字、空格、下划线和短横线；拒绝控制字符、路径分隔符和仅符号名称；
- 不允许仅由空格组成；
- 保存前自动去除首尾空格；
- 名称唯一校验按 Unicode NFKC、去除首尾空格、连续空格折叠及英文字母不区分大小写后的标准化值执行。

示例：

```text
业务分析库
doris_strategy_cluster
```

数据源名称和数据源 ID 必须分离：

```text
name              用户显示名称，可修改，全局唯一
normalizedName    去除首尾空格并统一大小写后的唯一校验值
datasourceId      系统生成的稳定 ID，不因名称修改而变化
```

数据集、仪表盘和组件配置必须使用 `datasourceId` 关联数据源，不能使用名称关联，避免数据源改名后破坏已有引用。

#### 名称配置交互

新增 JDBC 数据源时，“数据源名称”应位于表单顶部，并在用户输入后进行即时校验：

1. 输入或修改名称时进行格式校验；
2. 输入框失焦时检查是否已存在；
3. 点击保存时再次进行后端校验；
4. 名称重复时在字段附近提示“数据源名称已存在，请更换名称”；
5. 编辑数据源时允许修改名称，但必须重新执行唯一性校验。

前端校验只用于即时反馈，后端必须通过数据库唯一索引保证最终一致性。并发创建或修改发生冲突时，后端应返回可识别的名称冲突错误，而不是通用保存失败：

```text
UNIQUE(normalized_name)
```

这里的“全局唯一”指整个 MateClaw 系统内唯一，而不是仅在某个工作区内唯一。

#### JDBC 在组件中的使用

仪表盘组件选择 JDBC 数据源后，不直接进入“选择表”流程，而是进入 SQL 配置区。用户通过标准 SQL 对当前 JDBC 数据源资源进行初步处理，SQL 的结果作为一个 JDBC 输入数据集。SQL 只允许访问当前 JDBC 数据源，不承担跨数据源 Join；跨数据源组合统一由后续脚本预处理节点完成：

```text
选择 JDBC 数据源
    ↓
配置标准只读 SQL
    ↓
校验 SQL / 预览数据 / 查看执行计划
    ↓
形成 JDBC 输入数据集
```

SQL 配置至少支持：

- 标准 SQL 只读校验；
- SQL 格式化；
- 仪表盘参数声明和绑定；
- 临时筛选条件；
- 字段结构识别；
- 数据预览；
- EXPLAIN 和扫描量提示。

仪表盘参数必须使用参数绑定，例如 `:start_date`，不得使用字符串拼接。数据库表、视图和字段选择属于 SQL 编辑器或数据集配置的一部分，不作为 JDBC 数据源选择后的强制页面。

JDBC SQL 的预览必须经过参数绑定和资源预算检查。系统不再通过是否包含 `WHERE` 关键字判断安全性，而是使用 AST 校验、参数绑定、`EXPLAIN` 或数据源统计估算扫描量；小结果查询可以使用 `LIMIT` 直接预览。

Python 通过平台提供的 `datasets.read(..., filters=...)` 接口声明读取条件。平台将读取请求转换为 `QuerySpec`，把条件追加到用户 SQL 并通过 JDBC 发送给目标数据库执行，因此属于数据库侧过滤下推。平台不得通过字符串拼接实现，必须先解析 SQL AST；对于包含 `ORDER BY`、`LIMIT`、分页或不允许嵌套的方言语法，优先采用 AST 安全改写，无法安全改写时拒绝执行并提示用户调整 SQL。

示例：

```sql
-- 用户配置的 SQL
SELECT user_id, order_date, amount
FROM orders
```

```sql
-- Python datasets.read(filters=...) 后的平台 JDBC SQL
SELECT *
FROM (
  SELECT user_id, order_date, amount
  FROM orders
) AS _mateclaw_source
WHERE order_date >= ?
  AND order_date <= ?
```

外层追加是产品默认语义，但不承诺所有数据库都一定将外层条件继续优化到最底层扫描；实际扫描效果必须通过 `EXPLAIN` 和执行统计验证。


### 4.3 HTTP/API 数据源

API 数据源应从“保存 URL 并发请求”升级为“可配置的接口数据集”，至少支持：

- HTTP Method 和 URL；
- Header、Query、Body 参数；
- 参数类型和默认值；
- 仪表盘参数到接口参数的映射；
- 返回数据路径；
- 字段名、字段类型和展示名；
- 分页方式；
- 鉴权方式；
- 超时、重试和限流；
- 缓存策略；
- 增量查询或时间范围查询。

只有能够映射为 API 参数的筛选条件，才能透传到底层接口。不得允许仪表盘直接调用任意地址，接口调用必须经过后端白名单、权限校验和审计。

### 4.4 文件数据源

建议加入 Excel、CSV、JSON、Parquet 和对象存储文件能力，优先满足临时分析、外部名单和手工报表场景。

文件应上传到对象存储并登记字段结构，不应默认一次性加载进 JVM 内存：

```text
上传文件 → 对象存储 → 识别字段 → 生成数据集 → 按需读取/处理
```

### 4.5 逻辑数据集

逻辑数据集不是传统外部数据源，但对复杂报表十分重要：

```text
原始数据集 → SQL/Python 处理 → 处理逻辑 → 仪表盘查询
```

用于多源 Join、复杂 Python 处理。

湖仓目录和流式数据源可以预留模型，但不作为当前第一阶段必做项。对象存储中的已上传文件属于文件数据源，纳入第一阶段；不要求第一阶段支持湖仓目录级发现和流式消费。

## 5. 查询执行与过滤下推

### 5.1 禁止全量加载

Python 处理不能采用“先把整张 Doris 表和 MySQL 表加载成 DataFrame，再交给 Python”的模式。脚本必须通过平台提供的 `datasets.read(..., filters=...)` 在读取数据集时声明过滤条件，由数据源执行器完成过滤。

平台只负责解析和校验受限的读取请求，不分析任意 Python 代码。页面筛选参数作为脚本参数传入，用户在 `datasets.read` 中明确将参数用于哪个数据集字段。

### 5.2 Query Plan

系统应将组件查询和处理步骤编译成统一的 Query Plan：

```text
数据集输入
  ↓
Python 读取参数
  ↓
临时筛选
  ↓
字段裁剪
  ↓
Python datasets.read 读取请求
  ↓
各输入数据集查询
  ↓
Python/JS 处理
  ↓
聚合、排序、分页
  ↓
组件结果
```

执行器需要区分：

- 已下推条件；
- 可以下推但暂未下推的条件；
- 只能在执行引擎处理的条件；
- Python/JS 自定义函数形成的残余计算。

### 5.3 跨源数据处理策略

按数据规模自动选择策略：

1. 同一个 Doris 或 MySQL 数据源：尽量直接拼接 SQL，让源数据库执行 Join；
2. 一侧过滤后数据较小：先取得 Join Key，再分批传递给另一侧；
3. 两侧数据都较大：要求时间范围、分区条件或预聚合；
4. 无法下推的处理：先物化到外部临时存储，再继续 Python/JS 处理；
5. 预计扫描量超限：阻断执行并提示用户调整条件。

### 5.4 执行计划和预览

数据集编辑器需要提供“预览数据”和“查看执行计划”，至少展示：

- 每个数据源实际生成的 SQL 或请求参数；
- 已下推和未下推的条件；
- 预计扫描行数、扫描字节和返回行数；
- 分区、索引、排序键或执行计划命中情况；
- 各阶段耗时；
- 是否发生跨源数据传输；
- 是否使用临时存储；
- 风险提示和优化建议。

系统不能承诺“必然走索引”，而应通过 EXPLAIN 和实际执行信息验证数据库最终选择的计划。

### 5.5 多数据源组件规则

所有支持数据的组件、图表和卡片都可以消费多数据源预处理后的最终数据集，实际输入数据源数量由用户配置决定。组件不直接实现跨源 Join，而是通过可复用的数据预处理定义完成：

- 只选择一个数据源时，可以直接将其查询结果作为组件输入数据集；
- 选择多个数据源时，必须配置 Python 脚本预处理；同一 JDBC 数据源内部可以先使用标准 SQL 完成过滤、字段转换、聚合和同源 Join，跨数据源 Join 由 Python 完成；
- 多数据源预处理必须将多个输入数据集转换为一个最终数据集；
- 未完成预处理时，不允许保存或发布该组件配置。

数据流统一为：

```text
单数据源：
数据源查询 → 组件

多数据源：
多个数据源查询 → 数据预处理 → 最终数据集 → 组件
```

多数据源配置界面应显示输入数据集、处理方式、处理代码和输出数据集。第一个数据源不再强制承担“主数据集”语义，用户可以在预处理配置中明确 Join、合并、分别计算汇总或其他处理关系。

推荐的交互结构：

```text
数据源
  ├── Doris · 业务数据库 → SQL 输入数据集
  └── MySQL · 用户数据库 → SQL 输入数据集

数据预处理
  └── Python Script Runner

输出
  └── 业务分析结果
```

每个数据源输入节点都必须提供数据集选择和独立预览；读取筛选条件由 Python `datasets.read` 请求声明。用户添加一个数据源后，可以先只预览该数据源的查询结果，不必等待其他数据源或最终预处理完成：

```text
Doris · 业务数据库
  ├── SQL 查询
  ├── Python 读取条件
  └── [预览该数据源]

MySQL · 用户数据库
  ├── SQL 查询
  ├── Python 读取条件
  └── [预览该数据源]
```

多数据源预处理的最终预览则在所有输入数据源配置完成后进行：

```text
单数据源预览
  ↓
多个输入数据源
  ↓
SQL / Python / JS 预处理
  ↓
最终数据集预览
```

### 5.6 预览流程与强制筛选

预览分为“输入数据源预览”和“最终数据集预览”两种：

### 输入数据源预览

每个数据源卡片均提供独立的“预览数据”按钮。点击后，系统只校验当前数据源：

1. 校验 JDBC SQL 或 Aloudata 指标视图配置；既有指标&维度路径继续走原校验逻辑；
2. 检查 Python `datasets.read` 读取请求中的数据集、字段和筛选条件；
3. 通过 `EXPLAIN` 或数据源统计检查扫描预算；
4. 校验通过后执行当前数据源查询；
5. 在抽屉或面板中显示结果。

缺少合法的 `datasets.read` 读取条件或预计扫描量超出预算时，不执行查询，直接定位到脚本读取请求，并提示用户缩小时间范围、增加条件或明确确认。`LIMIT` 较小的查询不因缺少 `WHERE` 关键字而被机械阻止。

### 最终数据集预览

点击预处理区域的“预览结果”时，系统先检查所有输入数据源和 Python `datasets.read` 读取请求是否配置完成，然后执行：

```text
读取各输入数据源
  ↓
执行 Python 读取请求中的筛选条件
  ↓
执行 SQL / Python / JS 预处理
  ↓
返回最终数据集的预览结果
```

预览结果面板包含：

- 数据预览：默认展示有限行数；
- 字段结构：字段名、类型和示例值；
- 执行信息：查询耗时、扫描量、返回量和下推状态；
- 处理日志：各输入数据源行数、处理节点和输出行数。

预览结果属于制作阶段的临时结果，不等同于发布后的完整运行结果。系统应限制预览返回行数、扫描量、执行时间和资源使用，并在达到限制时给出明确提示。

## 6. SQL、Python、JS 处理能力

### 6.1 使用边界

建议将处理能力分为：

- SQL：仅对 JDBC 数据源开放，优先用于过滤、字段转换、聚合和同源 Join；
- Python：用于复杂多数据集组合、统计分析和业务规则；
- JS：用于轻量字段转换和展示前处理。

SQL/Python/JS 都不能绕过统一的数据集执行层直接连接数据库。

### 6.2 Python 处理

Python 节点用于多数据源脚本预处理和复杂分析，应支持：

- 接收多个逻辑数据集；
- 按批次接收已过滤的数据集；
- 使用 Polars/Pandas 进行内存或流式处理；
- Arrow、Parquet 等列式批量传输；
- `print` 日志；
- 断点或分阶段调试；
- 样本预览；
- 运行超时、内存和行数限制；
- 错误定位到处理节点和代码行。

#### Python 输入数据集目录与脚本别名

用户在配置多数据源 Python 节点时，先从已固化数据集中选择输入，并为每个输入分配脚本别名。`datasetId` 是平台内部稳定 ID，不要求用户在脚本中填写；脚本只使用可读的 `input_name`。

```text
输入数据集
├── orders → MySQL 销售订单数据集
│   ├── user_id       BIGINT
│   ├── order_date    DATE
│   ├── amount        DECIMAL
│   └── order_type    VARCHAR
└── users → Doris 用户信息数据集
    ├── user_id        BIGINT
    ├── user_name      VARCHAR
    └── enabled        BOOLEAN
```

Spring/DataAgent 在运行脚本前向 Runner 注入当前任务可用的输入目录，只包含别名、内部 `datasetId`、Schema、参数定义和能力信息，不包含完整事实数据、数据库凭据或未授权数据集。编辑器应提供输入别名和字段自动补全、字段类型提示、一键插入 `datasets.read` 模板和受限样本预览。

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

标准读取样例：

```python
from mateclaw.filters import Filter

# `datasets` 由 Python Runner 按任务输入目录注入，不从模块导入。

orders = datasets.read(
    input_name="orders",
    columns=["user_id", "order_date", "amount"],
    filters=[
        Filter("order_date", "gte", datasets.params.get("start_date")),
        Filter("order_date", "lte", datasets.params.get("end_date")),
    ],
)
```

`datasets.read` 是带条件的数据集读取 API：读取请求先发送给 DataAgent，由 DataAgent 根据别名解析 `datasetId`，校验 Schema、权限、操作符和资源限制，再生成参数化 JDBC SQL。读取完成后返回的是已经过滤的数据集输入；Python 中对 DataFrame 的二次过滤不再下推。

运行时输入分为两层：

```text
DatasetInputDescriptor（Spring/DataAgent 返回）
  ├── datasetId
  ├── schema
  ├── rowCount / statistics
  └── capabilities

DatasetReadResult（datasets.read 返回；当前阶段）
  ├── schema
  ├── rowCount / statistics
  ├── rows（受限内联批次）
  ├── dataRef（输出结果/后续输入批读预留）
  ├── transport
  └── pushdownReport

DatasetInput（Python SDK 包装）
  ├── schema()
  ├── to_polars()
  ├── to_pandas()
  └── iter_batches()
```

Spring 在任务启动时通过 JSON 传递 Descriptor，不传递事实数据、Python 函数或数据库连接。脚本调用 `datasets.read` 后，DataAgent 才查询并返回受限 `DatasetReadResult`；当前 SDK 消费其中的内联批次，脚本大结果使用 `dataRef` 写入对象存储并供页面受限预览。输入侧根据 `dataRef` 创建远程批次读取器需要独立读取接口，列入后续扩展，不作为本阶段已实现能力。JDBC 数据集 Schema 通过 `DatabaseMetaData` 或零行元数据探测获取，第一阶段 Aloudata 新数据集复用指标视图 Schema，文件数据集读取文件 Schema 或受限样本推断。

Python 运行环境必须与 MateClaw 主 JVM 隔离，避免大数据处理拖垮主服务。本阶段暂定采用“兼容双模式（方案 B）”：旧 Agent Python 保持现有本地执行路径；新仪表盘多数据源脚本使用独立 Python Runner。

现有 Agent Python 分析能力暂不重写，继续保留 `PythonAnalysisTool → LocalCodeExecutorService` 兼容路径。新仪表盘多数据源脚本预处理使用独立的 `PythonExecutionService → PythonRunner` 路径，二者通过任务类型或能力标识区分。新路径不调用旧执行器，也不接受运行时依赖安装参数。旧路径的运行时 `pip install` 暂不改动，但必须标记为兼容模式，并作为后续安全迁移范围。

```text
浏览器
  ↓
Spring / DataAgent
  ├── Query Planner
  ├── 数据源查询与条件下推
  ├── Python 任务管理
  └── 日志与调试接口
        ↓
Python Runner
  ├── Python 解释器
  ├── Pandas / Polars
  ├── 用户脚本沙箱
  └── 批处理执行
        ↓
临时对象存储 / Arrow / Parquet
```

Spring 负责配置、编排、权限、查询计划、任务状态和日志；Python Runner 只负责执行用户处理代码。用户脚本不能绕过统一查询执行层直接连接 Doris、MySQL 或其他数据源。

Python 任务不应把大数据集通过 JSON 传给 Spring 或 Python。小规模预览可以使用受限 JSON，但必须限制输入字节数、行数、列数、嵌套深度和执行时间；正式处理应优先使用 Arrow RecordBatch、Arrow IPC、Parquet 或临时对象存储引用。

建议的传输边界为：

```text
小规模预览：受限 JSON
正式任务：dataRef + Arrow/Parquet
```

受限 JSON 的第一阶段默认限制为：单次输入不超过 10 MB、10,000 行、100 列、嵌套深度 5，预览执行时间不超过 30 秒。超过限制时必须提示用户缩小样本或改用正式任务，不得静默放宽限制。

预览超出限制时，系统应提示用户缩小样本或改用正式任务，不得静默放宽限制。

推荐的数据流为：

```text
用户配置筛选条件
    ↓
Spring 编译各数据源查询
    ↓
执行 EXPLAIN 和资源检查
    ↓
各数据源先完成条件下推
    ↓
按受限批次传给 Python Runner（输入 dataRef 批读为后续扩展）
    ↓
Python 处理多个输入数据集
    ↓
输出最终数据集
```

预览任务采用同步执行并直接返回受限结果；正式查询采用异步任务，支持状态查询、取消、重试和日志读取。

本项目整体采用 Docker 部署。方案 B 下，新 Python Runner 以预构建、版本固定的 Docker 镜像部署，例如 `mateclaw-python-runner:<version>`；开发环境可提供 `local` 模式复用本机 Python。运行新任务时不重新构建镜像，也不在任务内安装依赖。旧 `LocalCodeExecutorService` 继续按现有策略运行，并明确标记为兼容模式。

Docker 环境的推荐部署形态为：

```text
mateclaw-dataagent 容器
    ↓ 任务 API
Python Runner Manager 容器
    ↓ 调度
一次性 Python Task 容器（固定 Runner 镜像）
```

第一阶段在 Docker Compose 下，Runner Manager 容器内部按任务创建隔离进程；后续多租户正式环境再升级为一次性任务容器或 Kubernetes Job。DataAgent 主容器不直接挂载 Docker Socket，避免获得宿主机控制权限。Runner 不要求每个任务都重新创建 Docker 容器，但任务之间不得共享 Python 进程状态。

每个任务必须具备：

- 最大执行时间；
- 最大内存和 CPU；
- 最大输入和输出行数；
- 临时目录和文件访问范围；
- 依赖包白名单；
- 网络访问策略；
- 任务取消能力；
- 运行日志和错误堆栈。

正式环境建议将 Python Runner 作为独立容器或独立服务部署，禁止用户脚本访问宿主机、Spring 凭据和任意外部网络。Runner 使用预构建、版本固定的依赖环境，第一阶段默认提供 `pandas`、`polars` 和 `pyarrow`，不支持任务内 `pip install`；未包含在环境中的依赖应返回明确的不可用错误。

默认情况下只保存 `PyDataset` 定义，不保存最终结果；后续如需缓存，必须采用带 TTL、workspace 和权限绑定的可选缓存。

#### Python 调试交互

用户点击“调试运行”时，流程为：

```text
选择输入数据集
  ↓
配置 Python 读取条件
  ↓
只读取受限样本
  ↓
执行 Python
  ↓
实时查看日志
  ↓
查看输出数据
```

调试面板至少包含：

- Python 代码编辑器；
- 输入数据集和字段结构；
- 运行参数；
- 标准输出；
- 错误堆栈和代码行定位；
- 输出数据预览；
- 输入行数、输出行数和耗时。

#### Python 执行接口

Spring 调用 Python Runner 时传递任务描述、输入目录和短期读取能力，不直接传递未受控的数据库连接信息，也不在脚本启动前预取完整输入或传递大 JSON 数据：

```json
{
  "taskId": "task-001",
  "script": "...",
  "inputs": [
    {
      "name": "detail_input",
      "schema": "..."
    }
  ],
  "parameters": {
    "startDate": "2026-01-01",
    "endDate": "2026-01-31"
  },
  "datasetReadEndpoint": "http://mateclaw-dataagent:18089/dataagent/api/internal/v1/script-tasks/task-001/datasets/read",
  "readToken": "<task-scoped-token>",
  "limits": {
    "timeoutSeconds": 300,
    "maxMemoryMb": 2048,
    "maxOutputRows": 100000
  }
}
```

`readToken` 必须绑定 `taskId`、工作区、允许的输入别名和过期时间，且只能调用内部数据集读取接口。脚本执行到 `datasets.read(input_name, columns, filters)` 时，SDK 才携带该令牌回调 DataAgent；DataAgent 根据别名解析内部 `datasetId`，重新校验权限和 Schema，执行数据源查询并返回受限批次。脚本大结果的 `dataRef` 同样必须绑定任务、工作区和过期时间；输入侧远程 `dataRef` 批读待后续专用接口。Runner 不能提交任意 `datasetId`，也不能根据脚本自行访问数据源。

Runner 返回任务状态、输出数据引用、Schema 和运行统计：

```json
{
  "taskId": "task-001",
  "status": "succeeded",
  "output": {
    "schema": "...",
    "dataRef": "temporary://result-001"
  },
  "metrics": {
    "inputRows": 4046,
    "outputRows": 936,
    "elapsedMs": 420
  }
}
```

任务状态至少包括 `queued`、`running`、`succeeded`、`failed` 和 `cancelled`，并通过 `taskId` 与 `executionId` 区分脚本定义和单次运行。预览与正式查询均应支持幂等、超时、取消、日志读取和失败原因回溯。

#### Python 数据集的执行模型

Python 在仪表盘中仅作为多数据集的中间处理节点，不负责生成或反向改写 JDBC 查询。用户配置后保存的是 `PyDataset` 定义，只有在用户预览或仪表盘查询时，系统才真正执行。

```text
输入数据集 A ─┐
              ├─ PyDataset：分析结果
输入数据集 B ─┘
```

`PyDataset` 定义至少保存：

- 输入数据集引用；
- 输入数据集别名；
- Python 处理逻辑；
- Python 脚本参数声明；
- 输出字段结构；
- 执行资源限制；
- 缓存策略。

默认不保存最终数据结果，执行时按照以下流程运行：

```text
用户点击预览或查询
    ↓
页面参数作为 Python 脚本入参
    ↓
Python 调用 datasets.read 并传入筛选条件
    ↓
各数据源执行过滤、投影和分页
    ↓
获取过滤后的数据
    ↓
执行 Python Join、合并和计算
    ↓
返回最终 PyDataset
    ↓
页面展示
```

例如页面将时间范围和业务类型作为 Python 入参后，脚本分别在各个 `datasets.read` 请求中指定字段过滤：

```text
仪表盘参数：start_date、end_date、business_type

Doris `datasets.read`：
  event_date between params.get("start_date") and params.get("end_date")
  business_type = params.get("business_type")

MySQL `datasets.read`：
  register_date between params.get("start_date") and params.get("end_date")
```

两个数据源分别完成查询后，Python 才接收结果并进行后续处理，不能先把整张表加载成 DataFrame 再筛选。

Python SDK 的每次 `datasets.read` 调用拿到对应来源已经过滤后的受限批次，再由用户使用 Polars/Pandas 做预处理；脚本输出超过内联阈值时才通过受控 `dataRef/outputRef` 传输：

```python
events = datasets.read(
    input_name="events",
    columns=["user_id", "amount"],
    filters=[...],
).to_polars()
users = datasets.read(
    input_name="mysql_users",
    columns=["user_id", "user_name"],
    filters=[...],
).to_polars()

result = (
    events
    .select("user_id", "amount")
    .join(users.select("user_id", "user_name"), on="user_id")
)

return result
```

Runner 不在任务开始时一次性接收所有输入。脚本每次调用 `datasets.read` 时才触发对应数据源查询，查询完成后 SDK 消费该次受限批次；输入侧远程 `dataRef` 读取待后续接口完成后再启用。

需要区分两类筛选：

- 页面筛选：来自仪表盘筛选器、时间筛选、页面 Tab 和组件参数，先作为脚本参数传入，再由脚本在 `datasets.read` 中明确用于哪个输入字段；
- Python 内部筛选：写在普通 Python 代码中的条件，发生在数据读取后，不自动下推。

为了保证下推，需要下推的条件必须通过 `datasets.read(..., filters=...)` 表达，不能写成读取完成后的 DataFrame 过滤逻辑。

PyDataset 定义与某次运行实例分离：

```text
PyDatasetDefinition
  ├── inputs
  ├── parameters
  ├── script
  ├── outputSchema
  └── executionPolicy

PyDatasetExecution
  ├── executionId
  ├── inputQueryPlans
  ├── pushedFilters
  ├── pythonLogs
  ├── outputDataRef
  └── metrics
```

其中：

- `PyDatasetDefinition` 表示保存的处理逻辑；
- `PyDatasetExecution` 表示一次预览或查询产生的运行实例。

#### PyDataset 预览

预览需要支持两个层级：

1. 输入数据集预览：分别执行并查看 MySQL、Doris、Aloudata 等输入数据；
2. 最终结果预览：输入数据集查询完成后执行 Python 处理，再查看最终 PyDataset。

最终结果预览应展示输入行数、处理前后数据量、Join/合并结果、输出字段、Python 日志、各数据源实际查询条件和筛选下推状态。

实现边界：Runner 首先支持小结果的受限 JSON 返回，脚本全局 `result` 与标准输出日志分离，并受 `max_result_bytes` 限制；大结果由 Runner 惰性转换为 Parquet，通过 DataAgent 内部短期令牌接口写入受控对象存储并返回 `outputRef`。ObjectRef 下载和页面表格/图表展示仍需后续接入；上传失败或超过资源上限时返回明确错误，不把大结果伪装成成功。

现有 Agent Python 分析入口继续保留为兼容路径；仪表盘脚本预处理新增 `PythonExecutionService` 和独立 `PythonRunner`，不要求一次性重写旧执行器：

```text
PythonAnalysisTool
  ↓
PythonExecutionService
  ↓
PythonRunner
```

### 6.3 任意 Python 代码的边界

任意 Python 代码无法被系统可靠地静态分析出所有过滤条件。因此需要下推的过滤条件必须通过平台提供的 `datasets.read(..., filters=...)` 接口表达；读取完成后的 DataFrame 过滤不下推。

用户配置流程建议为：

```text
选择数据集 → 配置临时筛选 → 选择字段 → 配置 JDBC SQL/Python 处理 → 预览
```

平台当前不负责自动生成 SQL 或 Python/JS 脚本；用户可以借助外部大模型生成简单草稿，确认后粘贴到编辑器中进行校验、预览和保存。仪表盘脚本预处理第一阶段只实现 Python Runner，JS 处理保留为后续扩展能力。

### 6.4 开源方案选型与推荐架构

本节只选择能够嵌入或围绕 Python Runner 使用的成熟开源组件，不改变以下产品边界：

- JDBC 数据源才支持用户配置标准 SQL；
- 不引入 Trino 作为当前阶段的跨源查询引擎；
- 多数据源通过脚本节点组合为一个最终数据集；
- 现有 `PythonAnalysisTool → LocalCodeExecutorService` 保持兼容；
- 新 Runner 不支持运行时 `pip install`，依赖通过固定运行环境提供。

#### 方案 A：自研 Runner + Apache Arrow/Parquet

由 DataAgent 自己实现任务管理、脚本执行、资源限制和结果引用，使用 Apache Arrow/Parquet 作为数据交换格式。Apache Arrow Flight 提供基于 gRPC/Arrow IPC 的高性能批量数据 RPC，可用于 Runner 与 DataAgent 之间的可选传输层；Parquet 适合作为临时结果和重试输入的持久化格式。

优点：

- 与现有 Java/Spring 架构最容易集成；
- 协议、权限、任务状态可以完全按 MateClaw 设计；
- 不引入额外的查询引擎。

缺点：

- 需要自行实现批处理、Schema 校验、背压、取消和失败恢复；
- 仅使用 Arrow/Parquet 并不会自动提供跨源 Join 或内存优化；
- 长期维护成本较高。

参考：[Apache Arrow Flight RPC](https://arrow.apache.org/docs/format/Flight.html)。

#### 方案 B：独立 Runner + Polars + Apache Arrow/Parquet（推荐）

Runner 负责隔离和任务生命周期，Polars 负责脚本中的列式数据处理，Arrow/Parquet 负责批量输入输出。Polars 提供 Lazy API 和 streaming execution，可以按批处理超出内存的数据；Polars 与 Arrow 之间支持低拷贝数据交换。

建议的职责划分：

```text
DataAgent
  ├── 查询编排、权限、参数绑定、任务状态
  ├── JDBC SQL / Aloudata 查询
  └── 脚本大结果写入 Arrow/Parquet 或生成受控 dataRef（输入侧当前返回受限批次）
          ↓
Python Runner
  ├── 一次性隔离进程或容器
  ├── 预构建 Python 环境
  ├── Polars/Pandas 兼容处理
  └── 输出 Arrow/Parquet + Schema + Metrics
```

优点：

- 与“多数据源 + Python 脚本预处理”直接匹配；
- 不需要引入 Trino 或新的常驻查询集群；
- 列式批处理和 streaming 能降低全量加载风险；
- 依赖环境可以制作成固定镜像，不需要运行时安装依赖；
- 后续可以在不改变 Runner 契约的情况下替换或增加 DuckDB 等内部库。

缺点：

- 脚本仍可能写出无法流式执行的操作，Runner 必须报告内存和执行风险；
- Polars 的部分操作会回退到内存执行，不能把“支持 streaming”当成绝对保证；
- 需要 DataAgent 自己实现脚本安全沙箱和任务治理。

参考：[Polars Streaming](https://docs.pola.rs/user-guide/concepts/streaming/)、[Polars Arrow interoperability](https://docs.pola.rs/user-guide/misc/arrow/)。

#### 方案 C：独立 Runner + DuckDB + Arrow/Parquet

Runner 使用 DuckDB 作为内部的嵌入式列式处理引擎，Python 脚本通过 DuckDB API 或 DataFrame/Arrow API 组合多个输入。DuckDB 可以直接读取 Parquet、Arrow、Pandas 和 Polars 数据，并将结果导出为 Arrow 或 Parquet。

注意：DuckDB SQL 只作为 Runner 内部实现选项，不新增独立的用户 SQL 配置能力；用户可配置的 SQL 仍只属于 JDBC 数据源。

优点：

- 对多输入数据集的 Join、聚合和列式扫描支持较好；
- 对 Parquet/Arrow 输入输出简单；
- 可作为 Polars 方案的补充，而不是新建分布式集群。

缺点：

- 引入第二套 SQL 语义，调试和权限边界更复杂；
- 如果允许用户直接编写 DuckDB SQL，会与“JDBC 才支持 SQL”产生产品歧义；
- 仍不能替代任务隔离、队列和资源治理。

参考：[DuckDB Python API](https://duckdb.org/docs/stable/clients/python/overview)、[DuckDB Data Ingestion](https://duckdb.org/docs/current/clients/python/data_ingestion)。

#### 方案 D：Apache Beam + Flink/Spark Runner

使用 Apache Beam 定义跨源批处理 Pipeline，再由 DirectRunner、FlinkRunner 或 SparkRunner 执行。Beam 提供统一的 Pipeline/PCollection/Transform 模型，并支持多个执行后端。

优点：

- 适合大规模、长时间、批流一体的任务；
- 可将执行后端与 Pipeline 定义解耦；
- 生态和分布式容错能力较完整。

缺点：

- 需要引入 Beam SDK、Runner、集群或额外运行时；
- 任务启动、调试和资源治理明显重于当前需求；
- 不适合作为第一阶段的同步预览执行器。

参考：[Apache Beam Programming Guide](https://beam.apache.org/documentation/programming-guide/)、[Apache Beam Runners](https://beam.apache.org/documentation/)。

#### 选型结论

推荐采用“方案 B 为主、方案 A 的传输契约为基础、方案 C 作为内部可选优化”的组合：

1. 用自定义 `PythonRunner`、`ScriptTask`、`ExecutionResult` 和 `dataRef` 契约保持 MateClaw 的权限和任务边界；
2. 用 Arrow/Parquet 作为标准数据交换格式，必要时再增加 Arrow Flight；
3. Runner 默认提供 Polars/Pandas 固定依赖环境；
4. 多源 Join、清洗和聚合优先使用 Polars；
5. 第一阶段 Runner 镜像不安装、不依赖 DuckDB，跨源处理统一使用 Polars/Pandas；
6. 只有出现可复现的 Polars 性能或能力缺口时，才另行评估 DuckDB，且不改变“SQL 只属于 JDBC 数据集”的产品边界；
7. 不引入 Trino 或 Beam 集群；当任务规模达到独立分布式计算需求时，再评估专用执行平台。

推荐架构：

```text
多个已固化数据集
    ↓
Datasource Adapter
    ├── JDBC → 标准 SQL（当前数据源内）
    └── Aloudata/API → 数据源查询
    ↓
Query/Resource Gate
    ↓
Arrow/Parquet + dataRef
    ↓
PythonExecutionService
    ↓
一次性 Python Sandbox（第一阶段仅 Polars/Pandas）
    ↓
最终数据集 + Schema + Metrics
    ↓
多个仪表盘组件复用
```

该选型只影响新仪表盘脚本预处理路径，不要求立即改写旧的 `LocalCodeExecutorService`。旧路径继续服务已有 Agent 分析，新路径逐步承接多数据源仪表盘任务。

## 7. 组件、容器和 Tab

### 7.1 组合卡片/卡片容器

需要新增通用卡片容器，允许组合：

- KPI 卡片；
- 图表；
- 图标；
- 筛选项；
- 时间筛选；
- AI 分析；
- 操作类组件；
- 其他容器。

容器支持：

- 拖拉拽添加子组件；
- 容器自身大小调整；
- 内部布局调整；
- 统一标题、边距、背景和边框；
- 容器级参数和事件作用域；
- 添加页签并为每个页签配置不同子组件。

### 7.2 指标分组卡片

不同业务指标分组都应使用通用指标分组卡片实现，而不是为某个业务场景增加专用组件。

指标分组卡片支持：

- 一个卡片内放置多个指标；
- 指标拖拉排序；
- 展示列名配置；
- 指标值、单位、趋势和辅助说明配置；
- 每个指标绑定不同字段或计算表达式；
- 根据页面 Tab 或参数切换指标集合。

## 8. 参数、事件和组件联动

### 8.1 声明式运行时

组件联动采用 Dashboard Runtime 的声明式模型：

```text
组件产生标准事件
    ↓
动作将事件映射为仪表盘参数
    ↓
参数绑定到组件查询
    ↓
相关组件重新查询或刷新
```

组件内部 Hook 只负责接入运行时，不作为用户配置协议。

### 8.2 公共交互协议

协议至少包含：

- 参数名称、类型和默认值；
- 参数作用域：页面、仪表盘、容器或组件；
- 事件来源和事件类型；
- 事件 payload；
- 动作类型；
- 参数映射；
- 组件绑定关系；
- 是否重新查询、刷新或仅更新展示；
- 防抖、并发和失败处理。

### 8.3 筛选器作用范围

多数据集配置完成后，筛选器和时间筛选必须提供“作用范围”配置，决定哪些 Python 任务或组件可以收到该参数；具体参数用于哪个数据集字段，由 Python `datasets.read` 的 `filters` 明确声明，不在产品侧逐个配置字段绑定：

```text
筛选器：业务类型

作用范围：
☑ Doris · 业务明细
☐ MySQL · 用户信息
☑ Aloudata · 业务指标视图
```

作用范围应支持：

- 按数据集选择；
- 按数据源选择；
- 全选和清空；
- 只展示当前数据集支持的参数类型和筛选操作；
- 不支持当前参数的数据集置灰并说明原因；
- 参数具体用于哪个数据集字段，由 Python `datasets.read(..., filters=...)` 明确声明，不在产品侧配置字段绑定。

系统内部需要区分筛选发生的阶段，但产品界面不需要暴露底层实现术语：

```text
用户选择作用范围
    ↓
将已绑定的筛选条件追加到 JDBC SQL 并下推到对应数据源
    ↓
数据源查询
    ↓
SQL/Python/JS 预处理
    ↓
必要时执行最终结果筛选
```

界面应对每个作用范围目标显示状态，例如“将在数据源查询阶段执行”或“将在预处理后执行”；不支持该参数的数据集应显示不可用原因。

### 8.4 通用操作组件

操作组件至少包括：

- 查询；
- 重置；
- 刷新；
- 导出；
- 打开详情；
- 页面导航；
- Tab 切换；
- 调用受控业务 API。

任何 API 类动作必须经过后端白名单、权限和审计控制。

## 9. Trino 边界

当前不建议将 Trino 作为 MateClaw 核心依赖直接引入。

Trino 的价值在于统一查询多个数据源、进行分布式执行、条件下推、动态过滤和跨源 Join。但它需要独立 Coordinator/Worker、Catalog、权限、资源组、内存和 Spill 管理，运行和运维成本明显高于当前按数据源查询、再由 Python 预处理的执行方式。

当前建议：

1. 先实现页面参数传入、受限 `datasets.read` 读取接口、数据源 Adapter、Pushdown Report 和 Python Batch Executor；
2. 将数据源查询与 Python 预处理解耦，筛选条件由各数据源执行器下推；
3. 暂不实现通用的跨源 Pushdown Engine；
4. 未来确有联邦查询需求时，再将 Trino 作为可选的 `TrinoQueryEngine` 接入，而不是让仪表盘直接依赖 Trino API。

```text
DatasourceQueryExecutor
  ├── JdbcQueryExecutor
  ├── AloudataQueryExecutor
  └── TrinoQueryEngine（未来可选）
```

只有在数据源数量、跨源 Join 规模和并发量达到独立查询集群的需求时，再评估部署 Trino。

## 10. 权限、安全和资源保护

必须在数据源、数据集、字段、查询和处理脚本多个层面控制权限：

- 数据源访问权限；
- 数据集可见范围；
- 行级权限；
- 字段级权限和脱敏；
- JDBC 只读账号；
- API 白名单和鉴权；
- SQL 只读校验；
- Python/JS 沙箱；
- 查询超时、最大行数和最大扫描量；
- 并发限制和队列；
- 审计日志。

对大数据集，必须在执行前提供阻断或确认机制，不能让用户一次操作直接触发全表扫描或把大量数据加载到 JVM。

## 11. 分阶段建设建议

### 第一阶段：打通通用仪表盘闭环

- 既有 Aloudata 指标/维度查询保持兼容，不作为本期新建数据集能力；
- Aloudata 已有指标视图的选择与查询；
- JDBC 统一数据源模型；
- 文件数据源（Excel、CSV、JSON、Parquet 和对象存储文件）的上传、Schema 识别和受限读取；
- API 数据集参数映射和分页；
- 数据源能力声明；
- 组合卡片和指标分组卡片；
- 页面/Tab；
- 仪表盘参数和筛选联动；
- 查询、重置、刷新、导出操作；
- 数据预览；
- Python `datasets.read` 条件读取：JDBC 条件下推、API 参数透传、文件数据集受控读取；
- HTTP/API 和文件输入与 Python 多数据源预处理的统一 `DatasetInputDescriptor`；
- 独立 Python Runner（方案 B）；
- 受限预览 JSON、Arrow/Parquet 分批传输；
- Python 任务状态、取消、超时和资源限制；
- 固定依赖环境（不支持运行时 pip install）；
- Docker Compose 下 Runner 容器隔离进程；
- 预览同步执行、正式查询异步执行；
- 兼容现有 Aloudata 仪表盘 Schema。

### 第二阶段：完善通用数据处理能力

- Query Plan 和 Pushdown Report 完善；
- 扫描预算和 EXPLAIN 资源门禁；
- Python 多数据集分批处理；
- Python 打印和调试；
- 查询缓存和物化数据集；
- 扫描量和资源保护。

### 第三阶段：平台化扩展

- 湖仓和对象存储数据源；
- CDC/增量同步；
- 流式数据源；
- JS 轻量处理；
- Trino 可选查询引擎；
- 更完整的血缘、质量和成本分析。

## 12. 验收标准

### 用户配置验收

- 所有用户可以通过拖拉拽创建页面、容器和指标分组卡片；
- 可以配置页面级和组件级 Tab；
- 可以配置时间范围、筛选器和查询/重置按钮；
- 可以将组件绑定到统一参数；
- 切换 Tab 后，绑定参数和组件数据能够按配置刷新；
- 可以配置表格列名、指标顺序和展示方式；
- 可以预览数据并理解当前查询条件。

### 执行验收

- Doris 和 MySQL 查询不会默认全量拉取；
- 临时筛选条件能够在数据源读取前下推；
- Python 能够接收多个数据集并分批处理；
- 能够展示已下推、未下推和残余处理条件；
- 能够查看 SQL、请求参数和执行计划；
- 超过扫描、内存、行数或耗时限制时能够阻断并提示；
- Python/JS 失败不会拖垮 MateClaw 主 JVM。

### 通用性验收

- 组件、容器、参数、事件和动作不包含具体业务专用逻辑；
- 新增一个类似报表不需要修改业务组件源码；
- 新增数据库主要通过 JDBC Dialect 和能力声明完成；
- 查询引擎可以在不修改仪表盘配置协议的情况下替换为 Trino。

## 13. 整体技术解决方案与开源选型

前面的章节描述产品能力，本节补充可以落地的技术方案。总体原则是：MateClaw 负责产品配置、租户权限、资源治理、公开 API 和仪表盘运行时；成熟开源项目负责数据交换、列式处理、对象存储和可选的 BI 能力。任何开源项目都通过 Adapter 或 Service 接入，不能直接成为仪表盘配置协议。

### 13.1 总体架构

```text
mateclaw-dataagent-ui
  ├── 数据源/数据集配置器
  ├── 仪表盘编辑器
  └── 预览、发布、联动运行时
          ↓ HTTP/SSE
DataAgent Application
  ├── DatasourceService / MetadataService
  ├── DatasetCatalogService
  ├── DashboardSchemaService
  ├── QueryService / QueryPlanner
  ├── PermissionService / ResourceGate
  ├── ScriptTaskService
  └── CompatibilityService
      ├── JDBC Adapter（标准 SQL）
      ├── Aloudata Adapter（指标/视图）
      └── API/File Adapter
          ↓
PostgreSQL：定义、Schema、权限、任务状态
对象存储：临时 Arrow/Parquet、导出文件、日志附件
          ↓
Python Runner Sandbox：多数据源脚本预处理
```

### 13.2 按功能域落地

| 功能域 | MateClaw 方案 | 可复用开源组件 | 关键边界 |
| --- | --- | --- | --- |
| 数据源连接 | `DatasourceService`、连接测试、凭据加密、能力声明 | JDBC Driver、Apache Arrow | 连接凭据只在服务端使用；前端只看能力和元数据 |
| 元数据发现 | `MetadataService` 同步表、字段、类型、估算行数 | JDBC `DatabaseMetaData` | 元数据不是事实数据；同步失败要有状态和重试 |
| 固化数据集 | `DatasetCatalogService` 保存数据集 ID、字段 Schema、来源引用 | PostgreSQL | 仪表盘引用 `datasetId`，不复制数据集定义到每个组件 |
| JDBC 查询 | `QueryService` + 参数绑定 + 只读 AST 校验 | JDBC、JSqlParser 或同类 SQL Parser | 只允许 JDBC；禁止拼接参数；强制超时、分页、扫描预算 |
| Aloudata 查询 | `AloudataAnalysisViewAdapter`：AnyMetrics 元数据 + Semantic 结果查询 | 现有 Aloudata Client | 指标视图优先走 Semantic API；JDBC 虚拟表仅作兼容；结果转换为统一 `DatasetBatch` |
| 多源预处理 | `ScriptTaskService` 编排多个输入数据集 | Polars、Arrow、Parquet | Join/清洗/聚合在脚本节点完成，输出一个最终 Schema |
| 批量传输 | `dataRef`、Schema、TTL、租户范围、内容摘要 | Apache Arrow、Parquet、可选 Arrow Flight | 预览可用受限 JSON，正式任务不能传大 JSON |
| 临时对象存储 | `ObjectRefService`、过期清理、权限签名 | MinIO 或兼容 S3 的对象存储 | 临时对象必须绑定 workspace、task 和过期时间 |
| 仪表盘编辑 | 现有 Schema 编辑器、组件注册表、版本化 Schema | 不直接嵌入外部 BI UI | 组件只消费统一数据结果，不直接访问数据源 |
| 联动运行时 | `Parameter/Event/Action` 声明式协议 | 前端状态管理库可选 | 参数传递由服务端校验，脚本读取条件由 `datasets.read` 声明 |
| 预览与执行 | `PreviewService`、`ExecutionService`、Pushdown Report | EXPLAIN、Arrow/Parquet | 预览和正式查询使用同一 QuerySpec，不同资源上限 |
| 发布与权限 | `WorkspaceGuard`、数据集/字段/组件/脚本授权 | Superset 的 RBAC 仅作参考 | 权限判断必须在 Service/Adapter 层兜底 |
| 导出与报告 | 异步导出任务、结果文件引用、审计记录 | Parquet、Apache POI、PDF 工具链 | 导出不能绕过原始数据权限和行数限制 |
| 兼容迁移 | Schema `version`、旧 Aloudata DTO Adapter、双写/只读兼容期 | — | 先兼容读取，再逐步迁移新配置；不能一次性改旧数据 |

### 13.3 开源整体路线选项

#### 选项 A：以 Apache Superset 作为 BI 核心

Superset 已提供数据集、图表、仪表盘、SQL Lab、REST API 和细粒度角色/数据源权限，可显著减少看板基础设施开发。官方文档也明确支持 Dataset API、Dashboard API、SQL Lab 权限和数据源级访问控制。

优点：看板、图表、权限和 SQL 能力成熟，交付速度快。

缺点：需要适配 Superset 的对象模型和安全模型；多源脚本 DAG、DataAgent 任务状态、Aloudata 语义层和现有 Schema 仍需自研；深度定制编辑器会形成二次开发分支。

适合：希望快速获得通用 BI 能力，并接受 Superset 作为主要产品界面的场景。

参考：[Superset 文档](https://superset.apache.org/docs/)、[Superset Dataset API](https://superset.apache.org/developer-docs/api/datasets/)、[Superset Security](https://superset.apache.org/admin-docs/security/)。

#### 选项 B：以 Metabase 嵌入作为看板核心

Metabase 提供 Dashboard、Question、Query Builder 和嵌入能力，也支持可编辑仪表盘的嵌入模型。

优点：嵌入和基础看板能力上手快，适合将标准分析页面放入现有应用。

缺点：多数据源脚本预处理、Aloudata 指标视图、当前自定义组件和声明式联动仍需在外部实现；官方文档显示交互/可编辑嵌入存在版本和授权边界，不能默认视为 OSS 核心能力。

适合：主要需求是嵌入标准 BI 页面，而不是建设 MateClaw 自己的看板编辑平台。

参考：[Metabase Embedding](https://www.metabase.com/docs/latest/embedding/introduction)、[Metabase Editable Dashboard](https://www.metabase.com/docs/latest/embedding/dashboard)。

#### 选项 C：MateClaw 原生产品层 + 开源执行组件（推荐）

保留现有 DataAgent UI、Dashboard Schema、Aloudata 集成和权限体系，只引入成熟的底层组件：Arrow/Parquet 负责数据交换，Polars 负责脚本预处理，MinIO 负责临时对象，JDBC Parser/Driver 负责 JDBC SQL 校验和执行。新能力通过 `DatasourceAdapter`、`QueryService`、`PythonRunner` 和 `ObjectRefService` 接入。

优点：最符合当前产品差异化能力；不被 Superset/Metabase 的对象模型绑死；可以兼容旧 Schema 和旧 Python 执行器；不需要引入 Trino 或分布式集群。

缺点：仪表盘编辑器、任务治理、权限和发布流程仍需要 MateClaw 自己建设；需要严格控制第一阶段范围。

适合：MateClaw 要成为自己的数据分析和洞察产品，而不仅是嵌入第三方 BI 的场景。

#### 选项 D：独立语义层（当前不采纳）

Cube、dbt MetricFlow 等可以提供独立指标模型和预聚合能力，但 MateClaw 已经使用 Aloudata 作为指标、维度和指标视图语义层。当前不再引入第二套语义层，避免指标口径、权限和 Schema 重复维护。

只有未来出现 Aloudata 无法覆盖的 JDBC 指标建模或统一预聚合需求时，才单独评估是否补充外部语义层；这不是当前阶段的建设项。

### 13.4 推荐选项与边界

推荐选项 C，并采用以下组合：

```text
MateClaw 原生 UI / Schema / 权限 / 联动
        + JDBC / Aloudata / API Adapters
        + Arrow/Parquet dataRef
        + Polars Python Runner
        + MinIO 或兼容 S3 对象存储
```

明确不在当前范围内的能力：

- 不把 Superset 或 Metabase 作为主产品 UI；
- 不引入 Trino、Beam 集群或其他外部语义层作为第一阶段强依赖；
- 不开放非 JDBC 数据源的用户 SQL 配置；
- 不支持运行时 `pip install`；
- 不在平台内自动生成 SQL/脚本；用户可借助外部大模型生成草稿后再提交平台校验；
- 不允许组件直接访问数据源或绕过 `QueryService`。

### 13.5 推荐实施顺序

1. **契约层**：定义 `DatasetRef`、`QuerySpec`、`DatasetBatch`、`ScriptTask`、`ExecutionResult`、`ObjectRef` 和 `CapabilityDescriptor`。
2. **数据源层**：将现有 Aloudata 查询包装为 `AloudataAdapter`，新增 JDBC Adapter 和标准 SQL 只读校验。
3. **数据集层**：以数据源已固化的数据集为输入，建立可复用 `DatasetCatalogService`，禁止在组件中重复保存数据源细节。
4. **执行层**：先实现受限预览 JSON，再实现 Arrow/Parquet + `dataRef`；接入独立 Python Runner，旧 `LocalCodeExecutorService` 保持原路径。
5. **仪表盘层**：将组件绑定到数据集或 Python 输出，并向脚本传递 typed parameters，保留旧 Schema 读取兼容。
6. **运行时层**：实现参数、事件、动作、刷新依赖、错误状态和异步任务状态。
7. **治理层**：补齐权限、审计、扫描预算、对象 TTL、取消、重试、导出和发布回滚。
8. **扩展评估**：只有在查询并发和任务规模达到明确阈值后，再评估 Trino、Beam 或其他分布式平台；指标语义继续复用 Aloudata。

### 13.6 方案验收增量

在原有产品验收标准之外，技术方案还必须满足：

- 旧 Aloudata Schema 可以继续读取和渲染；
- 新 JDBC SQL 只能通过参数绑定执行，且无法执行写操作；
- 多数据源脚本任务可以追踪状态、取消、重试和日志；
- 预览 JSON 超限时明确失败，不自动放大限制；
- 正式任务的大结果使用 Arrow/Parquet 或受控 `dataRef`；输入读取当前受控为批次，远程 `dataRef` 批读待后续扩展；
- Runner 无法读取未授权数据源、凭据或其他工作区对象；
- 固定依赖环境不需要运行时联网安装 Python 包；
- 同一 `QuerySpec` 在预览和发布运行中保持语义一致；
- 新增类似报表只需新增数据集、脚本和 Schema 配置，不修改业务组件源码。

### 13.7 JDBC SQL 解析、校验与编译器选型

#### 14.7.1 先区分 Parser、Validator 和 Compiler

“标准 SQL”不等于必须引入一个统一的 SQL 编译器。对于本产品的 JDBC 数据源，SQL 最终仍然由目标数据库执行，因此第一阶段需要的是：

```text
用户 SQL
  → Parser：生成 AST，检查语法
  → Validator：检查只读语句、表/字段、参数和权限
  → Dialect Profile：检查目标 JDBC 数据源支持的语法
  → JDBC：交给目标数据库执行
```

- **Parser**：将 SQL 转换为抽象语法树（AST），不能只依赖字符串搜索 `WHERE`、`SELECT` 等关键字。
- **Validator**：基于 AST 做单语句、只读、表字段白名单、参数和资源限制校验。
- **Compiler/Planner**：进一步将 SQL 转成关系代数，进行逻辑优化，再生成目标数据库方言 SQL。只有在平台要建立跨数据源统一查询模型时才需要它。

因此，文档中的“标准 SQL”应解释为“平台规定的安全 SQL 子集 + 当前 JDBC 数据源的方言能力”，不承诺 MySQL、Doris、StarRocks 等数据库之间完全无差异。当前不引入 Trino，也不把 DuckDB 作为 JDBC SQL 的执行引擎。

#### 14.7.2 开源选项

| 选项 | 定位 | 优点 | 代价与风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| **JSqlParser** | Java SQL Parser/AST | 轻量、Java 原生、无原生运行时；适合识别 `SELECT`、`WITH`、表、字段、函数和参数，并禁止 DDL/DML | 不负责完整的跨库类型推导、成本优化和执行计划 | **当前推荐** |
| **Apache Calcite** | SQL Parser + Validator + Relational Planner | 可解析、校验、转换为关系代数并基于规则/成本优化，适合建设统一查询编译器 | 需要维护 Schema、Catalog、类型系统、Planner、规则和方言；会显著增加 DataAgent 复杂度 | 后续按需引入 |
| **SQLGlot** | Python Parser/Transpiler/Optimizer | 多方言支持较广，适合 Python 侧 SQL 转换和分析 | 引入 Python 依赖；跨方言转换可能改变语义，不适合作为 Java/JDBC 主链路唯一标准 | 仅作 Python 侧备选 |

#### 14.7.3 推荐落地方式

第一阶段在 `mateclaw-dataagent` 内增加独立的 `SqlValidationService`（建议放在独立 package/module），使用 JSqlParser 生成 AST；实际 SQL 仍通过对应 `DatasourceAdapter` 的 JDBC 连接执行。建议接口保持与具体 Parser 解耦：

```java
interface SqlValidationService {
    ValidatedSql validate(SqlRequest request);
}
```

`SqlRequest` 至少包含 `datasourceId`、SQL 文本、参数定义和查询资源限制；`ValidatedSql` 至少返回规范化 SQL、引用的表/字段、SQL 摘要（digest）和校验警告。这样将来可在不改变 `QueryService` 契约的情况下，把 JSqlParser 替换或补充为 Calcite。

必须执行的校验包括：

1. 只允许单条只读查询，默认允许 `SELECT` 和 `WITH`；拒绝 `INSERT`、`UPDATE`、`DELETE`、`DROP`、`ALTER`、`CALL`、`SET` 等写操作或会话操作。
2. 参数采用 `:name` 等命名参数，在服务端转换为 JDBC 占位符并单独绑定值，禁止字符串拼接。
3. 表、视图和字段必须与当前数据源元数据或已固化数据集匹配，并经过 workspace/租户权限校验。
4. 设置查询超时、最大返回行数、分页和扫描预算；预览与正式执行使用相同 `QuerySpec`，只调整资源上限。
5. 保存原始 SQL、规范化 SQL、Parser 版本和 digest，便于审计、缓存和问题定位。

#### 14.7.4 何时升级为真正的 SQL Compiler

只有出现以下需求时，才评估在独立 `SqlCompiler` 模块或服务中引入 Apache Calcite：

- 用户需要跨 JDBC 数据源直接 Join，而不是通过 Python 脚本节点组合；
- 平台要提供与数据源无关的统一查询模型，并自动生成 MySQL/Doris/StarRocks 等方言 SQL；
- 需要统一的关系代数优化、成本估算、联邦查询计划或复杂下推策略。

在此之前引入 Calcite 会使 Spring 主进程承担不必要的 Planner、Schema 和方言维护成本；不建议为了“标准 SQL”这个名称而强制依赖它。
