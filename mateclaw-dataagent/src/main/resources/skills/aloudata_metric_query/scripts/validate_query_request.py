"""
Aloudata 指标查询请求校验脚本

在提交 aloudata_metrics_query 之前，校验请求参数是否符合规范，
避免常见错误（使用展示名、缺少时间维度、偏移粒度不匹配等）。

使用方式: python scripts/validate_query_request.py <request_json_file>

返回: 校验结果 JSON，包含 passed（是否通过）和 errors（错误列表）
"""

import json
import sys
import re
import argparse


# 日期粒度等级（从小到大）
GRAIN_LEVEL = {
    "day": 1,
    "week": 2,
    "month": 3,
    "quarter": 4,
    "year": 5
}

# 偏移粒度到日期粒度等级的映射
OFFSET_GRAIN_MAP = {
    "dod": "day",
    "wow": "week",
    "mom": "month",
    "qoq": "quarter",
    "yoy": "year",
    "woeow": "week",
    "moeom": "month",
    "qoeoq": "quarter",
    "yoeoy": "year",
    "wosow": "week",
    "mosom": "month",
    "qosoq": "quarter",
    "yosoy": "year"
}


def extract_offset_grain(metric_name: str) -> str:
    """从同环比快速计算语法中提取偏移粒度"""
    match = re.search(r"__sameperiod__.*?__(\w+)$", metric_name)
    if not match:
        # 尝试匹配带日期标识的格式: __sameperiod__{offset}__{flag}__{method}
        match = re.search(r"__sameperiod__(?:-\d+_)?(\w+?)(?:__\w+)?__(?:value|growthvalue|growth|decrease|decreaserate)$", metric_name)
    if match:
        grain = match.group(1)
        return OFFSET_GRAIN_MAP.get(grain, grain)
    return None


def extract_proportion_rank_dims(metric_name: str, calc_type: str) -> list:
    """从占比/排名快速计算语法中提取范围维度"""
    pattern = rf"__{calc_type}__(.+)$"
    match = re.search(pattern, metric_name)
    if match:
        return [d.strip() for d in match.group(1).split(",")]
    return []


def get_time_grain_from_dimensions(dimensions: list) -> str:
    """从 dimensions 列表中提取时间粒度"""
    for dim in dimensions:
        if dim.startswith("metric_time__"):
            grain = dim.replace("metric_time__", "")
            return grain
        if dim == "metric_time":
            return "day"  # 默认日粒度
    return None


def validate_request(request: dict) -> dict:
    """校验 Aloudata 指标查询请求"""
    errors = []

    # 1. 检查 metrics 是否存在
    metrics = request.get("metrics", [])
    if not metrics:
        errors.append("metrics 参数为空，至少需要一个指标")
        return {"passed": len(errors) == 0, "errors": errors}

    # 2. 检查是否使用了中文展示名（简单启发式：包含中文字符）
    for metric in metrics:
        base_metric = metric.split("__")[0]
        if re.search(r"[\u4e00-\u9fff]", base_metric):
            errors.append(f"指标 '{base_metric}' 可能使用了中文展示名，请使用英文名(metricName)")

    # 3. 检查 dimensions 是否使用了中文展示名
    dimensions = request.get("dimensions", [])
    for dim in dimensions:
        base_dim = dim.split("__")[0] if "__" in dim else dim
        if base_dim != "metric_time" and re.search(r"[\u4e00-\u9fff]", base_dim):
            errors.append(f"维度 '{base_dim}' 可能使用了中文展示名，请使用英文名(dimName)")

    # 4. 检查同环比约束
    time_grain = get_time_grain_from_dimensions(dimensions)
    has_time_in_constraint = "metric_time" in str(request.get("timeConstraint", ""))

    for metric in metrics:
        if "__sameperiod__" in metric:
            # 检查时间维度是否存在
            if not time_grain and not has_time_in_constraint:
                errors.append(f"同环比指标 '{metric}' 缺少时间维度，metric_time 必须在 dimensions 或 timeConstraint 中")

            # 检查偏移粒度是否小于日期粒度
            offset_grain = extract_offset_grain(metric)
            if offset_grain and time_grain:
                offset_level = GRAIN_LEVEL.get(offset_grain, 0)
                dim_level = GRAIN_LEVEL.get(time_grain, 0)
                if offset_level < dim_level:
                    errors.append(
                        f"同环比指标 '{metric}' 的偏移粒度({offset_grain})小于日期粒度({time_grain})，"
                        f"偏移粒度不可小于日期粒度"
                    )

    # 5. 检查占比维度约束
    for metric in metrics:
        if "__proportion__" in metric:
            proportion_dims = extract_proportion_rank_dims(metric, "proportion")
            for pdim in proportion_dims:
                if pdim not in dimensions:
                    errors.append(
                        f"占比指标 '{metric}' 的范围维度 '{pdim}' 未在 dimensions 中声明"
                    )

    # 6. 检查排名维度约束
    for metric in metrics:
        if "__rank__" in metric:
            rank_dims = extract_proportion_rank_dims(metric, "rank")
            for rdim in rank_dims:
                if rdim not in dimensions:
                    errors.append(
                        f"排名指标 '{metric}' 的范围维度 '{rdim}' 未在 dimensions 中声明"
                    )

    # 7. 检查 timeConstraint 格式
    time_constraint = request.get("timeConstraint", "")
    if time_constraint:
        # 检查是否缺少外层括号
        if not (time_constraint.startswith("(") and time_constraint.endswith(")")):
            errors.append(
                f"timeConstraint 格式错误: '{time_constraint}'，"
                f"整个表达式必须用 () 包裹，如 (DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
            )
        # 检查是否使用了错误的日期范围格式（如 2024-01-01/2024-01-31）
        if re.match(r"^\d{4}-\d{2}-\d{2}/\d{4}-\d{2}-\d{2}$", time_constraint):
            errors.append(
                f"timeConstraint 格式错误: '{time_constraint}'，"
                f"请使用表达式语法，如 ([metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\")"
            )

    # 8. 检查 filters 中的维度引用格式
    filters = request.get("filters", [])
    for f in filters:
        if not f.startswith("["):
            errors.append(
                f"筛选条件 '{f}' 格式可能不正确，维度引用应使用方括号，如 [region] IN (\"华东\")"
            )

    return {"passed": len(errors) == 0, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description="校验 Aloudata 指标查询请求")
    parser.add_argument("input", help="请求 JSON 文件路径")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        request = json.load(f)

    result = validate_request(request)
    print(json.dumps(result, ensure_ascii=False, indent=2))

    if not result["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
