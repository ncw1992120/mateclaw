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
            f.write("if 'result' in globals():\n")
            f.write("    value = result\n")
            f.write("    if hasattr(value, 'to_dicts'): value = value.to_dicts()\n")
            f.write("    elif hasattr(value, 'to_dict'): value = value.to_dict(orient='records')\n")
            f.write("    elif isinstance(value, dict): value = [value]\n")
            f.write("    elif not isinstance(value, list): value = [{'value': value}]\n")
            f.write("    with open('__mateclaw_result.json', 'w', encoding='utf-8') as output_file: json.dump(value, output_file, ensure_ascii=False, default=str)\n")
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
            if os.path.exists(result_path):
                result_size = os.path.getsize(result_path)
                encoded_result = None
                if result_size <= 100 * 1024 * 1024:
                    with open(result_path, "rb") as result_file:
                        encoded_result = result_file.read()
                if encoded_result is not None and len(encoded_result) <= max_result:
                    result_json = encoded_result.decode("utf-8")
                elif encoded_result is not None and result_upload_endpoint and result_upload_token:
                    parquet_path = os.path.join(directory, "__mateclaw_result.parquet")
                    if self._write_parquet(encoded_result, parquet_path):
                        output_ref = self._upload_result(parquet_path, result_upload_endpoint, result_upload_token)
                    else:
                        output_ref = None
            result_too_large = os.path.exists(result_path) and result_json is None and output_ref is None
            output_status = "OUTPUT_LIMIT" if len(out.encode()) > max_stdout else ("RESULT_LIMIT" if result_too_large else ("RESULT_REF" if output_ref else ("SUCCEEDED" if process.returncode == 0 else "FAILED")))
            return {"status": output_status, "output": out[:max_stdout], "result": result_json, "outputRef": output_ref, "error": err[:10_000], "returncode": process.returncode}
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

    def _write_parquet(self, encoded_result: bytes, path: str) -> bool:
        try:
            import pyarrow as pa
            import pyarrow.parquet as pq
            value = json.loads(encoded_result.decode("utf-8"))
            if not isinstance(value, list):
                value = [{"value": value}]
            pq.write_table(pa.Table.from_pylist(value), path, compression="snappy")
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
