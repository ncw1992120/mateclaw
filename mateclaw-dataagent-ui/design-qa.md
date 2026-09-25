# Aloudata 指标/维度选择器设计验收

final result: blocked

## Review scope

- 对照项目原型 `prototypes/aloudata-picker/src/App.jsx`、`src/styles.css` 和 `evidence/prototype-render.png`。
- 修正仪表盘实际入口 `insight/components/card-attribute/dataset/AloudataDialog.vue`，覆盖指标与维度两种选择。
- 验证展开分类、类目内联字段、选中/不可用状态、搜索、详情悬浮和多选行为。

## Evidence

- 用户给出的实际页面截图：`/var/folders/64/h6k4c1q17f5725r8cgxccdmr0000gn/T/codex-clipboard-943a7938-f069-453c-ad54-1e48e7a3b5af.png`。
- 原型目录参考：`/Users/srant/IdeaProjects/codex/mateclaw-1/prototypes/aloudata-picker/evidence/prototype-render.png`。
- 前端组件交互单测：`src/views/insight/components/__tests__/AloudataDialog.spec.ts`（12 项通过）。
- 隔离浏览器预览启动在 `http://127.0.0.1:5176/`，目标仪表盘路由被重定向到 `/login`；当前未在 5176 登录，无法完成真实页面截图对照。

## Findings

- 原入口旧弹窗将目录和指标/维度结果拆成上下/左右独立区域；现在分类行可展开，字段以内联目录项呈现，目录整体滚动，分类数量及分页“加载更多”保留。
- 搜索继续调用实时指标/维度分页接口；详情继续调用实时详情接口，不切换到本地分组缓存或 mock 数据。
- 已选项可取消/删除，兼容性禁用与自动隐藏行为保持。
- P0/P1/P2：未发现单测覆盖范围内的阻断项。像素级浏览器对照尚未执行。

## Validation

- `npm run build`：通过。
- `vitest run src/views/insight/components/__tests__/AloudataDialog.spec.ts`：12 项通过。
- 前端全量测试：88 个文件，86 个通过；4 项失败集中在现有 `QueryConfigDialog.spec.ts` 与 `PythonQueryConfigDialog.spec.ts`，与本次选择器路径无关。
- 浏览器实际页面验收：BLOCKED（隔离端口需要登录；未复制或转移 5174 的用户会话）。
