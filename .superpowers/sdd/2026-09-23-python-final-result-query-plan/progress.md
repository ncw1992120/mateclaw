# SDD ledger — plan: docs/superpowers/plans/2026-09-23-python-final-result-query-plan.md

Pre-flight: Task 1 produces `ResultSchema` and output-contract helpers consumed by Tasks 2, 4, and 5; current `component-output-spec.ts` and `script-result.ts` already provide validation primitives, so new code must extend them without changing the existing envelope contract.
Pre-flight: Task 2 produces component-level final-result query types consumed by Tasks 4 and 6; existing `datasetInputs[].queryConfig` remains input-stage configuration and must not be reused for result-stage execution.
Pre-flight: Task 3 produces the Java result-stage query service consumed by Task 4; it must operate only on the returned `ScriptResultEnvelope` and must not call an adapter.
Pre-flight: Task 4 changes the execution request consumed by the existing controller and frontend API; preserve backward-compatible constructors and request behavior for calls without the new context.
Pre-flight: Task 5 changes generated Python code consumed by the Runner; `datasets.input(inputName)` remains the only new generated read path, while `datasets.read` stays legacy-compatible.
Pre-flight: Task 6 consumes the result Schema and final-result query context from Tasks 1, 2, and 4; UI drafts must not persist until the outer Python editor confirms.

Ruling: Use the existing frontend `component-output-spec.ts` and Java `ScriptResultContractService` as the output-contract authority — why: the repository already validates component-specific table/scalar/message output; cost if wrong: introducing a second validator would allow UI and execution to disagree.

Task 1: complete (commit `13035973`, tests: `npm --prefix mateclaw-dataagent-ui run test -- --run` → 84 files / 487 tests passed; `./node_modules/.bin/vue-tsc --noEmit` → passed)

Task 1 note: npm emitted existing Vue Test Utils component-resolution/property warnings; no test failures or type errors.

Task 2: complete (commit `7a05216a`, tests: `npm --prefix mateclaw-dataagent-ui run test -- --run` → 85 files / 493 tests passed; `./node_modules/.bin/vue-tsc --noEmit` → passed)

Task 2 note: component-level `finalResultQueryConfig` is persisted separately from input `queryConfig`; runtime context normalization currently treats sorting/pagination as disabled until the user enables them in the result configuration.

Task 3: complete (commit pending, tests: Docker Java 21 `mvn -o -f mateclaw-dataagent/pom.xml -Dtest=FinalResultQueryServiceTest test` → 3 tests passed)

Task 3 note: `FinalResultQueryServiceImpl` only consumes `ValidatedEnvelope`; it applies parameter-bound filters, whitelist-checked sort, and bounded pagination in memory. Scalar/message outputs remain unchanged when no result query is requested and reject table-only operations.

Task 4: complete (commit pending, tests: Docker Java 21 `mvn -o -f mateclaw-dataagent/pom.xml -Dtest=FinalResultQueryServiceTest,ResultSetQueryServiceTest test` → 9 tests passed)

Task 4 note: result preview accepts optional `finalResultQueryConfig`; when present, the service loads and validates the complete Python envelope first, then applies final-result filters/sort/page. The old preview constructor/request behavior remains compatible when the field is absent.

Task 5: complete (commit pending, tests: `python-script-template.spec.ts` + `component-output-spec.spec.ts` → 24 tests passed; `vue-tsc --noEmit` → passed)

Task 5 note: generated Python system code now includes the component output kind, minimum field rules, and the Runner JSON-envelope example. User processing code remains a separate editable region.

Task 6: complete (commit pending, tests: `component-dataset-pipeline.spec.ts` + `python-script-template.spec.ts` → 15 tests passed; `vue-tsc --noEmit` → passed)

Task 6 note: after a successful Python envelope, the UI derives and persists component-level final-result field candidates from the real result Schema; the Python editor exposes those candidates as post-Python query configuration, while the result preview continues to operate on the final result rows.

Task 7: complete (commit pending; frontend full suite → 85 files / 493 tests passed; `vue-tsc --noEmit` → passed; Java focused suite → 10 tests passed; Java full suite → 288 tests, 285 passed, 3 blocked by missing Docker socket in Testcontainers)

Task 7 note: blocked full-suite tests are `ObjectRefServiceTest`, `ScriptDatasetReadObjectRefIntegrationTest`, and `S3DatasetFileStorageServiceTest`; no failures were reported. The feature path has compile and focused behavioral evidence, but Docker-dependent integration validation remains NOT_RUN in this environment.

Final review: self-review (no subagent tool available); `git diff --check` passed before the final display-field correction, and the correction is covered by the 10-test focused Java run.
