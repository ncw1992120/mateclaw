#!/usr/bin/env python3
"""本地 E2E 用的最小 S3/MinIO 兼容对象存储。

只覆盖 DataAgent ObjectRef 所需的 bucket HEAD/PUT、对象 PUT/GET/DELETE。
不实现认证；仅绑定本机地址，禁止用于生产。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import pathlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[s3-mock] " + fmt % args, flush=True)

    def _path(self) -> tuple[str, str | None]:
        parts = [unquote(part) for part in urlparse(self.path).path.split("/") if part]
        if not parts:
            return "", None
        return parts[0], "/".join(parts[1:]) or None

    def _send(self, status: int, body: bytes = b"", headers: dict[str, str] | None = None) -> None:
        self.send_response(status)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        if body:
            self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()
        if body:
            self.wfile.write(body)
            self.wfile.flush()

    def _read_body(self) -> bytes:
        if (self.headers.get("Transfer-Encoding") or "").lower() == "chunked":
            chunks: list[bytes] = []
            while True:
                size = int(self.rfile.readline().strip().split(b";", 1)[0] or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    return b"".join(chunks)
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length)

    def _file(self, bucket: str, object_id: str | None) -> pathlib.Path:
        root = pathlib.Path(os.environ["S3_MOCK_ROOT"]) / bucket
        if object_id is None:
            return root
        target = root / object_id
        target.resolve().relative_to(root.resolve())
        return target

    def do_HEAD(self):
        bucket, object_id = self._path()
        target = self._file(bucket, object_id)
        if target.is_file():
            self._send(200)
        elif target.is_dir() and object_id is None:
            self._send(200)
        else:
            self._send(404)

    def do_PUT(self):
        bucket, object_id = self._path()
        target = self._file(bucket, object_id)
        if object_id is None:
            target.mkdir(parents=True, exist_ok=True)
            self._send(200)
            return
        target.parent.mkdir(parents=True, exist_ok=True)
        body = self._read_body()
        target.write_bytes(body)
        self._send(200, headers={"ETag": '"' + hashlib.md5(body).hexdigest() + '"'})

    def do_GET(self):
        bucket, object_id = self._path()
        target = self._file(bucket, object_id)
        if not target.is_file():
            self._send(404)
            return
        self._send(200, target.read_bytes())

    def do_DELETE(self):
        bucket, object_id = self._path()
        target = self._file(bucket, object_id)
        if target.is_file():
            target.unlink()
        self._send(204)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="本地 E2E S3 ObjectRef mock")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=19000)
    parser.add_argument("--root", default="/tmp/mateclaw-s3-object-ref")
    args = parser.parse_args()
    pathlib.Path(args.root).mkdir(parents=True, exist_ok=True)
    os.environ["S3_MOCK_ROOT"] = str(pathlib.Path(args.root).resolve())
    print(f"S3 ObjectRef mock: http://{args.host}:{args.port} root={args.root}", flush=True)
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
