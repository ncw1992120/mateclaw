"""prepared input 契约测试（实施计划任务 4）。

覆盖：
- DatasetClient.input() 请求体只包含 inputName（令牌在 Header）
- 内联完整输入 / 空结果
- 大结果 ObjectRef 受控批次读取物化完整行集
- 批次游标不推进时显式失败（防死循环）
- 令牌过期返回可识别错误（PreparedInputExpiredError）
- read() 旧语义保持不变
"""
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from mateclaw.datasets import DatasetClient
from mateclaw.types import DatasetInput, PreparedInputExpiredError, ResourceLimitError


class FakeDataAgent(BaseHTTPRequestHandler):
    """模拟 /internal/v1/script-tasks/{id}/datasets/input 的最小行为。"""

    prepared: dict = {}  # inputName -> payload（rows 或 objectRef 分批）
    requests: list = []

    def do_POST(self):  # noqa: N802
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        FakeDataAgent.requests.append({"path": self.path, "body": body,
                                       "auth": self.headers.get("Authorization", "")})
        name = body.get("inputName")
        spec = FakeDataAgent.prepared.get(name)
        if spec is None:
            self._reply(400, {"code": "500", "message": "input alias is not declared"})
            return
        if spec.get("expired"):
            self._reply(401, {"code": "401", "message": "PREPARED_INPUT_EXPIRED: expired"})
            return
        if set(body.keys()) - {"inputName", "cursor", "batchSize"}:
            self._reply(400, {"code": "400", "message": "only accepts inputName"})
            return
        if spec.get("object_batches"):
            cursor = body.get("cursor", 0)
            if spec.get("stuck"):
                # 模拟服务端未推进游标且未结束
                self._reply(200, {"code": 200, "data": {
                    "inputName": name, "schema": spec["schema"], "rowCount": spec["rowCount"],
                    "rows": spec["object_batches"][0], "cursor": cursor, "last": False,
                    "objectRef": spec.get("objectRef")}})
                return
            batches = spec["object_batches"]
            batch = batches[cursor // spec["batchSize"]] if cursor // spec["batchSize"] < len(batches) else []
            last = cursor + len(batch) >= spec["rowCount"]
            self._reply(200, {"code": 200, "data": {
                "inputName": name, "schema": spec["schema"], "rowCount": spec["rowCount"],
                "rows": batch, "cursor": cursor + len(batch), "last": last,
                "objectRef": spec.get("objectRef") if cursor == 0 else None}})
            return
        self._reply(200, {"code": 200, "data": {
            "inputName": name, "schema": spec["schema"], "rowCount": len(spec["rows"]),
            "rows": spec["rows"], "objectRef": None}})

    def _reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):  # 静默
        pass


@pytest.fixture()
def server():
    FakeDataAgent.prepared = {}
    FakeDataAgent.requests = []
    httpd = HTTPServer(("127.0.0.1", 0), FakeDataAgent)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{httpd.server_port}"
    httpd.shutdown()


def make_client(server):
    return DatasetClient(server + "/internal/v1/script-tasks/task-1/datasets/read", "token-1",
                         input_endpoint=server + "/internal/v1/script-tasks/task-1/datasets/input")


def test_input_request_only_carries_input_name(server):
    FakeDataAgent.prepared["strategy_data"] = {
        "schema": [{"name": "strategy_id", "dataType": "STRING"},
                   {"name": "in_account", "dataType": "DECIMAL"}],
        "rows": [{"strategy_id": "A", "in_account": 120.5},
                 {"strategy_id": "B", "in_account": 80.0}],
    }
    client = make_client(server)
    frame = client.input("strategy_data")
    # 请求体只有 inputName，令牌在 Header
    assert FakeDataAgent.requests[-1]["body"] == {"inputName": "strategy_data"}
    assert FakeDataAgent.requests[-1]["auth"] == "Bearer token-1"
    assert frame.row_count == 2
    assert len(frame.rows) == 2
    polars = frame.to_polars()
    assert polars.height == 2
    assert polars["in_account"].sum() == 200.5


def test_input_empty_result_is_valid(server):
    FakeDataAgent.prepared["empty_input"] = {
        "schema": [{"name": "strategy_id", "dataType": "STRING"}],
        "rows": [],
    }
    frame = make_client(server).input("empty_input")
    assert frame.row_count == 0
    assert frame.to_polars().height == 0


def test_large_result_materializes_complete_rows_in_batches(server):
    all_rows = [{"strategy_id": f"S{i}", "in_account": float(i)} for i in range(25)]
    FakeDataAgent.prepared["big"] = {
        "schema": [{"name": "strategy_id", "dataType": "STRING"}],
        "object_batches": [all_rows[0:10], all_rows[10:20], all_rows[20:25]],
        "batchSize": 10,
        "rowCount": 25,
        "objectRef": {"objectId": "obj-1"},
    }
    client = make_client(server)
    frame = client.input("big")
    assert frame.rows == ()          # 首批不内联行集
    assert frame.object_ref is not None
    assert frame.row_count == 25
    polars = frame.to_polars()       # 受控批次读取后物化完整输入
    assert polars.height == 25
    # 批次续读请求携带游标（首批 input() 请求本身不含 cursor）
    cursors = [r["body"]["cursor"] for r in FakeDataAgent.requests
               if r["body"]["inputName"] == "big" and "cursor" in r["body"]]
    assert cursors == [0, 10, 20]


def test_stuck_cursor_fails_instead_of_looping(server):
    FakeDataAgent.prepared["stuck"] = {
        "schema": [{"name": "v", "dataType": "DECIMAL"}],
        "object_batches": [[{"v": 1}]],
        "batchSize": 10,
        "rowCount": 5,
        "objectRef": {"objectId": "obj-2"},
        "stuck": True,
    }
    frame = make_client(server).input("stuck")
    with pytest.raises(RuntimeError, match="cursor did not advance"):
        frame.to_polars()


def test_expired_prepared_input_raises_recognizable_error(server):
    FakeDataAgent.prepared["strategy_data"] = {"expired": True, "schema": [], "rows": []}
    with pytest.raises(PreparedInputExpiredError):
        make_client(server).input("strategy_data")


def test_input_without_endpoint_configured_fails_fast(server):
    client = DatasetClient(server + "/x", "token-1")
    with pytest.raises(RuntimeError, match="prepared input endpoint"):
        client.input("strategy_data")


def test_read_legacy_contract_unchanged(server):
    """旧 read() 保持按需读取语义（columns/filters 进入请求体）。"""
    captured = {}

    original_do_POST = FakeDataAgent.do_POST

    def spy_do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        captured.update(json.loads(self.rfile.read(length) or b"{}"))
        self.send_response(200)
        payload = json.dumps({"code": 200, "data": {"schema": [], "rows": [], "objectRef": None}}).encode()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    FakeDataAgent.do_POST = spy_do_POST
    try:
        client = make_client(server)
        frame = client.read("legacy_input", columns=["a"], filters=[{"field": "a", "operator": "eq", "value": 1}])
        assert isinstance(frame, DatasetInput)
        assert captured["inputName"] == "legacy_input"
        assert captured["columns"] == ["a"]
        assert captured["filters"][0]["field"] == "a"
    finally:
        FakeDataAgent.do_POST = original_do_POST
