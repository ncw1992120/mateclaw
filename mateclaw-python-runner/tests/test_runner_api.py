from fastapi.testclient import TestClient
from runner.app import app

client = TestClient(app)

def test_health_and_rejects_requirements():
    assert client.get("/health").json() == {"status": "UP"}
    payload = {"taskId":"t1","script":"print('ok')","datasetReadEndpoint":"http://dataagent/read","readToken":"secret","requirements":["x"]}
    assert client.post("/v1/tasks", json=payload).status_code == 422
    payload.pop("requirements")
    payload["datasetReadEndpoint"] = "https://example.com/read"
    assert client.post("/v1/tasks", json=payload).status_code == 422

def test_task_runs_and_reports_status():
    response = client.post("/v1/tasks", json={"taskId":"t2","script":"print('ok', type(datasets).__name__)","datasetReadEndpoint":"http://dataagent/read","readToken":"secret"})
    assert response.status_code == 202
    import time
    for _ in range(100):
        status = client.get("/v1/tasks/t2").json()
        if status["status"] != "RUNNING": break
        time.sleep(.02)
    assert status["status"] == "SUCCEEDED" and "ok DatasetClient" in status["output"]

def test_token_is_redacted_from_result():
    response = client.post("/v1/tasks", json={"taskId":"t3","script":"print(__import__('os').environ['MATECLAW_READ_TOKEN'])","datasetReadEndpoint":"http://dataagent/read","readToken":"secret-token"})
    assert response.status_code == 202
    import time
    for _ in range(100):
        status = client.get("/v1/tasks/t3").json()
        if status["status"] != "RUNNING": break
        time.sleep(.02)
    assert "secret-token" not in (status["output"] or "") and "[REDACTED]" in status["output"]

def test_task_returns_script_result_separately():
    response = client.post("/v1/tasks", json={"taskId":"t-result", "script":"result = [{'id': 1}]", "datasetReadEndpoint":"http://dataagent/read", "readToken":"secret"})
    assert response.status_code == 202
    import time
    for _ in range(100):
        status = client.get("/v1/tasks/t-result").json()
        if status["status"] != "RUNNING": break
        time.sleep(.02)
    assert status["status"] == "SUCCEEDED" and status["result"] == '[{"id": 1}]'

def test_executor_crash_is_terminal_failure(monkeypatch):
    def crash(*args, **kwargs):
        raise RuntimeError("executor crashed")

    monkeypatch.setattr("runner.app.executor.start", crash)
    response = client.post("/v1/tasks", json={"taskId":"t-executor-crash", "script":"print('never')", "datasetReadEndpoint":"http://dataagent/read", "readToken":"secret"})
    assert response.status_code == 202
    import time
    for _ in range(100):
        status = client.get("/v1/tasks/t-executor-crash").json()
        if status["status"] != "RUNNING": break
        time.sleep(.02)
    assert status["status"] == "FAILED"
    assert status["error"] == "executor crashed"
