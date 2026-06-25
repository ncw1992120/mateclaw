"""
Aloudata 指标查询结果格式化脚本

将 aloudata_metrics_query 返回的原始 JSON 结果转换为更易读的表格格式。
使用方式: python scripts/format_query_result.py <input_json_file> [--output <output_file>]

输入: aloudata_metrics_query 的原始响应 JSON
输出: 格式化后的 Markdown 表格
"""

import json
import sys
import argparse


def format_query_result(response: dict) -> str:
    """将 Aloudata 指标查询响应格式化为 Markdown 表格"""
    if not response.get("success", False):
        return f"查询失败: {response.get('message', '未知错误')}"

    data = response.get("data", {})
    table = data.get("table", {})
    columns = table.get("columns", {})
    metas = data.get("metas", [])

    if not columns:
        return "查询结果为空"

    # 获取列名和顺序
    column_names = [meta["name"] for meta in metas] if metas else list(columns.keys())

    # 获取行数
    first_col = columns.get(column_names[0], [])
    row_count = len(first_col)

    if row_count == 0:
        return "查询结果为空"

    # 构建 Markdown 表格
    lines = []

    # 表头
    header = "| " + " | ".join(column_names) + " |"
    separator = "| " + " | ".join(["---"] * len(column_names)) + " |"
    lines.append(header)
    lines.append(separator)

    # 数据行
    for i in range(row_count):
        row_values = []
        for col_name in column_names:
            col_data = columns.get(col_name, [])
            if i < len(col_data):
                value = col_data[i].get("value", "")
                row_values.append(str(value) if value is not None else "N/A")
            else:
                row_values.append("N/A")
        lines.append("| " + " | ".join(row_values) + " |")

    # 添加元信息
    lines.append("")
    lines.append(f"共 {row_count} 条记录")
    if data.get("queryId"):
        lines.append(f"查询ID: {data['queryId']}")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="格式化 Aloudata 指标查询结果")
    parser.add_argument("input", help="输入 JSON 文件路径")
    parser.add_argument("--output", "-o", help="输出文件路径（默认输出到控制台）")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        response = json.load(f)

    result = format_query_result(response)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(result)
    else:
        print(result)


if __name__ == "__main__":
    main()
