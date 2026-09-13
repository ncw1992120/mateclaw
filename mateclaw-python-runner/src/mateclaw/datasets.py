import json
import urllib.request
from dataclasses import replace
from typing import Any, Iterable

from .filters import Filter
from .params import Params
from .types import DatasetColumn, DatasetInput

class DatasetClient:
    def __init__(self, endpoint: str, read_token: str, parameters: dict[str, Any] | None = None, timeout: float = 60):
        if not endpoint.startswith("http://") and not endpoint.startswith("https://"):
            raise ValueError("dataset endpoint must be HTTP(S)")
        if not read_token: raise ValueError("read token is required")
        self._endpoint, self._token, self._timeout = endpoint, read_token, timeout
        self.params = Params(parameters or {})

    def read(self, input_name: str, columns: Iterable[str] | None = None,
             filters: Iterable[Filter | dict[str, Any]] | None = None) -> DatasetInput:
        if not input_name or input_name.startswith("_"): raise ValueError("invalid input name")
        normalized = []
        for value in filters or ():
            if isinstance(value, Filter):
                normalized.append(value.as_dict())
            elif isinstance(value, dict):
                normalized.append(Filter(value.get("field"), value.get("operator"), value.get("value")).as_dict())
            else:
                raise ValueError("filter must be a Filter or mapping")
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
