---
name: aloudata_metric_anomaly_detection
version: "1.0.0"
description: "指标异常检测入口。当用户问'有没有异常/突增/突降/波动异常/离群/是否正常/异动/数据有没有问题'时，必须先 load_skill('aloudata_metric_anomaly_detection') 加载检测工作流。流程：必须先加载 aloudata_metric_query 完成检索并取回指标时间序列 → 用业界标准方法（稳健 MAD 修正 Z 分数、3σ Z 分数、IQR 箱线图、移动平均控制图、STL 分解残差、EWMA 控制图，多方法投票集成 + 变点检测位移确认）检测 → 分级（轻微/中等/严重）与分类（尖峰/凹陷/水平位移/零值）→ 对异常时点按维度下钻定位根因 → 输出异常报告。硬约束：检测前置条件——必须先 load_skill('aloudata_metric_query') 加载查询工作流，取数与检索严格按其流程执行，禁止绕过直接调用 aloudata_metrics_query；必须先取指标时间序列（metric_time 维度升序，样本量 ≥8 才可判定）；检测必须交给 scripts/anomaly_detect.py 或 python_analysis 执行，禁止肉眼判读；相对时间（近N天/上月/本周等）必须先调 DateTimeTool.getCurrentDate 获取当前日期，禁止主观推算；样本量不足或无法判定时如实说明，禁止编造异常。"
dependencies:
  tools:
    - search_business_term
    - aloudata_search_semantic
    - aloudata_metric_available_dimensions
    - aloudata_metrics_query
    - python_analysis
    - runSkillScript
    - getCurrentDate
references:
  - references/methods.md
  - references/examples/spike-detection.json
  - references/examples/seasonal-residual-detection.json
  - ../aloudata_metric_query/references/api-doc.md
scripts:
  - scripts/anomaly_detect.py
templates:
  - templates/anomaly-report.md
---

# 指标异常检测技能

**前置依赖（硬性）**：本技能的检索与取数完全建立在 `aloudata_metric_query` 之上——检测前必须先 load_skill('aloudata_metric_query') 加载查询工作流，检索与取数严格按其流程执行（术语标准化 → 语义检索 → 构造查询 → 失败止损），禁止绕过该流程直接调用 aloudata_metrics_query。本技能只负责"检测与解读"。

## 适用场景

用户想知道**指标数据是否有异常**——"这周销售额有没有突增突降""近30天订单量有什么异动""这个指标最近正常吗"。异常检测回答"哪一天/哪个维度值不正常、偏离多少、严重程度如何"。

**三类分析的分工**：

| 用户意图 | 走哪个技能 | 返回什么 |
|---------|-----------|---------|
| 指标是多少（数值/趋势/排名/占比） | `aloudata_metric_query` | 指标结果值 |
| 指标为什么变（原因/贡献率） | `aloudata_metric_attribution_analysis` | 各维度贡献率 |
| 指标有没有异常（突增/突降/离群/异动） | **本技能** | 异常时点清单 + 严重度 + 根因 |

**检测与归因的边界**：检测先定位"哪里异常"（无对比基准，靠统计基线），归因回答"为什么变"（需要对比区间）。检测出异常时点后，可按需转归因技能做因子拆解。

## 工作流程

> **流程总览**：解析检测意图 → 复用查询技能检索指标英文名 → 确定检测口径 → 取时间序列 → 执行检测（脚本/工具，禁止肉眼判读）→ 分级分类解读 → 根因下钻 → 输出报告

### 第一步：解析检测意图

判断用户问题是否为异常检测（而非纯查询）。检测信号：

- "有没有异常""突增/突降""飙升/暴跌""波动很大""离群""不正常""异动""数据质量/有没有问题"
- 隐含批量扫描："哪些维度异常""帮我巡检一下这几个指标"

提取要素：

- **指标**：检测哪个指标（可能是多个）
- **时间范围/回看窗口**：近N天、本月、上月（必须能确定起止）
- **时间粒度**：DAY/WEEK/MONTH（默认与用户表述一致，未说明时用 DAY）
- **维度**：是否要按业务维度分别检测（"各渠道有没有异常"）
- **方向偏好**：只关心上升、下降，还是双向（默认双向）
- **方法/阈值**：用户是否指定（如"用3σ""只看超过50%的"），未指定用默认（见第三步）

**时间锚定（先取真实日期，禁止主观推算）**：回看窗口涉及相对表述（"近7天""近30天""上周""上月"等）时，必须**先调用内置工具 getCurrentDate（DateTimeTool.getCurrentDate）获取服务器当前日期与星期**，再以其返回值为唯一锚点换算 timeConstraint 区间；严禁凭主观印象推算今天日期与星期。

**追问复用**：本会话已通过 `aloudata_metric_query` 成功取过同一指标同一窗口的数据时，可直接复用该时间序列，不重复取数；仅窗口/粒度变化时增量取数。

### 第二步：检索指标英文名（复用指标查询技能）

复用 `aloudata_metric_query` 的第二、三步：业务术语先走 `search_business_term`，再走 `aloudata_search_semantic` 获取 metricName（英文名）。检测/取数只能用英文名。

- 本会话已加载 `aloudata_metric_query` 并检索过同一指标 → 直接复用 metricName，不重复检索
- keyword 宁整勿截（保留限定词与括号内容），与 `aloudata_metric_query` 一致

### 第三步：确定检测口径

> 未指定时按本表默认值执行，不要为这些默认项向用户追问（用户明确指定时才覆盖）。

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 时间粒度 granularity | 与用户表述一致，未说明用 DAY | 决定回看窗口与季节性周期 |
| 回看窗口 lookback | 日粒度近 30 天；周粒度近 12 周；月粒度近 12 月 | **样本量下限 8 个点**，不足则不可检测 |
| 检测方法 method | `auto`（多方法投票集成） | 可选 mad/zscore/iqr/moving/stl/ewma |
| 判定阈值 threshold | MAD 3.5 / 3σ 3.0 / IQR 1.5 与 3.0 | 稳健方法优先，见 [references/methods.md](references/methods.md) |
| 实际意义下限 minDeviationRatio | 0.03（相对偏离 3%） | 统计显著但偏离不足该比例的点不报告，仅计入 warnings |
| 方向 direction | `both` | both/up/down |
| 季节性周期 period | 自动推断（日粒度通常 7） | 显式传 1 表示无季节性 |

**样本量硬约束**：

- 有效点 < 8 → **不执行检测**，告知"数据点不足，无法做统计判定"，可建议扩大窗口
- 8 ≤ 有效点 < 14 → 只允许稳健方法（MAD + IQR 投票），禁用 STL/EWMA
- ≥ 14 → 可用移动平均控制图；≥ 3×周期（日粒度 ≥ 21 点）→ 可用 STL 分解

**窗口选择原则**：回看窗口至少覆盖 2 个业务周期（如日粒度含至少 2 个完整周），否则星期效应会被误判为异常。

### 第四步：取时间序列（必执行，复用 aloudata_metric_query）

构造 `aloudata_metrics_query` 请求，**必须带时间维度并升序排序**：

```json
{
    "metrics": ["sales_amount"],
    "dimensions": ["metric_time__day"],
    "timeConstraint": "([metric_time__day]>=DateAdd(Today(),-30,\"DAY\"))",
    "orders": [{"metric_time__day": "asc"}],
    "limit": 200
}
```

- 需要按维度分别检测时，`dimensions` 追加业务维度（如 `["channel", "metric_time__day"]`）
- 需要根因下钻时，另取一份按业务维度拆解的同窗口数据（见第七步）
- `orders` 必须是对象数组且按时间升序——**时序错乱会让移动平均/STL 全部失效**
- 取数结果缺行（某天无记录）**不需要补数再检测**：脚本把时间映射到均匀网格坐标、按真实时间对齐季节相位，缺口处不参与差分，缺口数量在 `warnings` 中提示
- 详细参数约束（timeConstraint 写法、同环比约束等）见 [../aloudata_metric_query/references/api-doc.md](../aloudata_metric_query/references/api-doc.md)；取数失败处理与止损规则同 `aloudata_metric_query`

### 第五步：执行检测（禁止肉眼判读）

把查询结果交给检测引擎执行，**禁止直接用自然语言逐点目测判断**。

**首选：脚本 `scripts/anomaly_detect.py`**（纯标准库，无第三方依赖）：

调用内置工具 `runSkillScript`，`args` 直接传一个 JSON 对象（工具会作为单个参数转发，不要包成数组）：

```
runSkillScript(
  skillName="aloudata_metric_anomaly_detection",
  scriptPath="scripts/anomaly_detect.py",
  args={
    "metric": "sales_amount",
    "granularity": "DAY",
    "method": "auto",
    "direction": "both",
    "queryResult": { ...aloudata_metrics_query 的原始响应... }
  }
)
```

也支持不传 queryResult、直接传已有时间序列：

```json
{
    "metric": "sales_amount",
    "granularity": "DAY",
    "method": "auto",
    "params": {"window": 7, "threshold": 3.5, "period": 7},
    "series": [
        {"time": "2025-06-01", "value": 120},
        {"time": "2025-06-02", "value": 132}
    ]
}
```

**备选：`python_analysis`**——需要脚本未覆盖的能力时（Isolation Forest 离群检测、STL/状态空间分解、CUSUM 变点检测、多序列批量扫描 + FDR 校正），将序列数据作为 `input` 传入执行；选型按运行时已装库（numpy/pandas/scipy/scikit-learn/statsmodels），镜像**未预装 Prophet 与 ruptures(PELT)**，确需时经 `PIP_COMMAND` 现场安装。算法与适用条件见 [references/methods.md](references/methods.md)。

**端到端示例**（完整的工具调用链 + 真实输入输出，拿不准调用格式时先读一份再动手）：

- [references/examples/spike-detection.json](references/examples/spike-detection.json) — 单指标尖峰/凹陷检测（无季节结构）
- [references/examples/seasonal-residual-detection.json](references/examples/seasonal-residual-detection.json) — 周末高峰不被误判（季节残差检测）

**脚本执行失败时的兜底**：`runSkillScript` 返回 `exitCode` 非 0（或 stdout 无法解析）时，把**同一份 payload**（queryResult 或 series）交给 `python_analysis`，按 [references/methods.md](references/methods.md) 的方法口径（优先 `auto` 集成：稳健 MAD + IQR + 局部线性趋势，含季节分解与样本量门限）用 numpy/pandas 实现等价检测；再次失败才如实报告"检测工具不可用"，**禁止因此退回肉眼判读**。

**脚本输出字段解读**：

| 字段 | 含义 |
|------|------|
| `summary.isStable` | 整体是否平稳（无异常） |
| `summary.anomalyCount` / `episodeCount` | 异常点数量 / 异常片段数量（连续异常合并为一段） |
| `anomalies[].time` / `value` | 异常时点与原值 |
| `anomalies[].expected` | 统计基线（期望值） |
| `anomalies[].score` | 偏离得分（稳健 Z 分数，绝对值越大越异常） |
| `anomalies[].deviationRatio` | 相对偏离幅度（0.5 = 偏离基线 50%） |
| `anomalies[].severity` | 严重度 MINOR/MAJOR/CRITICAL |
| `anomalies[].type` | 类型 SPIKE/DIP/LEVEL_SHIFT/ZERO_VALUE |
| `anomalies[].methods` | 命中该点的检测方法（投票数越多越可信） |
| `warnings` | 数据质量提示（缺失、常量序列、样本不足、时间缺口、实测采样间隔与声明粒度不符等） |

### 第六步：分级与分类解读

**严重度规则**（脚本已算，解读时按此口径陈述）：

- `CRITICAL`：偏离幅度 ≥100% 或稳健 Z ≥ 6（或超出 3×IQR 极值围栏）
- `MAJOR`：偏离幅度 ≥50% 或稳健 Z ≥ 4.5
- `MINOR`：其余命中点

**类型规则**：

- `SPIKE` 尖峰（单点或少量点向上偏离基线）——多为促销、活动、异常流量
- `DIP` 凹陷（向下偏离基线）——多为渠道中断、缺货、数据延迟
- `LEVEL_SHIFT` 水平位移（变点检测确认水平突变，或连续 ≥3 点同向异常）——多为口径/策略/上游变更，需重点确认
- `ZERO_VALUE` 零值（异常时点值为 0）——优先按数据链路问题排查

**解读原则**：

1. 先给整体结论（平稳 / 存在异常及其严重程度），再列异常明细
2. 按严重度 + 偏离幅度排序，**只列 Top N（建议 5 条）**，不要罗列全部
3. 必须区分"单点脉冲（SPIKE/DIP）"与"持续位移（LEVEL_SHIFT）"——后者业务影响更大
4. 命中方法越多、偏离幅度越大，结论越可信；仅 1 个方法命中的点应标注"待确认"
5. 数据点不足、常量序列（无波动）、缺失过多时**如实告知无法判定**，禁止编造异常

### 第七步：根因定位（按需）

检测只回答"哪里异常"，用户追问"为什么"时按以下顺序定位：

1. **维度下钻**：对主异常时点，取同窗口按业务维度拆解的数据（`aloudata_metrics_query`，dimensions 加业务维度），比较各维度值在异常时点的偏离幅度，定位贡献最大的维度值
2. **多维度值同时异常** → 多为系统性原因（口径变更、上游数据延迟、整体大盘波动），而非局部业务问题
3. **需要贡献率量化**（如"每个渠道对这次下跌贡献多少"）→ 转 `aloudata_metric_attribution_analysis`（归因需要对比区间，注意该技能的前置校验）

### 第八步：输出报告

按 [templates/anomaly-report.md](templates/anomaly-report.md) 输出，报告必须包含：检测口径、整体结论、异常明细（含严重度）、根因（若已下钻）、建议动作、数据质量说明。

## 检测方法速查

| 方法 | 适用场景 | 关键参数 | 标准出处 |
|------|---------|---------|---------|
| `mad` 稳健 Z 分数 | 通用首选，抗离群点污染 | threshold 3.5 | Iglewicz & Hoaglin 修正 Z 分数 |
| `zscore` 3σ | 近似正态、样本干净的序列 | threshold 3.0 | 经典统计过程控制 |
| `iqr` 箱线图围栏 | 分布偏斜、小样本 | k=1.5（离群）/3.0（极值） | Tukey 围栏 |
| `moving` 局部线性趋势残差 | 存在趋势、无强季节性 | window 7、threshold 3.5 | Theil–Sen 稳健回归 + Hampel 滤波思路 |
| `stl` 分解残差 | 有明显周期性（星期/月度效应） | period 自动推断 | STL / S-H-ESD 分解后残差检测 |
| `ewma` 指数加权控制图 | 需捕捉持续水平漂移 | alpha 0.3、L 3.0 | SPC EWMA 控制图 |
| `shift` 位移确认（内部通道） | 多方法集成时自动启用，不单独选用 | 双窗中位数比较、门限 3.5 | 变点检测（Change Point Detection） |
| `auto` 投票集成（默认） | 不确定用哪种时 | 多数投票，≥2 票命中 | 集成检测（Ensemble） |

**集成与分解的关系**：无论 `auto` 还是显式指定方法，检测引擎都会先做**结构识别**——自相关法推断周期，用"Theil–Sen 局部线性趋势 + 相位中位数季节"构成稳健基线；一旦分解通过三重门限（季节强度、季节效应量、拟合不过度完美），所有检测器都在"实际值 − 基线"的**残差**上判定，周期波动（如周末高峰）因此不会被误判。`stl` 即"显式要求走分解 + 残差检测"，未检出季节结构时退化为稳健 Z 分数并给出提示。投票之外另有**位移确认通道**（变点检测：前后双窗中位数比较）：显著的水平位移直接判为 `LEVEL_SHIFT` 并绕过投票阈值，避免"位移分摊到每点后单点偏离不显著"导致的漏检。

**`auto` 为什么不纳入 EWMA**：EWMA 会把单个尖峰平滑后持续外溢，导致尖峰之后多个正常点被连带判为"水平位移"；需要捕捉持续漂移时显式指定 `ewma`。

方法公式、参数默认值、选择决策树、三重门限、严重度标准详见 [references/methods.md](references/methods.md)。

## 结果判读的三条口径

1. **统计显著 + 实际意义**：命中检测器只说明"统计显著"；报告还要过**实际意义下限**（相对偏离 ≥ 3%，可用 `minDeviationRatio` 调整）。低于下限的点不列为异常，只在 `warnings` 中提示数量，避免无噪声序列的正常小波动淹没报告
2. **可信度看命中方法数**：`anomalies[].methods` 命中越多越可信；仅 1 个方法命中时标注"待确认"。`mad`/`moving` 属高相关证据（同一残差结构），仅靠这类方法凑满 2 票的点要求得分绝对值 ≥ 4.5 才报告；统计显著但证据不足的点不列为异常，计入 `warnings`
3. **得分是模型相对的**：`score` 以残差稳健尺度为分母，序列越规整得分越大；判读严重度时以 `deviationRatio` 为主、`score` 为辅

## 重要提示

1. **样本量不足不下结论（硬性）**：有效点 < 8 禁止判定异常；8-13 点只用稳健方法；缺失比例 > 30% 必须先提示数据质量风险
2. **禁止肉眼判读**：异常判定必须由 `scripts/anomaly_detect.py` 或 `python_analysis` 计算得出，禁止凭"看起来波动大"下结论
3. **先取数再检测**：检测的输入必须是真实查询结果（时间序列），禁止凭空构造数据或复用其他指标的序列
4. **时序必须升序**：`orders` 按时间维度升序；乱序输入会让趋势/季节性方法失效（脚本会自动按时间排序，但取数时就应正确排序）
5. **相对时间先取真实日期（硬性）**：回看窗口涉及"近N天/上周/上月"等表述时，必须先调用 getCurrentDate 获取服务器当前日期与星期，以其为唯一锚点换算区间（同 `aloudata_metric_query` 第一步）
6. **区分异常与业务事件**：促销、大促、节假日造成的尖峰是"预期内事件"；报告需说明"若为已知活动则为预期内波动"，不武断定性为数据问题
7. **常量序列的处理**：整段序列无波动（如长期为 0）时无法用统计方法判定，应作为数据质量问题反馈，而不是报告"无异常"
8. **与查询/归因协作**：取数与检索一律复用 `aloudata_metric_query` 的流程与约束（含失败止损规则），本技能只负责"检测与解读"，不重复定义查询语法
9. **指标英文名硬约束**：metricName 必须先经 `aloudata_search_semantic` 获取，禁止用中文展示名或猜测名

## 验证清单

- [ ] 已判定为异常检测意图（而非查询/归因），并提取指标、窗口、粒度、维度、方向
- [ ] 相对时间窗口已用 getCurrentDate 的返回值换算，未主观推算日期
- [ ] metricName 通过 `aloudata_search_semantic` 检索（或复用本会话已验证结果）
- [ ] 时间序列通过 `aloudata_metrics_query` 取回，含 metric_time 维度且时间升序
- [ ] 有效样本量 ≥ 8，缺失比例已评估；不足时未强行下结论
- [ ] 检测由脚本或 python_analysis 执行，输出了 score/deviationRatio/severity/type
- [ ] 报告区分了单点脉冲与持续位移，按严重度排序并控制在 Top N
- [ ] 已说明数据质量情况（缺失、常量序列、样本量），未编造异常