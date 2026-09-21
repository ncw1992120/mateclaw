from concurrent.futures import ThreadPoolExecutor
from fastapi import FastAPI, HTTPException
from .executor import TaskExecutor
from .models import TaskRequest, TaskResponse

app = FastAPI(title="MateClaw Python Runner")
executor = TaskExecutor(); pool = ThreadPoolExecutor(max_workers=4); tasks: dict[str, TaskResponse] = {}

@app.get("/health")
def health(): return {"status": "UP"}

@app.post("/v1/tasks", response_model=TaskResponse, status_code=202)
async def submit(request: TaskRequest):
    if request.taskId in tasks and tasks[request.taskId].status in {"RUNNING", "SUCCEEDED", "FAILED", "TIMEOUT", "CANCELLED"}: raise HTTPException(409, "task already exists")
    tasks[request.taskId] = TaskResponse(taskId=request.taskId, status="RUNNING")
    def run():
        try:
            result = executor.start(request.taskId, request.script,
                {"MATECLAW_DATASET_ENDPOINT": request.datasetReadEndpoint, "MATECLAW_READ_TOKEN": request.readToken},
                request.limits.timeout_seconds, request.limits.max_stdout_bytes, request.limits.max_result_bytes,
                request.resultUploadEndpoint, request.readToken,
                request.limits.max_memory_mb, request.limits.max_file_bytes,
                request.parameters)
            token = request.readToken
            def redact(value): return (value or "").replace(token, "[REDACTED]")
            # result 为结构化 envelope（dict）；字符串仅在异常路径兜底
            result_value = result.get("result")
            error_value = result.get("error")
            tasks[request.taskId] = TaskResponse(taskId=request.taskId, status=result["status"], output=redact(result["output"]),
                result=result_value if isinstance(result_value, dict) else None,
                outputRef=result.get("outputRef"),
                error=redact(error_value) if isinstance(error_value, str) else str(error_value or "")[:10_000],
                stats={"returncode": result["returncode"]})
        except Exception as error:
            # Never leave a task in RUNNING when the worker itself crashes.
            # The control plane needs a terminal state so callers can retry
            # instead of waiting for the dashboard timeout.
            tasks[request.taskId] = TaskResponse(
                taskId=request.taskId,
                status="FAILED",
                error=str(error)[:10_000],
                stats={"returncode": 1},
            )
    pool.submit(run); return tasks[request.taskId]

@app.get("/v1/tasks/{task_id}", response_model=TaskResponse)
def status(task_id: str):
    if task_id not in tasks: raise HTTPException(404, "task not found")
    return tasks[task_id]

@app.post("/v1/tasks/{task_id}/cancel", response_model=TaskResponse)
def cancel(task_id: str):
    if task_id not in tasks: raise HTTPException(404, "task not found")
    if tasks[task_id].status == "RUNNING": executor.cancel(task_id); tasks[task_id] = TaskResponse(taskId=task_id, status="CANCELLED")
    return tasks[task_id]
