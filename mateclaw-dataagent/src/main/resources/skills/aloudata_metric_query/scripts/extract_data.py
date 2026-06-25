"""
Aloudata 指标查询结果数据提取脚本

从 aloudata_metrics_query 返回的原始 JSON 中提取数据为结构化格式，
便于后续传入 python_analysis 工具进行深度分析。

使用方式: python scripts/extract_data.py <input_json_file> [--output <output_file>]

输入: aloudata_metrics_query 的原始响应 JSON
输出: 结构化 JSON，包含 columns（列名列表）和 rows（行数据列表）
"""

import json
import sys
import argparse


def extract_data(response: dict) -> dict:
    """从 Aloudata 指标查询响应中提取结构化数据"""
    if not response.get("success", False):
        return {
            "success": False,
            "error": response.get("message", "查询失败"),
            "data": None
        }

    data = response.get("data", {})
    table = data.get("table", {})
    columns = table.get("columns", {})
    metas = data.get("metas", [])

    if not columns:
        return {
            "success": True,
            "data": {
                "columns": [],
                "rows": [],
                "rowCount": 0
            }
        }

    # 获取列名和类型
    column_names = []
    column_types = []
    for meta in metas:
        column_names.append(meta.get("name", ""))
        column_types.append(meta.get("dataTypeName", "STRING"))

    # 提取行数据
    first_col = columns.get(column_names[0], [])
    row_count = len(first_col)

    rows = []
    for i in range(row_count):
        row = {}
        for col_name in column_names:
            col_data = columns.get(col_name, [])
            if i < len(col_data):
                row[col_name] = col_data[i].get("value")
            else:
                row[col_name] = None
        rows.append(row)

    return {
        "success": True,
        "data": {
            "columns": column_names,
            "columnTypes": column_types,
            "rows": rows,
            "rowCount": row_count
        }
    }


def main():
    parser = argparse.ArgumentParser(description="提取 Aloudata 指标查询数据为结构化格式")
    parser.add_argument("input", help="输入 JSON 文件路径")
    parser.add_argument("--output", "-o", help="输出文件路径（默认输出到控制台）")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        response = json.load(f)

    result = extract_data(response)

    output = json.dumps(result, ensure_ascii=False, indent=2)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(output)
    else:
        print(output)


if __name__ == "__main__":
    main()
