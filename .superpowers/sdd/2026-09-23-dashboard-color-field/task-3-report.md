# Task 3 实施报告：新旧属性面板迁移共享颜色字段

## 实施结果

- 新版 `PropertyPanel` 和旧版 `AttributePanel` 的自定义背景色、边框色均已替换为 `InsightColorField`。
- 背景色继续传入 `CARD_BG_PRESETS`；边框未传入背景色推荐数组。
- 新版颜色更新继续经 `v-model` 写入本地组件，并在 `change` 时调用既有 `emitChange()`。旧版继续通过 `v-model` 原地更新 `activeCard.visualStyle`，不新增面板保存事件；模式选项仍由各自父面板控制。
- 未修改原型或任何对话框。

## TDD 记录

RED：先添加两个面板测试，再按要求运行：

```text
cd mateclaw-dataagent-ui && pnpm exec vitest run src/views/insight/components/__tests__/PropertyPanel.spec.ts src/views/insight/components/card-attribute/__tests__/AttributePanel.color.spec.ts
```

迁移前两条共享字段断言均按预期失败：两面板各自期望找到 2 个共享字段，实际均为 0；其余 20 条既有/新增断言通过。首次旧面板 RED 捕获到夹具中 `activeCard` 形态不匹配，修正为 computed 卡片后再次运行，得到上述有效 RED 结果。

GREEN：实现后再次运行同一条定向命令，最终结果：

```text
Test Files  2 passed (2)
Tests       23 passed (23)
Exit code   0
```

覆盖新版和旧版的颜色更新、背景推荐色传递、边框不使用背景推荐色，以及透明背景/主题边框模式行为。

## 完整 UI 包测试

按要求仅运行一次：`cd mateclaw-dataagent-ui && pnpm exec vitest run`

```text
Test Files  78 passed (78)
Tests       431 passed (431)
Errors      1 error
Exit code   1
```

Vitest 报告 1 个未处理异常，来自未修改的 `src/views/insight/components/card-attribute/__tests__/DatasetCard.spec.ts`：`DatasetCard.vue:33` 点击路径中 `$setup.openQueryConfig is not a function`。同次运行还输出不少 Element Plus 组件/指令未注册及原生 input 的 `size="small"` 警告。当前证据只确认错误发生位置，不据此断言它在基线中必然存在。

## 文件

- 修改：`mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue`
- 修改：`mateclaw-dataagent-ui/src/views/insight/components/card-attribute/AttributePanel.vue`
- 修改：`mateclaw-dataagent-ui/src/views/insight/components/__tests__/PropertyPanel.spec.ts`
- 新增：`mateclaw-dataagent-ui/src/views/insight/components/card-attribute/__tests__/AttributePanel.color.spec.ts`
- 新增：本报告

## 自审与关注项

- 两面板各自的父级模式及更新语义保持原样；颜色字段事件只更新颜色值，没有将设置对象合并进共享字段。
- diff 限于上述面板、对应测试和本报告；未改原型、对话框或 Task 3 brief 之外的产品代码。
- `git diff --check` 在提交前检查；未运行浏览器验收或生产构建，本任务要求的验证为定向 Vitest 与 UI 包 Vitest。
- UI 包套件目前不能标记为全绿；唯一报告的未处理异常需后续单独查明其原因/基线状态。
