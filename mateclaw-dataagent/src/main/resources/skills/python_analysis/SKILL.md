---
name: python_analysis
version: "1.0.0"
description: "当用户需要进行复杂数据分析（统计分析、机器学习、相关性分析、异常检测等）时，使用 Python 分析工具执行 Python 代码。适用于 SQL 难以完成的复杂计算场景。"
dependencies:
  tools:
    - python_analysis
---

# Python 数据分析技能

## 适用场景

当分析需求超出 SQL 能力范围时，使用 Python 进行数据分析：

- **统计分析**：相关性分析、假设检验、回归分析、分布检验
- **数据清洗**：缺失值处理、异常值检测、数据类型转换
- **机器学习**：聚类分析、分类预测、时序预测
- **高级可视化**：matplotlib/seaborn/plotly 生成复杂图表
- **复杂计算**：数据透视、多表关联计算、时序分析

## 工作流程

### 第一步：评估分析需求

判断是否需要使用 Python 分析：
- 简单的聚合、过滤、排序 → 优先使用 SQL（query_datasource 的 execute_sql）
- 复杂统计、机器学习、高级可视化 → 使用 Python 分析

### 第二步：获取数据

1. 先通过 `query_datasource` 的 `execute_sql` 获取所需数据
2. SQL 查询结果将作为 Python 代码的输入数据

### 第三步：编写并执行 Python 代码

1. 编写 Python 分析代码，遵循以下约定：
   - 使用 `sys.stdin.read()` 读取输入数据（JSON 格式）
   - 使用 `json.loads()` 解析输入数据
   - 使用 `pandas.DataFrame` 进行数据处理
   - 使用 `print()` 输出分析结果
2. 将 SQL 查询结果作为 `input` 参数传入
3. 如需额外依赖，通过 `requirement` 参数声明

### 第四步：解读分析结果

根据 Python 执行输出，向用户解读分析结果。

## Python 代码模板

```python
import sys
import json
import pandas as pd

# 读取标准输入数据（SQL 查询结果的 JSON）
input_data = sys.stdin.read()
if input_data:
    data = json.loads(input_data)
    df = pd.DataFrame(data)

# 在此编写分析逻辑
result = df.describe()
print(result.to_string())
```

## 注意事项

- Python 执行有超时限制（默认 60 秒），避免编写耗时过长的代码
- 输出结果应简洁明了，避免输出大量原始数据
- 如果执行失败，根据错误信息修正代码后重新执行
- 优先使用 pandas/numpy 等标准数据分析库
