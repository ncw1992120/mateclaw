from runner.executor import TaskExecutor
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading

def test_timeout_kills_process():
    result = TaskExecutor().start("timeout", "import time; time.sleep(2)", {}, 1, 100)
    assert result["status"] == "TIMEOUT"

def test_output_limit_is_terminal():
    result = TaskExecutor().start("large", "print('x' * 1000)", {}, 5, 100)
    assert result["status"] == "OUTPUT_LIMIT"

def test_result_is_serialized_separately_from_logs():
    result = TaskExecutor().start("result", "print('log'); result = [{'id': 1}]", {}, 5, 1000)
    assert result["status"] == "SUCCEEDED"
    assert result["output"].strip() == "log"
    assert result["result"] == '[{"id": 1}]'

def test_large_result_without_upload_is_rejected():
    result = TaskExecutor().start("result-limit", "result = [{'value': 'x' * 1000}]", {}, 5, 1000, 100)
    assert result["status"] == "RESULT_LIMIT"
    assert result["outputRef"] is None

def test_large_result_uploads_parquet_reference():
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            self.server.content_type = self.headers.get("Content-Type")
            self.server.body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            payload = json.dumps({"code": 200, "data": {"objectId": "task/result", "format": "parquet"}}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(payload))); self.end_headers(); self.wfile.write(payload)
        def log_message(self, *_): pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.handle_request, daemon=True).start()
    result = TaskExecutor().start("result-ref", "result = [{'value': 'x' * 1000}]", {}, 5, 1000, 100, f"http://127.0.0.1:{server.server_port}/result", "secret")
    assert result["status"] == "RESULT_REF"
    assert result["outputRef"]["format"] == "parquet"
    assert server.content_type == "application/vnd.apache.parquet"
    assert server.body[:4] == b"PAR1"

def test_file_size_limit_is_applied_to_child_process():
    result = TaskExecutor().start(
        "file-limit", "open('too-large.bin', 'wb').write(b'x' * (2 * 1024 * 1024))", {},
        5, 1000, 1000, None, None, 256, 1024 * 1024
    )
    assert result["status"] == "FAILED"

def test_child_does_not_inherit_arbitrary_runner_environment(monkeypatch):
    monkeypatch.setenv("MATECLAW_TEST_SECRET", "should-not-be-visible")
    result = TaskExecutor().start(
        "env-isolation", "result = os.environ.get('MATECLAW_TEST_SECRET', 'missing')", {},
        5, 1000
    )
    assert result["status"] == "SUCCEEDED"
    assert result["result"] == '[{"value": "missing"}]'

def test_dataset_client_receives_task_parameters():
    result = TaskExecutor().start(
        "task-parameters", "result = [{'region': datasets.params.require('region')}]",
        {"MATECLAW_DATASET_ENDPOINT": "http://dataagent/read", "MATECLAW_READ_TOKEN": "secret"},
        5, 1000, parameters={"region": "east"}
    )
    assert result["status"] == "SUCCEEDED"
    assert result["result"] == '[{"region": "east"}]'

def test_two_dataset_aliases_can_be_joined_in_runner():
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            request = json.loads(self.rfile.read(length))
            if request["inputName"] == "orders":
                body = {"descriptor": {"schema": [
                    {"name": "order_id", "dataType": "int"},
                    {"name": "customer_id", "dataType": "int"},
                    {"name": "amount", "dataType": "double"},
                ]}, "rows": [
                    {"order_id": 101, "customer_id": 7, "amount": 12.5},
                    {"order_id": 102, "customer_id": 8, "amount": 8.0},
                ]}
            elif request["inputName"] == "customers":
                body = {"descriptor": {"schema": [
                    {"name": "customer_id", "dataType": "int"},
                    {"name": "customer_name", "dataType": "string"},
                ]}, "rows": [
                    {"customer_id": 7, "customer_name": "Alice"},
                    {"customer_id": 8, "customer_name": "Bob"},
                ]}
            else:
                self.send_response(404)
                self.end_headers()
                return
            encoded = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def log_message(self, *_):
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = TaskExecutor().start(
            "join-aliases",
            """orders = datasets.read('orders').to_pandas()
customers = datasets.read('customers').to_pandas()
result = orders.merge(customers, on='customer_id').to_dict(orient='records')""",
            {"MATECLAW_DATASET_ENDPOINT": f"http://127.0.0.1:{server.server_port}/read", "MATECLAW_READ_TOKEN": "secret"},
            5, 1000,
        )
    finally:
        server.shutdown()
        thread.join(timeout=2)

    assert result["status"] == "SUCCEEDED"
    assert json.loads(result["result"]) == [
        {"order_id": 101, "customer_id": 7, "amount": 12.5, "customer_name": "Alice"},
        {"order_id": 102, "customer_id": 8, "amount": 8.0, "customer_name": "Bob"},
    ]
