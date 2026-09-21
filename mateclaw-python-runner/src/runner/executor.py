import os
import signal
import subprocess
import tempfile
import sys
import json
import urllib.request
import resource
from dataclasses import dataclass
from threading import Lock

@dataclass
class RunningTask:
    process: subprocess.Popen
    directory: str

class TaskExecutor:
    def __init__(self): self._tasks: dict[str, RunningTask] = {}; self._lock = Lock()
    def start(self, task_id: str, script: str, env: dict[str, str], timeout: int, max_stdout: int,
              max_result: int = 1_000_000, result_upload_endpoint: str | None = None,
              result_upload_token: str | None = None, max_memory_mb: int = 1024,
              max_file_bytes: int = 100 * 1024 * 1024,
              parameters: dict | None = None):
        directory = tempfile.mkdtemp(prefix="mateclaw-task-")
        user_path = os.path.join(directory, "user_script.py")
        path = os.path.join(directory, "script.py")
        with open(user_path, "w", encoding="utf-8") as f: f.write(script)
        with open(path, "w", encoding="utf-8") as f:
            f.write("import json\nimport os\nfrom mateclaw.datasets import DatasetClient\n")
            f.write("datasets = DatasetClient(os.environ['MATECLAW_DATASET_ENDPOINT'], os.environ['MATECLAW_READ_TOKEN'], json.loads(os.environ.get('MATECLAW_TASK_PARAMETERS', '{}'))) if os.environ.get('MATECLAW_DATASET_ENDPOINT') and os.environ.get('MATECLAW_READ_TOKEN') else None\n")
            f.write("exec(compile(open('user_script.py', encoding='utf-8').read(), 'user_script.py', 'exec'))\n")
            # 统一输出契约：result 标准化为 envelope；非法输出显式失败，不再 default=str 掩盖
            f.write("from mateclaw.results import normalize_result, ResultContractError\n")
            f.write("try:\n")
            f.write("    envelope = normalize_result(globals().get('result'))\n")
            f.write("    with open('__mateclaw_result.json', 'w', encoding='utf-8') as output_file:\n")
            f.write("        json.dump(envelope, output_file, ensure_ascii=False, allow_nan=False)\n")
            f.write("except ResultContractError as exc:\n")
            f.write("    with open('__mateclaw_contract_error.json', 'w', encoding='utf-8') as error_file:\n")
            f.write("        json.dump(exc.as_dict(), error_file, ensure_ascii=False)\n")
            f.write("    raise\n")
        # Do not copy arbitrary Runner/container secrets into user code. The
        # child only needs a minimal interpreter environment plus the task-scoped
        # SDK variables supplied by the control plane.
        child_env = {key: os.environ[key] for key in ("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR") if key in os.environ}
        child_env.update(env)
        child_env["MATECLAW_TASK_PARAMETERS"] = json.dumps(parameters or {}, ensure_ascii=False)
        source_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        child_env["PYTHONPATH"] = source_root + os.pathsep + child_env.get("PYTHONPATH", "")
        process = subprocess.Popen([sys.executable, path], cwd=directory, env=child_env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                   start_new_session=True,
                                   preexec_fn=lambda: self._apply_limits(max_memory_mb, max_file_bytes))
        with self._lock: self._tasks[task_id] = RunningTask(process, directory)
        try:
            out, err = process.communicate(timeout=timeout)
            result_json = None
            output_ref = None
            result_path = os.path.join(directory, "__mateclaw_result.json")
            contract_error_path = os.path.join(directory, "__mateclaw_contract_error.json")
            contract_error = None
            if os.path.exists(contract_error_path):
                with open(contract_error_path, "r", encoding="utf-8") as error_file:
                    contract_error = json.loads(error_file.read())
            if os.path.exists(result_path):
                result_size = os.path.getsize(result_path)
                encoded_result = None
                if result_size <= 100 * 1024 * 1024:
                    with open(result_path, "rb") as result_file:
                        encoded_result = result_file.read()
                envelope = json.loads(encoded_result.decode("utf-8")) if encoded_result is not None else None
                if envelope is not None and len(encoded_result) <= max_result:
                    result_json = envelope
                elif envelope is not None and result_upload_endpoint and result_upload_token:
                    # 只有 table 结果走 Parquet 引用；scalar/message 超限即 RESULT_LIMIT
                    if envelope.get("kind") == "table":
                        parquet_path = os.path.join(directory, "__mateclaw_result.parquet")
                        if self._write_parquet(envelope, parquet_path):
                            output_ref = self._upload_result(parquet_path, result_upload_endpoint, result_upload_token)
                            if output_ref is not None:
                                # 引用结果必须携带重建 envelope 所需元数据
                                output_ref = {**output_ref,
                                              "schemaVersion": envelope.get("schemaVersion"),
                                              "kind": envelope.get("kind"),
                                              "columns": envelope["data"]["columns"],
                                              "rowCount": envelope["meta"]["rowCount"]}
                        else:
                            output_ref = None
            result_too_large = os.path.exists(result_path) and result_json is None and output_ref is None
            # 状态优先级：资源类错误（stdout 超限）优先于输出契约错误，避免掩盖真正的失败原因
            if len(out.encode()) > max_stdout:
                output_status = "OUTPUT_LIMIT"
            elif result_too_large:
                output_status = "RESULT_LIMIT"
            elif output_ref is not None:
                output_status = "RESULT_REF"
            else:
                output_status = "SUCCEEDED" if process.returncode == 0 else "FAILED"
            error_value = err[:10_000]
            if output_status in {"SUCCEEDED", "FAILED"} and contract_error is not None:
                # 输出不合法：结构化契约错误 + 独立状态，绝不伪装成 SUCCEEDED
                # （资源类错误如 OUTPUT_LIMIT/RESULT_LIMIT 优先级更高，保留原状态）
                output_status = "OUTPUT_CONTRACT_ERROR"
                result_json = None
                error_value = contract_error
            return {"status": output_status, "output": out[:max_stdout], "result": result_json, "outputRef": output_ref, "error": error_value, "returncode": process.returncode}
        except subprocess.TimeoutExpired:
            self.cancel(task_id)
            return {"status": "TIMEOUT", "output": "", "error": "task timed out", "returncode": -signal.SIGKILL}
        finally:
            with self._lock: self._tasks.pop(task_id, None)
            try: os.unlink(path); os.unlink(user_path); os.rmdir(directory)
            except OSError: pass
    def _upload_result(self, path: str, endpoint: str, token: str) -> dict | None:
        try:
            with open(path, "rb") as content:
                request = urllib.request.Request(endpoint, data=content.read(), method="POST",
                    headers={"Content-Type": "application/vnd.apache.parquet", "Authorization": f"Bearer {token}"})
            with urllib.request.urlopen(request, timeout=60) as response:
                body = json.loads(response.read())
            if body.get("code") != 200:
                return None
            return body.get("data")
        except Exception:
            return None

    def _write_parquet(self, envelope: dict, path: str) -> bool:
        try:
            import pyarrow as pa
            import pyarrow.parquet as pq
            rows = envelope.get("data", {}).get("rows", [])
            pq.write_table(pa.Table.from_pylist(rows), path, compression="snappy")
            return True
        except Exception:
            return False
    def cancel(self, task_id: str) -> bool:
        with self._lock: task = self._tasks.get(task_id)
        if not task or task.process.poll() is not None: return False
        try: os.killpg(task.process.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        return True

    @staticmethod
    def _apply_limits(max_memory_mb: int, max_file_bytes: int):
        """Apply limits in the child before user code starts; unavailable limits are fail-safe no-ops."""
        if hasattr(resource, "RLIMIT_AS"):
            try:
                memory = max(128, int(max_memory_mb)) * 1024 * 1024
                resource.setrlimit(resource.RLIMIT_AS, (memory, memory))
            except (ValueError, OSError):
                pass
        if hasattr(resource, "RLIMIT_FSIZE"):
            try:
                size = max(1 * 1024 * 1024, int(max_file_bytes))
                resource.setrlimit(resource.RLIMIT_FSIZE, (size, size))
            except (ValueError, OSError):
                pass
