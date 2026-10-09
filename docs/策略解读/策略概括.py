# ===== 系统生成区域：读取已完成查询的数据集 =====
# 预览筛选应用后可能没有记录，仍需返回合法的空记录集。
if "table1" not in locals():
    table1 = datasets.input(input_name="table1").to_polars()

# ===== 用户处理区域 =====
# KPI 组件不需要二次变换：保留查询结果；空输入返回 []，避免 Runner 将 None
# 作为输出而触发 OUTPUT_CONTRACT_ERROR。
if table1 is None:
    result = []
elif hasattr(table1, "is_empty") and table1.is_empty():
    result = []
elif hasattr(table1, "empty") and table1.empty:
    result = []
elif hasattr(table1, "to_dicts"):
    result = table1.to_dicts()
elif hasattr(table1, "to_dict"):
    result = table1.to_dict(orient="records")
else:
    result = list(table1)
