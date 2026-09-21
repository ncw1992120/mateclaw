import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading
import pytest

from mateclaw.datasets import DatasetClient
from mateclaw.filters import Filter

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        self.server.payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        body = {"schema": [{"name": "id", "dataType": "integer"}], "rows": [{"id": 1}]}
        data = json.dumps(body).encode(); self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def log_message(self, *_): pass


class WrappedHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = {"code": 200, "msg": "success", "data":
                {"schema": [{"name": "id", "dataType": "integer"}], "rows": [{"id": 2}]}}
        data = json.dumps(body).encode(); self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def log_message(self, *_): pass


class ErrorEnvelopeHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = {"code": 403, "msg": "dataset access denied", "data": None}
        data = json.dumps(body).encode(); self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def log_message(self, *_): pass

def test_read_sends_alias_filters_and_params():
    server = HTTPServer(("127.0.0.1", 0), Handler); threading.Thread(target=server.handle_request, daemon=True).start()
    client = DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token", {"region": "east"})
    result = client.read("orders", ["id"], [Filter("status", "eq", "PAID")])
    assert result.rows == ({"id": 1},)
    assert server.payload["inputName"] == "orders" and "datasetId" not in server.payload
    assert server.payload["filters"][0]["field"] == "status"
    assert server.payload["filters"][0]["role"] == "dimension"


def test_filter_mapping_defaults_to_dimension_role():
    server = HTTPServer(("127.0.0.1", 0), Handler); threading.Thread(target=server.handle_request, daemon=True).start()
    DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token").read(
        "orders", filters=[{"field": "amount", "operator": "gt", "value": 10}])
    assert server.payload["filters"][0]["role"] == "dimension"


def test_read_rejects_unsupported_filter_operator_before_network_call():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="unsupported filter operator"):
        client.read("orders", filters=[Filter("status", "like", "PAID")])


def test_read_supports_contains_fuzzy_operator():
    server = HTTPServer(("127.0.0.1", 0), Handler); threading.Thread(target=server.handle_request, daemon=True).start()
    DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token").read(
        "orders", filters=[{"field": "status", "operator": "contains", "value": "PAID"}])
    assert server.payload["filters"][0]["operator"] == "contains"


def test_result_from_dataset_a_filters_dataset_b():
    class RouterHandler(BaseHTTPRequestHandler):
        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            self.server.payload = payload
            if payload["inputName"] == "dataset_a":
                rows = [{"customer_id": 7}, {"customer_id": 8}, {"customer_id": 9}]
            else:
                rows = [{"customer_id": 7, "amount": 12.5}, {"customer_id": 10, "amount": 1.0}]
                # 模拟源端应用结构化 in 条件（验证 SDK 确实发送了 filters）
                for f in payload.get("filters", []):
                    if f.get("operator") == "in":
                        allowed = set(f.get("value") or [])
                        rows = [row for row in rows if row.get(f["field"]) in allowed]
            body = {"schema": [{"name": "customer_id", "dataType": "integer"}], "rows": rows}
            data = json.dumps(body).encode(); self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
        def log_message(self, *_): pass

    server = HTTPServer(("127.0.0.1", 0), RouterHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    datasets_client = DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token")
    ids = [row["customer_id"] for row in datasets_client.read("dataset_a").rows]
    result = datasets_client.read("dataset_b", filters=[
        {"field": "customer_id", "operator": "in", "value": ids}
    ])
    server.shutdown()
    assert result.rows == ({"customer_id": 7, "amount": 12.5},)


def test_empty_in_list_returns_empty_result_without_network_call():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    result = client.read("dataset_b", filters=[{"field": "customer_id", "operator": "in", "value": []}])
    assert result.rows == () and result.schema == ()


def test_in_with_more_than_1000_values_is_rejected_before_request():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="filter value count exceeds 1000"):
        client.read("dataset_b", filters=[
            {"field": "customer_id", "operator": "in", "value": list(range(1001))}
        ])


def test_more_than_50_filters_is_rejected_before_request():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="too many filters"):
        client.read("dataset_b", filters=[
            {"field": f"f{i}", "operator": "eq", "value": i} for i in range(51)
        ])


def test_between_requires_two_element_list():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="between requires a two-element list"):
        client.read("orders", filters=[{"field": "amount", "operator": "between", "value": [1, 2, 3]}])


def test_read_rejects_malformed_filter_mapping_before_network_call():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="invalid filter field"):
        client.read("orders", filters=[{"operator": "eq", "value": "PAID"}])


def test_read_unwraps_dataagent_response_envelope():
    server = HTTPServer(("127.0.0.1", 0), WrappedHandler); threading.Thread(target=server.handle_request, daemon=True).start()
    result = DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token").read("orders")
    assert result.rows == ({"id": 2},)


def test_read_rejects_business_error_in_dataagent_envelope():
    server = HTTPServer(("127.0.0.1", 0), ErrorEnvelopeHandler); threading.Thread(target=server.handle_request, daemon=True).start()
    with pytest.raises(RuntimeError, match="dataset access denied"):
        DatasetClient(f"http://127.0.0.1:{server.server_port}/read", "token").read("orders")
