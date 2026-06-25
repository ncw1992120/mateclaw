---
name: python_analysis
version: "1.0.0"
description: "当用户需要进行复杂数据分析（统计分析、机器学习、相关性分析、异常检测等）时，使用 Python 分析工具执行 Python 代码。适用于数据查询工具难以完成的复杂计算场景。"
dependencies:
  tools:
    - python_analysis
---

# Python 数据分析技能

## 适用场景

当分析需求超出数据查询工具能力范围时，使用 Python 进行数据分析：

- **统计分析**：相关性分析、假设检验、回归分析、分布检验
- **数据清洗**：缺失值处理、异常值检测、数据类型转换
- **机器学习**：聚类分析、分类预测、时序预测
- **高级可视化**：matplotlib/seaborn/plotly 生成复杂图表
- **复杂计算**：数据透视、多表关联计算、时序分析

## 工作流程

### 第一步：理解业务术语（可选）

如果用户提问涉及业务术语、缩写或别名：
1. 通过 `search_business_term` 查询术语的标准名称、定义和同义词
2. 明确业务含义后，再进行数据查询和分析

### 第二步：评估分析需求

判断是否需要使用 Python 分析：
- 简单的聚合、过滤、排序 → 优先使用数据查询工具
- 复杂统计、机器学习、高级可视化 → 使用 Python 分析

### 第三步：获取数据

根据数据源类型选择合适的数据查询工具：

| 数据源 | 查询工具 | 说明 |
|--------|---------|------|
| 数据库 | `data_query` 的 `execute_sql` | 执行 SQL 查询获取结构化数据 |
| 指标平台 | `aloudata_metrics_query` | 查询指标数据（按时间、维度筛选） |
| 指标/维度元数据 | `aloudata_search_semantic` | 检索指标/维度的业务名称、描述、同义词 |
| 多数据源 | 组合使用上述工具 | 先查元数据理解数据结构，再查具体数据 |

查询结果将作为 Python 代码的输入数据。

### 第四步：编写并执行 Python 代码

1. 编写 Python 分析代码，遵循以下约定：
   - 使用 `sys.stdin.read()` 读取输入数据（JSON 格式）
   - 使用 `json.loads()` 解析输入数据
   - 使用 `pandas.DataFrame` 进行数据处理
   - 使用 `print()` 输出分析结果
2. 将查询结果作为 `input` 参数传入
3. 如需额外依赖，通过 `requirement` 参数声明

### 第五步：解读分析结果

根据 Python 执行输出，向用户解读分析结果。

## Python 代码模板

```python
import sys
import json
import pandas as pd

# 读取标准输入数据（查询结果的 JSON）
input_data = sys.stdin.read()
if input_data:
    data = json.loads(input_data)
    df = pd.DataFrame(data)

# 在此编写分析逻辑
result = df.describe()
print(result.to_string())
```

## 典型工作流示例

### 示例1：数据库数据 + Python 分析

用户: "帮我分析各产品的销售趋势和异常值"

1. `data_query` execute_sql → 获取销售明细数据
2. `python_analysis` execute_python → 时序分析 + 异常检测

### 示例2：指标平台数据 + Python 分析

用户: "分析营收指标的同比环比变化趋势"

1. `search_business_term` → 查询"营收"的术语定义（同义词：收入、营业收入）
2. `aloudata_search_semantic` → 检索"营收"对应的指标和维度
3. `aloudata_metrics_query` → 查询营收指标的时间序列数据
4. `python_analysis` execute_python → 计算同比环比 + 趋势分析

### 示例3：混合数据源 + Python 分析

用户: "对比指标平台的 KPI 与数据库中的实际数据，分析偏差"

1. `aloudata_metrics_query` → 获取指标平台的 KPI 数据
2. `data_query` execute_sql → 获取数据库中的实际数据
3. `python_analysis` execute_python → 两份数据对比分析

## 注意事项

- Python 执行有超时限制（默认 60 秒），避免编写耗时过长的代码
- 输出结果应简洁明了，避免输出大量原始数据
- 如果执行失败，根据错误信息修正代码后重新执行
- 优先使用 pandas/numpy 等标准数据分析库
- 获取数据时应先了解数据结构（通过 search_schema 或 aloudata_search_semantic），再编写精确的查询
