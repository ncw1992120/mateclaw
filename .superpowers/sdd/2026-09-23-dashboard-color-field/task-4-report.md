# Task 4 实施报告：组合卡片原型边框颜色字段迁移

## 状态

实现完成并已本地提交。只修改了组合卡片原型、对应聚焦测试和本报告；未推送。

## TDD 证据

### RED

新增 `CardContainerPrototype.color.spec.ts` 后，在未修改原型代码时执行：

```text
cd mateclaw-dataagent-ui
pnpm exec vitest run src/views/insight/__tests__/CardContainerPrototype.color.spec.ts
```

结果：2 个测试中 1 个失败、1 个通过。边框颜色行为测试在“共享字段输入不存在”处失败（`expected false to be true`，对应 `[aria-label="边框颜色"]` 不存在）；路由测试通过。失败原因正是原型尚未迁移颜色控件。

### GREEN

将原型中的边框颜色 `el-input` 换为 `InsightColorField` 并绑定同一 `container.style.border.color` 后，重新运行聚焦测试：

```text
✓ src/views/insight/__tests__/CardContainerPrototype.color.spec.ts (2 tests)
Test Files  1 passed (1)
Tests       2 passed (2)
```

行为测试挂载真实原型和共享颜色字段，将 `#E5EAF2` 更新为 `#123abc`，并验证 `.card-container` 的实际 `borderColor` 从 `rgb(229, 234, 242)` 更新为 `rgb(18, 58, 188)`；同时验证边框开关仍为启用、宽度仍为 `1`。第二个测试确认 `/insight/card-container-prototype` 仍解析到 `insight-card-container-prototype`，且该开发路由仍按 `import.meta.env.DEV` 开关注册。

## 完整 UI 套件

按要求仅运行一次：

```text
cd mateclaw-dataagent-ui
pnpm exec vitest run --reporter=dot
```

Vitest 汇总原文：

```text
Vitest caught 1 unhandled error during the test run.
TypeError: $setup.openQueryConfig is not a function
  at src/views/insight/components/card-attribute/DatasetCard.vue:33:47
This error originated in "src/views/insight/components/card-attribute/__tests__/DatasetCard.spec.ts".

Test Files  79 passed (79)
Tests       434 passed (434)
Errors      1 error
Duration    17.15s
```

命令以非零状态结束，完整套件结论为 FAIL（存在 1 个 unhandled error），不能报告为 PASS。错误是在 `DatasetCard.spec.ts` 的点击路径调用 `$setup.openQueryConfig` 时产生，与本任务的原型组件和测试无关。输出中另有不少 Element Plus stub 警告；此次未修改这些无关测试/组件。

## 变更文件

- `mateclaw-dataagent-ui/src/views/insight/CardContainerPrototype.vue`：导入共享 `InsightColorField`，替换边框颜色输入；继续双向绑定原有颜色状态。
- `mateclaw-dataagent-ui/src/views/insight/__tests__/CardContainerPrototype.color.spec.ts`：新增实际原型更新行为、边框设置保持和开发路由断言。
- `.superpowers/sdd/2026-09-23-dashboard-color-field/task-4-report.md`：本报告。

## 自查

- `git diff --check`：通过。
- 路由定义文件未修改；测试确认开发路由仍存在且指向原有命名路由。
- 只替换 `container.style.border.color` 控件；`container.style.border.enabled`、`container.style.border.width` 和容器状态结构未改。
- 本地提交只包含上述三个任务文件；未推送。

## 顾虑

- UI 全套件当前被 `DatasetCard.spec.ts` 的 1 个 unhandled error 标记为 FAIL。该错误与本次原型范围无关，需单独修复后再获得全套件绿色结果。
- 聚焦测试挂载大型原型时，未使用的 Element Plus 控件会产生组件解析/stub 属性警告；聚焦行为断言通过，警告未影响测试结果。
