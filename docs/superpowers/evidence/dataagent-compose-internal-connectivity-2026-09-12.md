# DataAgent Compose 内部连通性证据（2026-09-12）

## 验证范围

在当前工作树构建 `mateclaw-dataagent` 镜像，并用 Docker Compose 启动 MySQL、MinIO、Python Runner 和 DataAgent，验证 DataAgent 通过 Compose 内部网络访问依赖服务。

## 执行结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| DataAgent 镜像构建 | PASS | `mateclaw-dataagent:test` 构建完成；Compose 同时生成 `mateclaw-1-mateclaw-dataagent` |
| MySQL 健康 | PASS | Compose `healthy`；DataAgent Flyway 成功应用到 schema v217 |
| MinIO 健康 | PASS | Compose `healthy`；容器内 `http://minio:9000/minio/health/ready` 返回 HTTP 200 |
| Python Runner 健康 | PASS | Compose `healthy`；容器内 `http://python-runner:8080/health` 返回 `{"status":"UP"}` |
| DataAgent 启动 | PASS | `mateclaw-dataagent` 容器状态为 `running` |
| Runner 内网隔离 | PASS | Runner 仍只加入 `runner_internal`，宿主机无端口映射 |
| DataAgent 全量测试 | PASS | `mvn -f mateclaw-dataagent/pom.xml test -q`：127 tests，0 failures，0 errors，0 skipped；Testcontainers 使用 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` |

## 约束与未完成项

- 宿主机 `18089` 已被现有本地 DataAgent 占用，因此本次验证使用临时 Compose override 清除正式端口映射；这不改变正式 `18089:18089` 配置。
- DataAgent 容器的宿主机 HTTP 端点未作为本次验收依据；本证据只证明 Compose 内部服务发现、启动和依赖连通性。
- 尚未用真实已登记数据源完成 JDBC+Aloudata 与 HTTP/API+文件两条完整双源预览链路。
- 验证结束后已移除本次创建的四个测试容器，保留 Docker volume；未停止或修改宿主机已有的本地 DataAgent 进程。
