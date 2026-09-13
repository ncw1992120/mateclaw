# 通用洞察仪表盘设计文档 · 综合评审（胖虎）

> 评审对象：`docs/策略解读/design.md`（通用洞察仪表盘能力设计，约 51KB）
> 评审日期：2026-09-11
> 评审视角：产品设计 / 用户体验 / 解决方案与开源选型
> 方法：先对照 `mateclaw-dataagent` 真实代码与产品原型核实"兼容性/现状承诺"是否成立，再给观点与业界对照。

---

## 0. 一句话总体判断

这是一份**架构素养很高**的蓝图：连接/数据源/数据集/查询四层抽象、数据源能力声明、展示/控制/操作分离、惰性数据集执行、独立 Python Runner 沙箱、QueryPlan/下推报告、分阶段建设——这些都是成熟 BI 平台才会认真考虑的点，且大量引用（`AloudataService`、`InsightDashboardSchema`、`LocalCodeExecutorService`、`PythonAnalysisTool`）在代码里**真实存在**，所以"兼容旧路径"的承诺基本可信。

但文档存在三个**内在矛盾**和若干可优化的选型：

1. 产品原则"面向所有用户"与"多源必须写 Python 预处理"直接冲突；
2. 文档把 AI 生成能力"外包给外部大模型+粘贴"，而产品现状与 `策略解读-prototype.html` 原型里**满屏都是"AI 智能解读 / 重新生成"**——自我阉割了最核心的差异化；
3. BI 正确性的真正地基"指标口径一致 / 语义层"反而缺失，只有 Aloudata 有 metric view，JDBC 源没有统一语义层。

另有最高风险项：**自研"轻量下推引擎"**，业界有 DuckDB / Apache DataFusion 这类可嵌入轮子，从零手搓长期维护成本就是文档自己承认的缺点。

---

## 1. 项目现状对照（grounding，确保评审不空谈）

| 文档声称/引用 | 代码现状核实 | 结论 |
|---|---|---|
| 现有 Insight Dashboard（schema_json + ECharts + AI 生成） | `InsightDashboardServiceImpl` 含 `GENERATE_SYSTEM_PROMPT` / `MODIFY_SYSTEM_PROMPT`；`InsightDashboardSchemaDTO` 支持 `perspectives/pages/components` + `aiAnalysisContent` | **真实存在且已上线**，"兼容旧 Schema"可行 |
| `LocalCodeExecutorService` / `PythonAnalysisTool` | `service/code/impl/LocalCodeExecutorService.java` 用 `ProcessBuilder` 起**独立进程**，写 `script.py/stdin.txt/requirements.txt`，**可联网 `pip install`** | 已是独立进程（非 JVM 内嵌），但**裸进程无沙箱**：同机同用户、全文件系统/网络访问、可装任意包 |
| Aloudata 指标/维度/视图集成 | `aloudata/` 包 + `AloudataService` + 同步调度 + 语义层 ES | **真实存在** |
| 数据源能力声明 / Dialect | 现有 `DatasourceEntity` 仅支持 JDBC 类型（见 `datasource-semantic-layer.md`） | 文档的"能力声明 + Dialect 抽象"是对现状的合理升级 |
| `QueryService` / `DatasetCatalogService` / `QueryPlanner` / `ScriptTaskService` | 代码库**未检索到**这些类 | 多为**全新提议**，非现有能力——评审以此为前提 |
| 产品原型 | `策略解读-prototype.html` 标题"策略解读·数据视角"，含视角切换、筛选器、**AI 智能解读（AI 生成 / 重新生成）**、指标卡、明细表 | AI 是原型 UX 的一等公民 |

**关键推论**：文档"必须隔离出 JVM"的字面表述其实弱于现状（现状已是进程隔离）；文档真正新增的安全价值是**沙箱化 + 网络策略 + 依赖白名单 + 资源硬限制**，而非"隔离出 JVM"本身。因此新 Runner 的验收重点应是"隔离技术选型"（下面 §6）。

---

## 2. 产品设计角度

### 2.1 亮点（值得保留）

- **四层抽象（Connection → Datasource → Dataset → Query）**：把连接信息、业务对象、组件查询解耦，避免把连接细节写进组件，方向完全正确。
- **数据源能力声明（capabilities + Dialect）**：前端按能力渲染配置项，而不是按 `type` 写死分支——这是 JDBC 多方言（MySQL/Doris/StarRocks/PG）的正确抽象。
- **展示/控制/操作分离 + 通用 Action Card**：避免为每个原型加专用按钮，可复用性高。
- **声明式联动（事件→参数→查询）**：把"组件内部 Hook"限定为实现细节、不暴露给用户配置，是对的。
- **分阶段建设**：阶段一打通闭环、阶段二补执行层、阶段三平台化——节奏合理。

### 2.2 矛盾一：「面向所有用户」vs「多源必须写 Python」（核心问题）

文档 §2.1 明确"用户不必理解 SQL/Hook/事件总线"，§6.5 却规定：

> 选择多个数据源时，**必须配置 Python 脚本预处理**；未完成预处理时，不允许保存或发布。

一个非技术用户只要说"我想把 Doris 的业务表和 MySQL 的用户表按 user_id 关联"，就被迫写 Python。这直接违反 §2.1 的"面向所有用户"原则。

**建议**：把"多源"拆成三级，Python 仅作 escape hatch：
1. **同库/同源 join**：走可视化 join 构造器（选左表、右表、关联键、类型），系统生成查询计划，无需代码；
2. **轻跨源（同机）**：用 DuckDB `ATTACH`（见 §5）一条 SQL join，门槛远低于 Python 脚本；
3. **复杂逻辑**：才落到 Python 脚本。

这样"面向所有用户"才名副其实。

### 2.3 矛盾二：AI 能力被自我阉割，与产品/原型自相矛盾

文档 §2.1、§7.3 写道：

> 平台不负责自动生成或直接执行 AI 输出；用户可以借助**外部大模型**生成草稿，确认后粘贴到编辑器。

但：
- 产品现状 `InsightDashboardServiceImpl` 已有 `GENERATE_SYSTEM_PROMPT` 用 LLM 生成 Schema；
- 原型 `策略解读-prototype.html` 顶部就是"**AI 智能解读**"卡片，带"重新生成"按钮；
- 产品定位是"AI 洞察仪表盘 / AI 对话生成"（见 `项目介绍与架构说明.md` 第十节）。

也就是说，文档把产品**最差异化的能力**降级成了"用户自己开个 ChatGPT 写 SQL 再粘回来"。

**建议**：AI 生成应作为**受治理的一等能力**，而不是外包。担心的"不可信 AI SQL"用文档自己设计的校验层兜底即可——同一套 `JSqlParser` 只读校验 + 参数绑定 + 下推报告 + 资源预算，对 AI 生成物和人写物一视同仁。这样既不违背安全原则，又保住了产品的灵魂。

### 2.4 矛盾三：缺"语义层 / 指标口径一致"这块 BI 地基

文档只把"指标视图"作为 **Aloudata 专属**的一等数据集；对 JDBC 源，每个图表各自写 SQL 算"活跃用户""营收"。结果：同一个"活跃用户"在不同图里口径不同，数字互相打架——这正是传统 BI 最被诟病的问题，也是语义层要解决的。

**建议**：把"轻量语义层"从阶段三的"评估 Cube"**提前到阶段二**。不必一上来引入 Cube 全家桶，先做最小可用：
- 在 Dataset 之上定义 **Metric（口径公式 + 维度 + 聚合）** 与 **Dimension**；
- 图表引用 Metric 而非裸字段，保证"营收"处处同义；
- 后端实现可借鉴 Cube（YAML 模型）或 dbt MetricFlow，但先用 MateClaw 自有 Schema 承载，避免强依赖。

### 2.5 预览门禁：WHERE 关键字检查是弱启发式

§5.2 / §5.6 规定 JDBC 预览"SQL 必须包含 `WHERE` 关键字"，否则阻止。这是"防君子不防小人"且误伤工程师：
- `SELECT ... FROM big_table LIMIT 100` 安全却被拦；
- `SELECT * FROM a JOIN b ON a.id=b.id WHERE 1=1` 全扫却放行。

**建议**：改为**基于成本预算的门禁**——用 `EXPLAIN` / 表统计估算扫描行数/字节，超预算（如 >1000 万行）才硬拦并提示"请加更精确的筛选/分区条件"。这既更安全（基于真实代价），也更顺手（不卡 LIMIT 安全查询）。

---

## 3. 用户体验角度

### 3.1 亮点

- 拖拽心智模型（拖组件→选数据→配字段→配筛选→连联动→预览→发布）清晰；
- **独立数据源预览**（每个源卡片可单独预览）降低调试认知负荷；
- **下推报告 / Pushdown Report**（已下推/未下推/残余）透明化执行，是高级但极有价值的 UX；
- 筛选"作用范围"可视化（按数据集/数据源勾选、置灰不可绑定项）做得细。

### 3.2 命名规则自相矛盾（低级但刺眼）

§5.2 写：名称"支持英文、下划线和数字"；示例却给 `"业务分析库"`（中文）。文档自己打自己脸。

**建议**：要么放开中文名称（产品面向中文业务用户，中文名更友好），要么示例改成 `doris_strategy_cluster` 并保持纯 ASCII。二选一，统一即可。

### 3.3 多源配置的门槛与认知负荷

即使保留 Python 路径，§6.5 要求的"作用范围 + 字段映射 + 预览筛选 + 预处理代码"对非技术用户是陡坡。即使"第一个数据源不再强制主数据集"是好改进，整体仍偏重。

**建议**：
- 单源 / 同源 join 默认走可视化，不暴露脚本；
- 仅当用户确实选了"跨异构源 + 复杂逻辑"时，才引导到 Python/DuckDB；
- 强化"**预览≠生产**"的视觉提示（预览是受限样本），发布前给一份"发布前校验报告"（扫描量、下推状态、权限范围）。

### 3.4 JS 处理定位不清

§5.5、§7.1 把 JS 定为"轻量字段转换和展示前处理"，并留到后期。应明确：**JS 跑在浏览器端**（类似计算列），不作为服务端执行负担。否则容易被误解为要再养一个 JS 执行沙箱。

---

## 4. 解决方案与技术选型角度

### 4.1 亮点（与业界主流一致，认可）

- Arrow/Parquet + `dataRef` 做数据交换、预览用受限 JSON、正式用列式——现代且合理；
- Polars（lazy + streaming）做 pushdown 后的内存变换——合适；
- 独立 Python Runner + 一次性隔离进程/容器——方向对；
- `JSqlParser` 做 AST 校验、`Apache Calcite` 留作后期升级——务实；
- `QueryEngine` 抽象（LocalPushdownEngine / 未来 TrinoQueryEngine）——正确，给未来留口子。

### 4.2 最高风险：自研"轻量下推引擎"

§9 主张自研 `LocalPushdownEngine`。但下推正确性（semi-join 改写、相关子查询、window 函数的分区下推、NULL/浮点语义）极难，文档自己也承认"长期维护成本较高"。**在已有 Polars/DuckDB/DataFusion 的今天，从零手搓是性价比最低的一步。**

**建议**：认真考虑用**可嵌入引擎**作为 `LocalPushdownEngine` 的实现，而非手搓：

- **Apache DataFusion（Rust，Arrow-native，已是 ASF 顶级项目）**：自带完整 planner + 优化器（predicate/projection pushdown、join reorder、spill to disk），可嵌入为库，后续 `Ballista` 还能分布式。把"多源查询计划"交给它，比自研省一个量级的工作量，正确性更有保证。
- **DuckDB（嵌入式中，可 `ATTACH` MySQL/Postgres/SQLite，直接读 Parquet/JSON/HTTP）**：对"同机多源 join"是比 Python 脚本更轻的方案（Doris 走 MySQL 协议 ATTACH、MySQL、Parquet 一条 SQL 全 join），且完全 embeddable，无独立集群。

> 文档把 DuckDB 压到"Runner 内部可选优化"，我建议**提升为跨源 QueryEngine 的一等候选**（同 box 场景），比强制 Python 更友好。

### 4.3 惰性数据集 API 与 Ibis 高度重合，应直接复用

§6.1 的 `dataset("doris.strategy").filter().select().join().collect()` 几乎就是 **Ibis** 的 API：

```python
import ibis
con = ibis.duckdb.connect()          # 或 ibis.mysql.connect(...)
t = con.table("strategy", database="doris")
t.filter(t.event_date.between(start, end)).join(...).to_sql()
```

Ibis 是"通用 Python DataFrame 前端"，把链式调用**编译成后端原生 SQL**，支持 MySQL/PostgreSQL/Doris/Trino/BigQuery 等近 20 个后端，且 `set_backend` 一行切换。自研这套 lazy API = 重造 Ibis。

**建议**：Python 多源处理用 **Ibis 作为统一前端**（负责下推到各后端），**Polars 负责下推完成后的内存变换**；或至少在 `QueryEngine` 层评估 Ibis。DuckDB 负责 OLAP-on-files + 跨源，Ibis 负责"一套 API 写多后端"。

### 4.4 行/列级权限应下沉到数据库，而非 Python 层

§10 列了 RLS/CLS 但没给机制。问题在于：Python 脚本预处理阶段**无法自动注入 `WHERE tenant_id=:current_tenant`**，自研 RLS rewriter 既脆弱又难全。

**建议**：
- **JDBC 源**：直接用 MateClaw 已有的"**用户绑定自己的只读查询账号**" + **数据库原生 RLS**（行级安全策略），让 DB 自己保证隔离，零自研；
- **API / Aloudata 源**：在 Adapter 内做行/列掩码策略；
- 阶段一**不要**自研通用 RLS rewriter。

### 4.5 权限应复用现有 RBAC，而非另起炉灶

`项目介绍与架构说明.md` 第 13 节已有成熟底座：工作区隔离 + 全局/工作区角色 + **通用资源授权表（view<use<edit）+ Guard 纵深防御**。但设计文档 §10 的权限清单是"真空"列出的，像是重新描述一套权限模型。

**建议**：明确 `datasource` / `dataset` 作为资源接入**现有 `ResourceGuard`**，复用 `view/use/edit` 与现有授权表；行/列掩码作为策略插件挂到同一层。不要新写权限模型。

### 4.6 跨源 join 的"Python 硬门槛"可软化

Doris/StarRocks/MySQL 同 box 时，DuckDB `ATTACH` 一条 SQL 即可 join，门槛远低于写 Python、也更不易出错。把"多源必须 Python"放宽为"可视化 join → DuckDB SQL → Python"三级（呼应 §2.2），更契合"面向所有用户"。

### 4.7 Trino 边界：方向对，但补充候选

不引入 Trino 我**同意**（运维重、阶段一不需要）。`QueryEngine` 抽象 + 未来 `TrinoQueryEngine` 也对。只需补充：DuckDB / DataFusion 同样是 `QueryEngine` 的合理实现，且比手搓引擎更省——在抽象里给它们留位置即可。

---

## 5. 业界成熟开源方案对照表

| 能力域 | 文档现状 | 推荐补充/替代（成熟开源） | 备注 |
|---|---|---|---|
| 数据交换 | Arrow/Parquet + `dataRef`（✓） | Arrow Flight（phase 1 **过度**，暂不需） | 文档判断正确 |
| 惰性多后端 DataFrame | 自研 lazy API | **Ibis**（编译成后端 SQL，~20 后端） | 避免重造轮子 |
| 可嵌入查询/下推引擎 | 自研 LocalPushdownEngine | **DuckDB**（跨源 ATTACH）、**Apache DataFusion**（Rust 可嵌入 planner） | 降自研风险 |
| 语义/指标层 | 仅 Aloudata metric view | **Cube**（YAML 模型）、**dbt MetricFlow**、**Lightdash**、**Malloy**、Snowflake/Databricks Semantic View | 阶段二引入轻量版 |
| BI 平台 | 自研 UI（选项 C ✓） | Superset / Metabase（已分析）；另可参考 **Grafana**（观测）、**Evidence / Rill**（code-based BI） | 选项 C 合理，不绑定第三方 UI |
| 安全沙箱 | "一次性隔离进程/容器"（**未指定技术**） | **nsjail**（进程级 ~50ms）、**gVisor / Firecracker**（微 VM，强隔离 ~100–200ms）、Docker、WASM | 必须明确选型 + 默认禁网禁 pip |
| SQL 解析/校验 | JSqlParser（✓） | Apache Calcite（后期统一编译器） | 文档判断正确 |
| Python 处理 | Polars + 独立 Runner（✓） | 可叠加 Ibis 前端 | 见 §4.3 |

**关于沙箱的关键提醒**：当前 `LocalCodeExecutorService` 是**裸 `ProcessBuilder` + 联网 `pip install`**，离沙箱差得远。新 Runner 迁移时**必须补上隔离技术**（nsjail/gVisor/Firecracker 分级），否则只是"换了个名字的裸进程"。建议分级：
- 同机、可信脚本 → **nsjail**（轻量、~50ms、seccomp+cgroup）；
- 多租户、不可信 → **gVisor / Firecracker**（独立内核边界）；
- 通用 → Docker（`--rm`、只读根、非 root、默认禁网）；
- 全局红线：无宿主凭据挂载、默认 deny 出网、禁止运行时 `pip install`。

---

## 6. 落地清单（按优先级）

| # | 建议 | 对应章节 | 优先级 |
|---|---|---|---|
| 1 | 统一数据源命名规则（修 §5.2 自相矛盾） | §5.2 | 高（低级但刺眼） |
| 2 | 多源"可视化 join → DuckDB SQL → Python"三级，Python 仅作 escape hatch | §2.1/§6.5 | 高（核心矛盾） |
| 3 | 预览门禁从 WHERE 关键字改为 EXPLAIN 成本预算 | §5.2/§5.6 | 中 |
| 4 | 阶段二引入轻量语义层（Metric/口径），不拖到阶段三 | §11/§13.3 | 高（BI 正确性） |
| 5 | AI 生成作为受治理一等能力，复用校验层，而非外包"外部大模型粘贴" | §2.1/§7.3 | 高（产品灵魂） |
| 6 | 复用现有工作区 RBAC + 资源授权表，不新写权限模型 | §10 | 中 |
| 7 | JDBC 行/列级权限用"每用户只读账号 + DB 原生 RLS" | §10 | 中 |
| 8 | 评估 DuckDB / DataFusion 作为 `LocalPushdownEngine` 实现（或一等 QueryEngine 候选） | §9 | 高（降风险） |
| 9 | Python 侧评估 Ibis 作为惰性多后端前端 | §6.1/§7.2 | 中 |
| 10 | 沙箱明确选型（nsjail/gVisor/Firecracker 分级）+ 默认禁网禁 pip | §7.2 | 高（安全） |
| 11 | 先跑通"单源 JDBC → 图表 → 参数绑定 → 预览 → 发布"端到端 walking skeleton，再铺执行层 | §11 | 中（降风险） |

---

## 7. 结语

文档的工程素养和抽象能力是**上乘**的，四层模型、能力声明、惰性执行、下推透明化、分阶段节奏都经得起推敲，且对现有代码的兼容性判断基本诚实。真正需要"不同意见"的有三处：**把 AI 能力外包出去（与产品灵魂矛盾）、缺少语义层（BI 正确性的地基）、以及用自研下推引擎去扛本可用 DuckDB/DataFusion/Ibis 解决的事**。把这三点修正后，这是一份可以放心照着落地的设计。

— 胖虎 🐯
