import json
import urllib.error
import urllib.request
from typing import Any, Iterable

from .filters import Filter
from .params import Params
from .types import DatasetColumn, DatasetInput, PreparedInputExpiredError

# 单次读取的条件数上限（与 DataAgent 读取策略一致）
MAX_FILTERS = 50


class DatasetClient:
    def __init__(self, endpoint: str, read_token: str, parameters: dict[str, Any] | None = None,
                 timeout: float = 60, input_endpoint: str | None = None):
        if not endpoint.startswith("http://") and not endpoint.startswith("https://"):
            raise ValueError("dataset endpoint must be HTTP(S)")
        if not read_token: raise ValueError("read token is required")
        self._endpoint, self._token, self._timeout = endpoint, read_token, timeout
        self._input_endpoint = input_endpoint
        self.params = Params(parameters or {})

    def input(self, input_name: str) -> DatasetInput:
        """读取 prepared input：请求只携带 inputName（令牌在 Header）。

        返回的 DatasetInput 已经是查询计划处理后的输入（页面筛选/排序/分页已应用），
        不携带也不会重新拼接 filters/orders/limit/offset 等页面参数。
        """
        if self._input_endpoint is None:
            raise RuntimeError("prepared input endpoint is not configured for this task")
        if not input_name or input_name.startswith("_"):
            raise ValueError("invalid input name")
        payload = self._post_input({"inputName": input_name})
        data = self._unwrap(payload, context=input_name)
        schema = tuple(DatasetColumn(c["name"], c.get("title", c["name"]), c.get("dataType", "string"),
                                    c.get("nullable", True), c.get("semanticRole")) for c in data.get("schema", []))
        object_ref = data.get("objectRef")
        if object_ref is None:
            return DatasetInput(input_name, schema, tuple(data.get("rows") or ()),
                                row_count=data.get("rowCount"))
        # 大结果：rows=null + objectRef；SDK 用受控批次读取取得完整行集后才能 to_polars()
        batch_size = data.get("batchSize") or 10_000

        def batch_reader(cursor: int) -> dict[str, Any]:
            body = self._post_input({"inputName": input_name, "cursor": cursor, "batchSize": batch_size})
            return self._unwrap(body, context=input_name)

        return DatasetInput(input_name, schema, (), object_ref,
                            row_count=data.get("rowCount"),
                            _batch_reader=batch_reader)

    def _post_input(self, payload: dict[str, Any]):
        request = urllib.request.Request(self._input_endpoint, data=json.dumps(payload).encode(), method="POST",
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {self._token}"})
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                if response.status >= 400:
                    raise RuntimeError(f"prepared input read failed: HTTP {response.status}")
                return json.loads(response.read())
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                detail = json.loads(exc.read()).get("message", "")
            except Exception:
                pass
            if exc.code == 401 or exc.code == 403:
                raise PreparedInputExpiredError(
                    f"prepared input rejected (HTTP {exc.code}); task token may be expired: {detail}") from exc
            raise RuntimeError(f"prepared input read failed: HTTP {exc.code} {detail}") from exc

    @staticmethod
    def _unwrap(body: Any, context: str) -> dict[str, Any]:
        if isinstance(body, dict):
            code = body.get("code")
            if code is not None and str(code) not in {"0", "200"}:
                message = body.get("msg") or body.get("message") or code
                if "PREPARED_INPUT_EXPIRED" in str(message):
                    raise PreparedInputExpiredError(str(message))
                raise RuntimeError(f"prepared input '{context}' read failed: {message}")
            if body.get("success") is False:
                raise RuntimeError(f"prepared input '{context}' read failed: {body.get('msg') or 'request rejected'}")
            if isinstance(body.get("data"), dict):
                return body["data"]
        return body

    def read(self, input_name: str, columns: Iterable[str] | None = None,
             filters: Iterable[Filter | dict[str, Any]] | None = None) -> DatasetInput:
        if not input_name or input_name.startswith("_"): raise ValueError("invalid input name")
        normalized = []
        for value in filters or ():
            if isinstance(value, Filter):
                normalized.append(value.as_dict())
            elif isinstance(value, dict):
                normalized.append(Filter(value.get("field"), value.get("operator"), value.get("value"), value.get("role", "dimension")).as_dict())
            else:
                raise ValueError("filter must be a Filter or mapping")
        if len(normalized) > MAX_FILTERS:
            raise ValueError(f"too many filters: {len(normalized)} exceeds {MAX_FILTERS}")
        # 空 in/not_in 集合直接返回合法空结果：源端不会把空集合误解释为「不过滤」，
        # 典型场景是数据集 A 的结果筛空后不再访问数据集 B。
        for value in normalized:
            if value["operator"] in {"in", "not_in"} and isinstance(value["value"], (list, tuple)) and len(value["value"]) == 0:
                return DatasetInput(input_name, (), ())
        payload = {"inputName": input_name, "columns": list(columns or []),
                   "filters": normalized, "parameters": dict(self.params)}
        request = urllib.request.Request(self._endpoint, data=json.dumps(payload).encode(), method="POST",
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {self._token}"})
        with urllib.request.urlopen(request, timeout=self._timeout) as response:
            if response.status >= 400: raise RuntimeError(f"dataset read failed: HTTP {response.status}")
            body = json.loads(response.read())
        if isinstance(body, dict):
            code = body.get("code")
            if code is not None and str(code) not in {"0", "200"}:
                raise RuntimeError(f"dataset read failed: {body.get('msg') or body.get('message') or code}")
            if body.get("success") is False:
                raise RuntimeError(f"dataset read failed: {body.get('msg') or body.get('message') or 'request rejected'}")
        # DataAgent controllers return the shared R<T> envelope; keep direct
        # payload parsing for local adapters and older Runner test fixtures.
        payload = body.get("data") if isinstance(body, dict) and isinstance(body.get("data"), dict) else body
        descriptor = payload.get("descriptor", payload)
        schema = tuple(DatasetColumn(c["name"], c.get("title", c["name"]), c.get("dataType", "string"),
                                    c.get("nullable", True), c.get("semanticRole")) for c in descriptor.get("schema", []))
        rows = tuple(payload.get("rows", ()))
        return DatasetInput(input_name, schema, rows, payload.get("objectRef"))
