# 本地文件存储与 MinIO 迁移

DataAgent 不再依赖 MinIO/S3。文件数据集与 Python 临时/大结果对象都写入 `mateclaw.storage.root` 指定的本地文件系统目录。Docker Compose 使用 `MATECLAW_STORAGE_ROOT`（默认为 `./data/object-storage`）作为宿主机持久目录，并挂载到容器 `/data/mateclaw-storage`。多实例部署必须挂载同一共享文件系统目录；该目录应仅允许受信任的 DataAgent 进程写入。

## MinIO 数据迁移

代码切换不会自动连接、删除或迁移任何旧 MinIO bucket/volume。迁移前先停写，保留原 bucket 与 volume 作为回滚副本；确认应用读取迁移后的数据前，不要删除旧数据。

1. 配置并挂载持久存储目录，确认 DataAgent 的 `MATECLAW_STORAGE_ROOT` 指向挂载点。
2. 使用管理员配置的 `mc` alias，将旧 bucket 的对象递归复制到新根目录。目标相对路径必须与源 object key 完全一致，例如源 key `datasets/42/<uuid>` 必须落在 `<storage-root>/datasets/42/<uuid>`；不要额外加 bucket 名称目录。先用少量对象演练并核对完整路径映射，再迁移全量对象。
3. 迁移前后分别记录对象总数与总字节数；按 object key 对照清单，逐个核对大小与 SHA-256。示例核对方式：从源端按 key `mc cat` 计算 SHA-256，并对目标端同 key 的文件计算 SHA-256。所有 key、数量、大小和摘要必须一致；遇到重复 key、权限失败或不一致时停止切换并保留两端数据。
4. 在隔离环境中通过应用读取代表性文件数据集，并覆盖 CSV、JSON、XLSX、Parquet 及 Python 大结果；确认 workspace 隔离、文件内容和结果读取正常后，再安排切换。
5. 切换后继续保留原 bucket/volume 与备份一个回滚窗口。只有业务方确认无回滚需求、且备份已验证后，才由运维人员手动删除旧对象存储数据。此仓库脚本不会执行该删除。

临时 Python ObjectRef 使用任务级 key，并按 TTL 过期；若只需保留当前有效任务，迁移时可以按运维约定筛选，但必须把筛选规则和未迁移对象清单留档。需要完整回滚能力时则迁移全量 key，不要只迁移 `datasets/` 前缀。

## 运行约束

- 单机部署可将存储根配置为专用持久目录；容器重建和应用升级不能删除该目录。
- 多实例部署须共享同一文件系统，并保证目录权限、原子文件替换和备份策略满足要求。不要把容器临时目录用于生产数据。
- `dev-support/local-simulation/files/` 中的订单 fixture 是独立测试样本，不是从旧 MinIO 自动恢复的数据。
