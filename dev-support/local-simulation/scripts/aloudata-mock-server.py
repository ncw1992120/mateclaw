#!/usr/bin/env python3
"""Aloudata 本地 mock 服务：路径、参数、请求方式与真实环境完全一致，只有 ip:port 不同。

用法：
    python3 aloudata-mock-server.py [--port 18081] [--fixtures <夹具目录>]

把数据源的语义层/产品层地址指向本机即可（connection_params）：
    {"anymetricsHost": "http://127.0.0.1", "anymetricsPort": 18081,
     "semanticHost": "http://127.0.0.1", "semanticPort": 18081, "authType": "UID"}
然后**不再启用 local-mock profile**，后端会用真实的 AloudataApiClient 打到本服务。

行为对齐真实服务（故意不"兜底"，避免本地假绿）：
  * 请求方式不匹配 → 405；
  * 必填参数缺失 → 400 + success=false；
  * 未知视图 → SM_02_0038（无权限）；
  * metrics/query 的 filters 必须是表达式字符串数组，结构化对象 → SM99002。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_FIXTURES = os.path.abspath(
    os.path.join(HERE, "..", "..", "..", "mateclaw-dataagent", "src", "main", "resources", "mock", "aloudata")
)

ANYMETRICS = "/anymetrics/api/v1"
SEMANTIC = "/semantic/api/v1.1"

OWNER_PLACEHOLDER = "__OWNER__"
DEFAULT_OWNER = "mock-uid-001"

# 视图名 → 结果集夹具；顺序即 metrics/query 反查视图时的优先级
VIEW_ORDER = ["cljd_zcl_zb_view", "cljd_zcl_wd_view"]

CONDITION = re.compile(r"\['?([^'\]]+)'?\]\s*(<>|>=|<=|=|>|<|IN|NotIn)\s*(.+)", re.IGNORECASE)


def load_fixture(name: str) -> dict:
    path = os.path.join(FIXTURES_DIR, name)
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def envelope(data, code="200", success=True, error=None, trace_id="mock-trace"):
    return {
        "data": data,
        "success": success,
        "code": code,
        "errorMsg": error,
        "detailErrorMsg": error,
        "traceId": trace_id,
    }


def replace_owner(node, owner):
    if isinstance(node, dict):
        for key, value in list(node.items()):
            if value == OWNER_PLACEHOLDER:
                node[key] = owner
            else:
                replace_owner(value, owner)
    elif isinstance(node, list):
        for item in node:
            replace_owner(item, owner)
    return node


def as_int(value, fallback):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return fallback


def paginate(items, page_number, page_size, one_based=True):
    size = page_size if page_size and page_size > 0 else len(items)
    number = max(1, page_number or 1)
    start = min(len(items), (number - 1) * size if one_based else number * size)
    end = min(len(items), start + size)
    return items[start:end]


def columns_to_rows(columns: dict) -> list:
    size = max((len(v) for v in columns.values() if isinstance(v, list)), default=0)
    rows = [{} for _ in range(size)]
    for name, cells in columns.items():
        if not isinstance(cells, list):
            continue
        for index, cell in enumerate(cells):
            rows[index][name] = cell.get("value") if isinstance(cell, dict) and "value" in cell else cell
    return rows


def rows_to_columns(rows: list, names) -> dict:
    return {
        name: [{"value": row.get(name), "flag": 0, "count": 1} for row in rows]
        for name in names
    }


def unquote_values(raw: str) -> list:
    text = raw.strip()
    if text.startswith("(") and text.endswith(")"):
        text = text[1:-1]
    values = []
    for part in text.split(","):
        value = part.strip()
        if len(value) >= 2 and value.startswith('"') and value.endswith('"'):
            value = value[1:-1]
        if value:
            values.append(value.replace('\\"', '"'))
    return values


def parse_conditions(expression: str) -> list:
    """解析 `[字段] 运算符 值`，支持 AND 组合与括号。"""
    text = expression.strip()
    while text.startswith("(") and text.endswith(")"):
        text = text[1:-1].strip()
    conditions = []
    for clause in re.split(r"(?i)\s+AND\s+", text):
        clause = clause.strip()
        while clause.startswith("(") and clause.endswith(")"):
            clause = clause[1:-1].strip()
        if not clause:
            continue
        matched = CONDITION.match(clause)
        if not matched:
            return []
        conditions.append((matched.group(1), matched.group(2).upper(), unquote_values(matched.group(3))))
    return conditions


def matches(actual, operator, values) -> bool:
    text = "" if actual is None else str(actual)

    def compare():
        if not values:
            return 0
        try:
            return (float(text) > float(values[0])) - (float(text) < float(values[0]))
        except ValueError:
            return (text > values[0]) - (text < values[0])

    if operator == "=":
        return len(values) == 1 and text == values[0]
    if operator == "<>":
        return len(values) == 1 and text != values[0]
    if operator == ">":
        return compare() > 0
    if operator == ">=":
        return compare() >= 0
    if operator == "<":
        return compare() < 0
    if operator == "<=":
        return compare() <= 0
    if operator == "IN":
        return text in values
    if operator == "NOTIN":
        return text not in values
    return True


def apply_filters(rows: list, filters) -> list:
    for expression in filters or []:
        conditions = parse_conditions(expression)
        if not conditions:
            continue
        kept = []
        for row in rows:
            if all(matches(row.get(field), operator, values) for field, operator, values in conditions):
                kept.append(row)
        rows = kept
    return rows


def sort_rows(rows: list, orders) -> list:
    """按 Aloudata orders 的字段顺序排序，稳定支持 asc/desc。"""
    result = list(rows)
    if not isinstance(orders, list):
        return result
    specs = []
    for item in orders:
        if isinstance(item, dict):
            specs.extend((str(field), str(direction).lower() == "desc") for field, direction in item.items())
    for field, descending in reversed(specs):
        result.sort(key=lambda row: (row.get(field) is None, row.get(field)), reverse=descending)
    return result


def metric_aggregation(metric_name: str, body: dict) -> str:
    """从查询临时定义或指标元数据中读取聚合函数，夹具指标默认使用 SUM。"""
    expression = None
    definitions = body.get("metricDefinitions") or {}
    if isinstance(definitions, dict):
        definition = definitions.get(metric_name)
        if isinstance(definition, dict):
            expression = definition.get("expr") or definition.get("expression") or definition.get("formula")
        elif isinstance(definition, str):
            expression = definition

    if not expression:
        for definition in load_fixture("metric_batch_detail.json").get("data", []):
            if definition.get("metricName") == metric_name:
                caliber = definition.get("caliber") or {}
                expression = caliber.get("expr") or caliber.get("formula")
                break

    matched = re.match(r"\s*(sum|avg|average|min|max|count)\s*\(", str(expression or ""), re.IGNORECASE)
    return matched.group(1).lower() if matched else "sum"


def aggregate_rows(rows: list, dimensions: list, metrics: list, body: dict) -> list:
    """GROUP BY 请求维度并按指标定义聚合明细行。"""
    groups = {}
    for row in rows:
        key = tuple(row.get(dimension) for dimension in dimensions)
        groups.setdefault(key, []).append(row)

    result = []
    aggregations = {metric: metric_aggregation(metric, body) for metric in metrics}
    for key, group_rows in groups.items():
        grouped = {dimension: value for dimension, value in zip(dimensions, key)}
        for metric, aggregation in aggregations.items():
            values = [row.get(metric) for row in group_rows if row.get(metric) is not None]
            if aggregation == "count":
                value = len(values)
            elif not values:
                value = None
            elif aggregation in {"avg", "average"}:
                value = sum(values) / len(values)
            elif aggregation == "min":
                value = min(values)
            elif aggregation == "max":
                value = max(values)
            else:
                value = sum(values)
            grouped[metric] = value
        result.append(grouped)
    return result


def resolve_view_for_metrics(requested_metrics, requested_dimensions) -> dict:
    if (set(requested_metrics) | set(requested_dimensions)).issubset({"region", "order_date", "revenue"}):
        # seed 的双源门禁通过 metrics/query 下推 region=east；该路径与
        # 无筛选时的 analysisView/query 使用同一份确定性结果。
        return local_sales_view_result({})
    container = load_fixture("analysis_view_query_data.json")
    wanted = set(requested_metrics) | set(requested_dimensions)
    if not wanted:
        return container[VIEW_ORDER[0]]
    for view in VIEW_ORDER:
        candidate = container.get(view) or {}
        available = set(((candidate.get("data") or {}).get("table") or {}).get("columns", {}).keys())
        if available and wanted.issubset(available):
            return candidate
    return container[VIEW_ORDER[0]]


# --------------------------------------------------------------------- 端点实现


def handle_tree_list(query, body, headers):
    return replace_owner(load_fixture("analysis_view_tree.json"), headers.get("auth-value") or DEFAULT_OWNER)


def handle_view_list(query, body, headers):
    envelope_body = replace_owner(load_fixture("analysis_view_list.json"), headers.get("auth-value") or DEFAULT_OWNER)
    data = envelope_body["data"]
    items = data.get("data", [])
    keyword = (query.get("keyword") or [""])[0].strip()
    if keyword and keyword != "_":
        needle = keyword.lower()
        items = [
            item for item in items
            if needle in str(item.get("viewName", "")).lower() or needle in str(item.get("displayName", "")).lower()
        ]
    page_size = as_int((query.get("pageSize") or [None])[0], len(items))
    page_number = as_int((query.get("pageNumber") or [None])[0], 1)
    data["data"] = paginate(items, page_number, page_size)
    data["pageNumber"] = page_number
    data["pageSize"] = page_size
    data["totalPageSize"] = len(items)
    return envelope_body


def local_sales_view_detail(headers):
    """兼容 seed 脚本使用的 local_sales_view，本地验证只需最小可查询视图。"""
    owner = headers.get("auth-value") or DEFAULT_OWNER
    return envelope({
        "id": "local-sales-view",
        "viewName": "local_sales_view",
        "displayName": "本地销售视图",
        "description": "本地多源 E2E 销售视图",
        "metrics": [{"name": "revenue", "displayName": "收入", "dataType": "DECIMAL"}],
        "dimensions": [
            {"name": "region", "displayName": "区域", "dataType": "STRING"},
            {"name": "order_date", "displayName": "日期", "dataType": "DATE"},
        ],
        "filters": [],
        "resultFilters": [],
        "orders": [],
        "owner": owner,
    }, trace_id="mock-trace-local-sales-detail")


def local_sales_view_result(headers):
    """返回一行 east 结果，覆盖 seed 的 region=east 下推断言并保持确定性。"""
    rows = [{"region": "east", "order_date": "2026-01-01", "revenue": 120.50}]
    columns = rows_to_columns(rows, ("region", "order_date", "revenue"))
    return envelope({
        "table": {"columns": columns, "total": len(rows)},
        "metas": [
            {"name": "region", "dataTypeName": "VARCHAR"},
            {"name": "order_date", "dataTypeName": "DATE"},
            {"name": "revenue", "dataTypeName": "DECIMAL"},
        ],
        "total": len(rows),
    }, trace_id="mock-trace-local-sales-query")


def handle_query_by_name(query, body, headers):
    view_name = (query.get("viewName") or [None])[0]
    if view_name == "local_sales_view":
        return local_sales_view_detail(headers)
    container = load_fixture("analysis_view_query_by_name.json")
    payload = container.get(view_name)
    if not payload:
        return envelope(None, code="SM_02_0038", success=False, error="用户&资源没有权限",
                        trace_id="mock-trace-access-denied")
    return replace_owner(payload, headers.get("auth-value") or DEFAULT_OWNER)


def handle_analysis_view_query(query, body, headers):
    view_name = (query.get("viewName") or [None])[0]
    if view_name == "local_sales_view":
        return local_sales_view_result(headers)
    container = load_fixture("analysis_view_query_data.json")
    payload = container.get(view_name)
    if not payload:
        return envelope(None, code="SM_02_0038", success=False, error="用户&资源没有权限",
                        trace_id="mock-trace-access-denied")
    payload = replace_owner(payload, headers.get("auth-value") or DEFAULT_OWNER)
    data = payload.setdefault("data", {})
    table = data.setdefault("table", {})
    columns = table.get("columns", {})
    total = data.get("total") or max((len(v) for v in columns.values() if isinstance(v, list)), default=0)
    page_size = as_int((query.get("pageSize") or [None])[0], total)
    page_index = as_int((query.get("pageIndex") or [None])[0], 0)
    start = max(0, page_index * page_size)
    end = min(total, start + page_size)
    if start > 0 or end < total:
        table["columns"] = {name: cells[start:end] for name, cells in columns.items() if isinstance(cells, list)}
    table["total"] = total
    table["pageSize"] = page_size
    table["pageIndex"] = page_index
    data["total"] = total
    return payload


def handle_metrics_query(query, body, headers):
    metrics = body.get("metrics") or []
    dimensions = body.get("dimensions") or []
    filters = body.get("filters") or []
    if any(not isinstance(item, str) for item in filters):
        return envelope(None, code="SM99002", success=False, error="系统异常: filters 仅支持表达式字符串数组",
                        trace_id="mock-trace-SM99002")

    source = resolve_view_for_metrics(metrics, dimensions)
    source = replace_owner(source, headers.get("auth-value") or DEFAULT_OWNER)
    source_data = source.get("data") or {}
    source_columns = ((source_data.get("table") or {}).get("columns")) or {}
    requested = [name for name in list(dimensions) + list(metrics)]
    rows = columns_to_rows(source_columns)
    rows = apply_filters(rows, filters)
    if body.get("timeConstraint"):
        rows = apply_filters(rows, [body["timeConstraint"]])
    result_filters = body.get("resultFilters") or []
    if any(not isinstance(item, str) for item in result_filters):
        return envelope(None, code="SM99002", success=False,
                        error="系统异常: resultFilters 仅支持表达式字符串数组",
                        trace_id="mock-trace-SM99002")
    rows = aggregate_rows(rows, dimensions, metrics, body) if requested else rows
    rows = apply_filters(rows, result_filters)
    rows = sort_rows(rows, body.get("orders"))
    total_before_paging = len(rows)

    offset = as_int(body.get("offset"), 0)
    if source.get("traceId") == "mock-trace-local-sales-query":
        # DataAgent 当前内部请求可能携带脚本分页游标；本地单行视图仍需
        # 保留 region=east 的验证行，避免把下推筛选误判为空结果。
        offset = 0
    limit = as_int(body.get("limit"), len(rows))
    rows = rows[offset:offset + limit] if limit >= 0 else rows[offset:]

    metas = source_data.get("metas") or []
    kept_metas = [meta for meta in metas if not requested or meta.get("name") in requested]
    query_result_type = body.get("queryResultType") or "DATA"
    output_names = requested or list(source_columns.keys())
    data = {"table": {"columns": rows_to_columns(rows, output_names), "total": len(rows)},
            "metas": kept_metas,
            "total": total_before_paging if body.get("isQueryTotalCount") is True else len(rows),
            "queryResultType": query_result_type}
    if body.get("source") is not None:
        data["source"] = body["source"]
    if str(query_result_type).upper() in {"SQL", "SQL_AND_DATA"}:
        select = ", ".join(requested) if requested else "*"
        data["sql"] = f"SELECT {select} FROM mock_metrics"
    return envelope(data, trace_id=source.get("traceId", "mock-trace"))


def handle_batch_detail(query, body, headers):
    payload = replace_owner(load_fixture("metric_batch_detail.json"), headers.get("auth-value") or DEFAULT_OWNER)
    requested = set(query.get("metricNames") or [])
    if not requested:
        return payload
    payload["data"] = [item for item in payload.get("data", []) if item.get("metricName") in requested]
    return payload


def handle_dimension_list(query, body, headers):
    payload = replace_owner(load_fixture("dimension_list.json"), headers.get("auth-value") or DEFAULT_OWNER)
    data = payload["data"]
    items = data.get("data", [])
    keyword = str(body.get("keyword") or "").strip().lower()
    category_id = str(body.get("categoryId") or "").strip()
    if keyword:
        items = [item for item in items if keyword in str(item.get("dimName") or "").lower()
                 or keyword in str(item.get("dimDisplayName") or "").lower()]
    if category_id:
        allowed = category_descendants(load_fixture("category_list.json").get("data", []),
                                       category_id, "CATEGORY_DIMENSION")
        items = [item for item in items if item.get("dimCategoryId") in allowed]
    pager = body.get("pager") or {}
    page_size = as_int(pager.get("pageSize"), len(items))
    page_number = as_int(pager.get("pageNumber"), 1)
    page = paginate(items, page_number, page_size)
    data["data"] = page
    data["pageNumber"] = page_number
    data["pageSize"] = page_size
    data["total"] = len(items)
    data["hasNext"] = (page_number * page_size) < len(items)
    return payload


def query_value(query, key):
    value = query.get(key)
    return value[0] if isinstance(value, list) and value else value


def category_descendants(categories, category_id, category_type):
    ids = {category_id}
    changed = True
    while changed:
        changed = False
        for item in categories:
            if item.get("categoryType") == category_type and item.get("parentId") in ids:
                if item.get("id") not in ids:
                    ids.add(item.get("id"))
                    changed = True
    return ids


def handle_dimension_values(query, body, headers):
    """返回指定维度的去重值，数据来源于策略解读-子策略-维度视图夹具。"""
    view = load_fixture("analysis_view_query_data.json").get("cljd_zcl_wd_view", {})
    columns = ((view.get("data") or {}).get("table") or {}).get("columns") or {}
    dim_name = str(body.get("dimName") or "")
    cells = columns.get(dim_name) or []

    values = []
    seen = set()
    for cell in cells:
        value = cell.get("value") if isinstance(cell, dict) else cell
        if value is None:
            continue
        text = str(value)
        if text not in seen:
            seen.add(text)
            values.append(text)

    keyword = str(body.get("dimValueKeyword") or "").strip().lower()
    if keyword:
        values = [value for value in values if keyword in value.lower()]

    page_size = as_int(body.get("pageSize"), 200)
    page_number = as_int(body.get("pageNumber"), 1)
    return envelope(paginate(values, page_number, page_size), trace_id="mock-trace-dimension-values")


def handle_metric_list(query, body, headers):
    payload = replace_owner(load_fixture("metric_batch_detail.json"), headers.get("auth-value") or DEFAULT_OWNER)
    data = payload.get("data", [])
    keyword = str(query_value(query, "keyword") or "").strip().lower()
    category_id = str(query_value(query, "metricCategoryId") or query_value(query, "categoryId") or "").strip()
    if keyword:
        data = [item for item in data if keyword in str(item.get("metricName") or "").lower()
                or keyword in str(item.get("metricDisplayName") or "").lower()]
    if category_id:
        allowed = category_descendants(load_fixture("category_list.json").get("data", []),
                                       category_id, "CATEGORY_METRIC")
        data = [item for item in data if item.get("metricCategoryId") in allowed]
    page_size = as_int(query_value(query, "pageSize"), len(data))
    page_number = as_int(query_value(query, "pageNumber"), 1)
    return envelope({"total": len(data), "pageNumber": page_number, "pageSize": page_size,
                     "hasNext": page_number * page_size < len(data),
                     "data": paginate(data, page_number, page_size)})


def handle_dimension_all(query, body, headers):
    requested = query.get("metricNames") or []
    if not isinstance(requested, list):
        requested = [requested]
    definitions = load_fixture("analysis_view_query_by_name.json")
    strategy_metrics = []
    strategy_dimensions = []
    for definition_envelope in definitions.values():
        definition = definition_envelope.get("data") or {}
        for metric_name in definition.get("metrics") or []:
            if metric_name not in strategy_metrics:
                strategy_metrics.append(metric_name)
        for dimension_name in definition.get("dimensions") or []:
            if dimension_name not in strategy_dimensions:
                strategy_dimensions.append(dimension_name)
    relations = {
        metric_name: list(strategy_dimensions)
        for metric_name in strategy_metrics
        if not requested or metric_name in requested
    }
    return envelope(relations)


def handle_category_list(query, body, headers):
    category_type = query_value(query, "categoryType")
    categories = load_fixture("category_list.json").get("data", [])
    if category_type:
        categories = [item for item in categories if item.get("categoryType") == category_type]
    return envelope(categories)


def handle_dimension_detail(query, body, headers):
    dim_name = query_value(query, "dimName")
    payload = load_fixture("dimension_list.json")
    dimensions = (payload.get("data") or {}).get("data") or []
    detail = next((item for item in dimensions if item.get("dimName") == dim_name), None)
    return envelope(detail)


def handle_orders(query, body, headers):
    """订单 HTTP 数据集 fixture，支持 E2E 查询中的 status 等值过滤。"""
    rows = [
        {"id": 1001, "customer_id": 101, "region": "east", "status": "PAID", "amount": 120.50},
        {"id": 1002, "customer_id": 102, "region": "west", "status": "PAID", "amount": 80.00},
        {"id": 1003, "customer_id": 101, "region": "east", "status": "CANCELLED", "amount": 30.00},
        {"id": 1004, "customer_id": 103, "region": "south", "status": "PAID", "amount": 210.00},
        {"id": 1005, "customer_id": 999, "region": "unknown", "status": "PENDING", "amount": 10.00},
        {"id": 1006, "customer_id": 101, "region": "east", "status": "SHIPPED", "amount": 45.75},
        {"id": 1007, "customer_id": 102, "region": "west", "status": "CANCELLED", "amount": 66.20},
        {"id": 1008, "customer_id": 103, "region": "south", "status": "REFUNDED", "amount": 19.99},
        {"id": 1009, "customer_id": 999, "region": "unknown", "status": "FAILED", "amount": 5.50},
        {"id": 1010, "customer_id": 101, "region": "east", "status": "PROCESSING", "amount": 300.00},
    ]
    status = (query.get("status") or [None])[0]
    order_id = (query.get("id") or [None])[0]
    if status:
        rows = [row for row in rows if row["status"] == status]
    if order_id:
        rows = [row for row in rows if str(row["id"]) == order_id]
    return {"data": rows}


ROUTES = {
    ("GET", f"{ANYMETRICS}/analysisview/treeList"): handle_tree_list,
    ("GET", f"{ANYMETRICS}/analysisview/list"): handle_view_list,
    ("GET", f"{ANYMETRICS}/analysisview/queryByName"): handle_query_by_name,
    ("GET", f"{ANYMETRICS}/metrics/batchDetail"): handle_batch_detail,
    ("GET", f"{ANYMETRICS}/metrics/list"): handle_metric_list,
    ("GET", f"{ANYMETRICS}/metrics/dimensionAll"): handle_dimension_all,
    ("POST", f"{ANYMETRICS}/dimension/list"): handle_dimension_list,
    ("POST", f"{ANYMETRICS}/dimension/values"): handle_dimension_values,
    ("GET", f"{ANYMETRICS}/dimension/detail"): handle_dimension_detail,
    ("GET", f"{ANYMETRICS}/category/list"): handle_category_list,
    ("GET", f"{SEMANTIC}/analysisView/query"): handle_analysis_view_query,
    ("POST", f"{SEMANTIC}/metrics/query"): handle_metrics_query,
    ("GET", "/orders"): handle_orders,
}


def summarize_body(body: dict) -> str:
    """请求体摘要：列表字段只报条数，筛选/分页等关键字段原样打印，便于与正式请求对账。"""
    summary = {}
    for key, value in body.items():
        if isinstance(value, list):
            summary[key] = ("%d 项" % len(value)) if key in ("metrics", "dimensions") else value
        else:
            summary[key] = value
    return json.dumps(summary, ensure_ascii=False)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock] %s %s\n" % (self.command, self.path))

    def _send(self, status, payload):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.wfile.flush()

    def _read_body(self) -> bytes:
        """读取请求体：RestTemplate 发 Map body 时用 chunked 编码且不带 Content-Length。"""
        encoding = (self.headers.get("Transfer-Encoding") or "").lower()
        if "chunked" in encoding:
            chunks = []
            while True:
                line = self.rfile.readline().strip().split(b";")[0]
                size = int(line or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def _handle(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query, keep_blank_values=True)
        raw = self._read_body()
        body = json.loads(raw) if raw else {}
        detail = []
        if query:
            detail.append("query=" + json.dumps({k: v[0] if len(v) == 1 else v for k, v in query.items()}, ensure_ascii=False))
        if body:
            detail.append("body=" + summarize_body(body))
        if detail:
            sys.stderr.write("[mock] %s %s %s\n" % (self.command, parsed.path, " ".join(detail)))
        handler = ROUTES.get((self.command, parsed.path))
        if handler is None:
            methods_for_path = sorted({method for method, path in ROUTES if path == parsed.path})
            if methods_for_path:
                self._send(405, envelope(None, code="SM_04_0001", success=False,
                                         error=f"不支持的请求方式 {self.command} {parsed.path}，"
                                               f"该端点仅支持 {methods_for_path}"))
            else:
                self._send(404, envelope(None, code="SM_04_0004", success=False,
                                         error=f"未 mock 的端点 {self.command} {parsed.path}"))
            return
        self._send(200, handler(query, body, self.headers))

    do_GET = _handle
    do_POST = _handle


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Aloudata 本地 mock 服务（路径/参数/请求方式与真实一致）")
    parser.add_argument("--port", type=int, default=int(os.environ.get("MOCK_PORT", "18081")))
    parser.add_argument("--fixtures", default=os.environ.get("MOCK_FIXTURES", DEFAULT_FIXTURES))
    parser.add_argument("--host", default=os.environ.get("MOCK_HOST", "127.0.0.1"))
    args = parser.parse_args()

    FIXTURES_DIR = os.path.abspath(args.fixtures)
    if not os.path.isdir(FIXTURES_DIR):
        sys.exit("夹具目录不存在: %s" % FIXTURES_DIR)

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print("Aloudata mock 服务已启动: http://%s:%d（夹具目录 %s）" % (args.host, args.port, FIXTURES_DIR))
    print("已注册端点：")
    for method, path in ROUTES:
        print("  %-4s %s" % (method, path))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
