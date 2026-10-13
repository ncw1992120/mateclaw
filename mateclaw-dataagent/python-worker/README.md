# Python 数据诊断工具

DataAgent 内置 Worker 已提供 `diagnostics`，脚本可直接使用，也可以写 `from mateclaw import diagnostics`。无需新增服务或安装额外依赖。工具接收 pandas DataFrame；Polars 输入可先使用 `.to_pandas()` 转换。

```python
# table_zb 是当前查询条件过滤后的输入，不重新请求数据源。
zb = table_zb.to_pandas()

# 把金额转换成数字；遇到空值或非法字符串时报告数据集、字段和问题行。
# row_keys 用于显示业务 ID，便于回到数据源定位记录；最多配置 5 个字段。
zb["amount"] = diagnostics.to_numeric(
    zb,
    dataset="table_zb",
    field="amount",
    row_keys=["platform_id", "channel"],
    null_policy="error",
)

# 转整数时拒绝小数和超出有符号 64 位范围的数，避免悄悄截断。
zb["users"] = diagnostics.to_integer(
    zb, dataset="table_zb", field="users",
    null_policy="fill", default=0,  # 只有空值替换为 0；非法字符串仍报错。
)

# 日期格式明确写出，避免月/日顺序歧义。
zb["date"] = diagnostics.to_datetime(
    zb, dataset="table_zb", field="date", format="%Y-%m-%d",
)

# 必填检查也会把空字符串、纯空格、None、NaN 和 NaT 作为空值。
diagnostics.required(zb, dataset="table_zb", field="platform_id")
result = zb
```

`null_policy` 支持 `error`（默认报错）、`keep`（保留空值）、`fill`（必须提供合法的非空 `default`）。工具返回转换后的 Series，不修改原 DataFrame；成功后由脚本显式赋值。

`to_numeric` 输出 pandas `Float64`，适用于浮点数计算；需要精确整数的业务 ID、计数等使用 `to_integer`（输出 `Int64`），避免大整数浮点精度损失。

失败时抛出 `DataValidationError`，统计全部错误但最多展示前 10 条；行号按**当前输入顺序从 1 开始**，与 DataFrame index 无关。单个问题值预览最多 120 个字符，不返回整行数据。业务标识和值会显示给有权查看本次执行的用户，脚本应避免将密码、令牌等字段配置为 `row_keys`。

Worker 使用现有 `error` 字符串返回 JSON：`code=DATA_VALIDATION_ERROR`、`message`、`errorDetails` 和 `traceback`。`errorDetails` 包含 `dataset`、`field`、`scriptLine`、`errorCount`、`issues`。后端原样保存并透传，服务日志只记录数据集、字段、行序号、错误数和原因，不记录业务标识、问题值或 traceback 源码。不需要数据库迁移。

脚本行号指调用诊断工具的 `user_script.py` 位置。普通 Python/pandas/Polars 运算仍保留原有 traceback，不自动猜测问题字段和值。
