# Python Runner 网络隔离验证

日期：2026-09-12

## 验证范围

使用 `mateclaw-python-runner:test`，在临时 Docker `--internal` 网络、只读根目录和 `/tmp` tmpfs 条件下启动 Runner。

## 结果

- `/health` 返回 `{"status":"UP"}`。
- 任务脚本尝试访问 `https://example.com` 时 DNS 解析失败，任务终态为 `FAILED`，没有外部网络出口。
- Runner 镜像以 UID 10001 运行，镜像不包含 DuckDB；只读根目录检查通过。

该验证证明当前 Docker 网络边界可以阻断外部访问；DataAgent/MinIO 内部连通性仍需在带完整 Compose 凭据的环境中执行正式 E2E。
