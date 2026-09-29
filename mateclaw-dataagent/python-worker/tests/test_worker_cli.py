import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer


PACKAGE_ROOT = os.path.dirname(os.path.dirname(__file__))
SOURCE_ROOT = os.path.join(PACKAGE_ROOT, "src")


def run_worker(tmp_path, request):
    request_path = tmp_path / "request.json"
    response_path = tmp_path / "response.json"
    request_path.write_text(json.dumps(request), encoding="utf-8")
    environment = os.environ.copy()
    environment["PYTHONPATH"] = SOURCE_ROOT + os.pathsep + environment.get("PYTHONPATH", "")
    completed = subprocess.run(
        [sys.executable, "-m", "runner.worker", "--request", str(request_path), "--response", str(response_path)],
        cwd=PACKAGE_ROOT,
        env=environment,
        text=True,
        capture_output=True,
        timeout=10,
        check=False,
    )
    response = json.loads(response_path.read_text(encoding="utf-8")) if response_path.exists() else None
    return completed, response


def task_request(script, **overrides):
    request = {
        "taskId": "task-worker-1",
        "script": script,
        "inputCatalog": {},
        "parameters": {},
        "limits": {"timeout_seconds": 5, "max_stdout_bytes": 4096, "max_result_bytes": 4096},
        "datasetReadEndpoint": "http://127.0.0.1:18089/dataagent/api/internal/read",
        "datasetInputEndpoint": "http://127.0.0.1:18089/dataagent/api/internal/input",
        "preferPreparedInputs": True,
        "readToken": "task-scoped-token",
    }
    request.update(overrides)
    return request


def test_worker_cli_writes_versioned_result_response(tmp_path):
    completed, response = run_worker(tmp_path, task_request("result = [{'amount': 12.5}]"))

    assert completed.returncode == 0
    assert response["taskId"] == "task-worker-1"
    assert response["status"] == "SUCCEEDED"
    assert response["result"]["schemaVersion"] == "1.0"
    assert response["result"]["kind"] == "table"
    assert response["result"]["data"]["rows"] == [{"amount": 12.5}]
    assert response["result"]["meta"]["rowCount"] == 1


def test_worker_keeps_user_stdout_separate_from_response_json(tmp_path):
    completed, response = run_worker(tmp_path, task_request("print('user log')\nresult = 7"))

    assert completed.returncode == 0
    assert "user log" in completed.stdout
    assert "schemaVersion" not in completed.stdout
    assert response["output"].strip() == "user log"
    assert response["result"]["kind"] == "scalar"


def test_worker_marks_stdout_over_limit_without_buffering_unbounded_output(tmp_path):
    request = task_request("print('x' * 20000)\nresult = 7")
    request["limits"]["max_stdout_bytes"] = 32
    completed, response = run_worker(tmp_path, request)

    assert completed.returncode == 0
    assert response["status"] == "OUTPUT_LIMIT"
    assert len(response["output"].encode("utf-8")) <= 32
    assert response["result"] is None


def test_worker_script_errors_are_terminal_and_token_is_redacted(tmp_path):
    completed, response = run_worker(
        tmp_path,
        task_request("raise RuntimeError(datasets._token)"),
    )

    assert completed.returncode == 0
    assert response["status"] == "FAILED"
    assert "task-scoped-token" not in response["error"]
    assert "[REDACTED]" in response["error"]


def test_worker_reads_prepared_dataset_input(tmp_path):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
            assert body == {"inputName": "sales"}
            assert self.headers.get("Authorization") == "Bearer task-scoped-token"
            payload = json.dumps({"code": 200, "data": {
                "inputName": "sales",
                "schema": [{"name": "amount", "dataType": "number"}],
                "rowCount": 2,
                "rows": [{"amount": 4}, {"amount": 9}],
            }}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_):
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        endpoint = f"http://127.0.0.1:{server.server_port}/input"
        request = task_request(
            "result = datasets.input('sales').to_pandas().to_dict(orient='records')",
            datasetInputEndpoint=endpoint,
            datasetReadEndpoint=endpoint,
        )
        completed, response = run_worker(tmp_path, request)
    finally:
        server.shutdown()
        thread.join(timeout=2)

    assert completed.returncode == 0
    assert response["status"] == "SUCCEEDED"
    assert response["result"]["data"]["rows"] == [{"amount": 4}, {"amount": 9}]


def test_worker_rejects_missing_and_unknown_request_fields(tmp_path):
    missing = task_request("result = 1")
    del missing["readToken"]
    completed, response = run_worker(tmp_path, missing)
    assert completed.returncode != 0
    assert response["status"] == "FAILED"
    assert "readToken" in str(response["error"])

    unknown = task_request("result = 1", arbitraryCommand="run this")
    completed, response = run_worker(tmp_path, unknown)
    assert completed.returncode != 0
    assert response["status"] == "FAILED"
    assert "arbitraryCommand" in str(response["error"])


def test_worker_import_does_not_require_posix_resource_module(tmp_path):
    script = "import builtins, importlib.util; original = builtins.__import__; " \
             "builtins.__import__ = lambda name, *args, **kwargs: (_ for _ in ()).throw(ImportError(name)) " \
             "if name == 'resource' else original(name, *args, **kwargs); " \
             "import runner.worker; print('worker-imported')"
    completed = subprocess.run(
        [sys.executable, "-c", script],
        cwd=PACKAGE_ROOT,
        env={**os.environ, "PYTHONPATH": SOURCE_ROOT},
        text=True,
        capture_output=True,
        timeout=10,
        check=False,
    )
    assert completed.returncode == 0, completed.stderr
    assert completed.stdout.strip() == "worker-imported"
