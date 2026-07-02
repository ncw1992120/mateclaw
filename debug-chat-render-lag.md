# Debug Session: chat-render-lag

Status: [OPEN]

## Symptom
多轮对话后，前端流式文本渲染明显卡顿，浏览器甚至弹出“等待或关闭”提示。

## Session ID
chat-render-lag

## First-principles model
浏览器主线程需要同时处理：SSE 数据读取、Vue 响应式更新、DOM diff/patch、Markdown/代码/图表渲染、滚动定位和用户输入。多轮对话后，如果任一环节的工作量随消息总量或文本长度超线性增长，就会产生长任务，阻塞主线程并触发浏览器无响应提示。

## Falsifiable Hypotheses
1. H1: 流式 token 更新触发整条 assistant 消息反复 Markdown/复杂组件重渲染，单次渲染耗时随当前消息长度增长。
2. H2: 每个 token/flush 都触发全列表重渲染或昂贵 computed/method，耗时随历史消息数量增长。
3. H3: 自动滚动、DOM 查询、代码高亮、图表/表格解析等副作用在流式阶段高频执行，造成 layout/reflow 长任务。
4. H4: FlushBuffer 批处理粒度仍过细，SSE 高频事件导致每秒 Vue patch 次数过多。
5. H5: 历史消息加载后没有虚拟化/冻结历史内容，导致多轮历史 Markdown 节点持续参与响应式 diff。

## Evidence Plan
- 记录 SSE 事件/flush 频率、单次 flush 文本量和消息数量。
- 记录 ChatView 更新周期耗时、消息数量、当前流式消息长度。
- 记录滚动逻辑触发频率与耗时。
- 记录 Markdown/消息内容组件渲染耗时（若存在独立组件）。

## Evidence Log
Instrumentation added. Waiting for reproduction.

## Changes
- Started debug server at http://127.0.0.1:7777.
- Added instrumentation only, no business optimization yet:
  - `useChatStore.ts`: FlushBuffer apply frequency, delta size, content length, message count.
  - `ChatView.vue`: Markdown parse/sanitize cost, watcher trigger frequency, scroll cost, ECharts block scan cost.
