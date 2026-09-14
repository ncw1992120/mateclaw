# Aloudata 指标视图真实环境探测记录

> 本记录只保存接口状态和结构摘要，不保存认证值、响应原文或签名信息。

## 环境

- 产品层：`https://demo.can.aloudata.com:443`
- 语义层：`http://semantic.demo.can.aloudata.com:80`
- 认证 Header：`tenant-id`、`auth-type`、`auth-value`（值不落盘）
- 探测时间：2026-09-12（Asia/Shanghai）

## 结果

### ALO-X01：目录与详情

- `GET /anymetrics/api/v1/analysisview/treeList`
  - HTTP：`200`
  - Aloudata `code`：`200`
  - `success`：`true`
  - 响应包含 `data.analysisViewRoots`
  - 探测到 16 个根节点、166 个指标视图条目
- `GET /anymetrics/api/v1/analysisview/queryByName?viewName=NewView_lily_1`
  - HTTP：`200`
  - Aloudata `code`：`200`
  - `success`：`true`
  - 返回视图详情，包含 `id`、`viewName`、`metrics`、`dimensions`、`filters`、`orders` 等字段

结论：ALO-X01 目录和详情接口已取得真实响应证据。

### ALO-X02：结果查询与筛选

- `GET /semantic/api/v1.1/analysisView/query` 使用目录中探测到的视图、`pageIndex=0`、`pageSize=5`、`queryResultType=DATA` 调用。
- HTTP：`200`，但 Aloudata 返回 `code=SM_02_0038`、`success=false`。
- 对目录中前 30 个视图重复探测，均返回相同的视图访问拒绝结果。

结论：当前认证上下文可以读取目录和详情，但没有可用于结果查询的已授权指标视图；ALO-X02 记录为 `BLOCKED`，不能据此宣称结果查询或远端筛选下推通过。

## 外部测试入口

已新增 `AloudataAnalysisViewExternalIT`。该测试仅在显式执行
`-Dtest=AloudataAnalysisViewExternalIT` 时运行；缺少任一 `ALOU_DATA_*` 配置会以失败结束（不会 `skip`）。当前工作区未提供这些环境变量，因此已验证其 fail-fast 行为；未将失败计入本地单元测试通过数。

## 后续解除条件

提供至少一个允许当前认证上下文执行 `analysisView/query` 的指标视图后，重新执行：

1. 无筛选的 5 行结果查询；
2. 一个维度或时间筛选查询；
3. 记录响应中的查询结果和请求/响应证据，确认筛选在语义层生效。

## 当前认证上下文复验（2026-09-14）

- 使用既有临时认证上下文只读探测 `Demo_view`：产品层目录和详情均返回 HTTP `200`、`code=200`、`success=true`；详情包含 5 个指标和 3 个维度，可作为 Adapter 字段映射样本。
- 语义层 `analysisView/query` 使用 `pageIndex=0`、`pageSize=5`、`queryResultType=DATA` 返回 HTTP `200`，但业务码仍为 `SM_02_0038`、`success=false`。
- 对目录前 20 个视图做同样的 5 行基线探测，均返回 `SM_02_0038`。因此本次未执行伪造筛选通过，ALO-X02 继续保持 `EXTERNAL-BLOCKED`；目录/详情可读不等于结果查询已授权。

## 本地模拟复验（2026-09-13）

- `make dashboard-prerequisites-simulation`、`./scripts/verify-dashboard-external-prerequisites.sh --local`：通过。
- `AloudataAnalysisViewExternalIT` 通过 Docker Maven 入口对本地 WireMock 执行：目录、详情、5 行基线和 `region=east` 筛选均通过。
- 该结果只证明 Adapter 契约和模拟接口闭环；真实环境仍维持 ALO-X02 `BLOCKED`，不能替代 `SM_02_0038` 授权问题的解除。

## 当前认证上下文复验（2026-09-15）

- 使用已配置的 `semantic.demo.can.aloudata.com`、租户 `tn_27436`、认证类型 `UID` 和只读认证值，按 Adapter 使用的 `tenant-id`、`auth-type`、`auth-value` 请求头再次调用 `Demo_view` 的 5 行查询。
- 返回 HTTP `200`、`code=SM_02_0038`、`success=false`，消息仍为视图 `pinganstock:Demo_view` 无权限；未执行或伪造筛选通过。
- 结论未变：目录/详情可读，结果查询及远端筛选下推仍为 `ALO-X02 EXTERNAL-BLOCKED`，等待 Aloudata 提供已授权可查询视图。
