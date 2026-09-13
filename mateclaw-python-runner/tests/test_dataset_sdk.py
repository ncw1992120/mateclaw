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


def test_read_rejects_unsupported_filter_operator_before_network_call():
    client = DatasetClient("http://127.0.0.1:1/read", "token")
    with pytest.raises(ValueError, match="unsupported filter operator"):
        client.read("orders", filters=[Filter("status", "contains", "PAID")])


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
