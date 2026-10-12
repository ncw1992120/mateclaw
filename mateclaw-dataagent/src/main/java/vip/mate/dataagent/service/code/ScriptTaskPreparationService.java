package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.dataset.demo.StrategyReadoutDemoDatasetFixtures;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 在提交 Runner 前固化输入别名、权限上下文和短期读取令牌；有查询计划时物化 prepared input。 */
@Service
public class ScriptTaskPreparationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ScriptTaskPreparationService.class);
    /** prepared input 内联行数上限；超过则转 ObjectRef，由 SDK 受控批次读取。 */
    static final int INLINE_MAX_ROWS = 10_000;
    private static final int PAGE_SIZE = 10_000;
    private static final int MAX_PAGES = 10;
    private static final long PREPARED_INPUT_TTL_MS = 900_000L;
    private static final int MAX_CONCURRENT_INPUT_READS = 4;

    private final DatasetManageService datasets; private final ScriptTaskInputRegistry registry; private final ScriptDatasetReadTokenService tokens;
    private final List<DatasetSourceAdapter> adapters; private final ObjectRefService objectRefs;
    private final Semaphore inputReadPermits = new Semaphore(MAX_CONCURRENT_INPUT_READS);
    private final ThreadPoolExecutor inputReadExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_INPUT_READS, MAX_CONCURRENT_INPUT_READS, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_CONCURRENT_INPUT_READS), daemonThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
    private StrategyReadoutDemoDatasetFixtures strategyReadoutFixtures;
    public ScriptTaskPreparationService(DatasetManageService datasets, ScriptTaskInputRegistry registry,
                                        ScriptDatasetReadTokenService tokens, List<DatasetSourceAdapter> adapters,
                                        ObjectRefService objectRefs) {
        this.datasets = datasets; this.registry = registry; this.tokens = tokens; this.adapters = adapters; this.objectRefs = objectRefs;
    }

    @Autowired(required = false)
    public void setStrategyReadoutFixtures(StrategyReadoutDemoDatasetFixtures fixtures) {
        this.strategyReadoutFixtures = fixtures;
    }

    public PreparedTask prepare(String taskId, Long workspaceId, Long userId, Map<String,Long> inputDatasets, String script, Map<String,Object> parameters) {
        return prepare(taskId, workspaceId, userId, inputDatasets, script, parameters, Map.of());
    }

    /**
     * 带查询计划的准备：在授权后执行 Query Planner 产出的计划（Adapter 物化），
     * 把完整且按任务隔离的行集或 ObjectRef、schema、rowCount 与过期时间注册到 registry。
     * 内存 registry 不是持久化存储，重启/过期后读取将得到明确失败。
     */
    public PreparedTask prepare(String taskId, Long workspaceId, Long userId, Map<String,Long> inputDatasets,
                                String script, Map<String,Object> parameters, Map<String, DatasetQueryPlanDTO> plans) {
        long prepareStarted = System.nanoTime();
        if (inputDatasets==null||inputDatasets.isEmpty()||script==null||script.isBlank()) throw new IllegalArgumentException("task inputs and script are required");
        DatasetAccessContext context=new DatasetAccessContext(workspaceId,userId,taskId,new HashSet<>(inputDatasets.values()));
        Map<String,DatasetInputDescriptor> descriptors=new LinkedHashMap<>(); inputDatasets.forEach((alias,id)->descriptors.put(alias,datasets.getInputDescriptor(context,id,alias))); registry.register(taskId,descriptors);
        String token=tokens.issue(taskId,workspaceId,900);
        Map<String, ScriptTaskInputRegistry.PreparedInput> prepared = new LinkedHashMap<>();
        Map<String, CompletableFuture<ScriptTaskInputRegistry.PreparedInput>> pending = new LinkedHashMap<>();
        try {
            for (String alias : plans.keySet()) {
                if (!descriptors.containsKey(alias)) {
                    throw new IllegalArgumentException("plan references unknown input: " + alias);
                }
            }
            for (Map.Entry<String, DatasetQueryPlanDTO> entry : plans.entrySet()) {
                String alias = entry.getKey();
                DatasetQueryPlanDTO plan = entry.getValue();
                DatasetInputDescriptor descriptor = descriptors.get(alias);
                acquireInputReadPermit();
                try {
                    pending.put(alias, CompletableFuture.supplyAsync(() -> {
                        long inputStarted = System.nanoTime();
                        try {
                            ScriptTaskInputRegistry.PreparedInput input = materialize(context, alias, descriptor, plan);
                            LOGGER.info("[dashboard-perf] stage=input-materialize taskId={} input={} datasetId={} rows={} elapsedMs={}",
                                    taskId, alias, descriptor.datasetId(), input.rowCount(), elapsedMillis(inputStarted));
                            return input;
                        } finally {
                            inputReadPermits.release();
                        }
                    }, inputReadExecutor));
                } catch (RejectedExecutionException exception) {
                    inputReadPermits.release();
                    throw exception;
                }
            }
            CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).join();
            pending.forEach((alias, future) -> prepared.put(alias, future.join()));
        } catch (RuntimeException e) {
            registry.remove(taskId); // 失败不留半注册状态
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw e;
        }
        prepared.values().forEach(input -> registry.registerPreparedInput(taskId, input));
        LOGGER.info("[dashboard-perf] stage=prepare-complete taskId={} inputs={} elapsedMs={}",
                taskId, inputDatasets.size(), elapsedMillis(prepareStarted));
        return new PreparedTask(taskId,script,descriptors,parameters==null?Map.of():Map.copyOf(parameters),token,prepared);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "dashboard-input-read");
            thread.setDaemon(true);
            return thread;
        };
    }

    private void acquireInputReadPermit() {
        try {
            inputReadPermits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to prepare dashboard inputs", exception);
        }
    }

    @PreDestroy
    public void close() {
        inputReadExecutor.shutdown();
    }
    public record PreparedTask(String taskId,String script,Map<String,DatasetInputDescriptor> inputCatalog,Map<String,Object> parameters,String readToken,
                               Map<String, ScriptTaskInputRegistry.PreparedInput> preparedInputs) {
        public PreparedTask(String taskId, String script, Map<String, DatasetInputDescriptor> inputCatalog,
                            Map<String, Object> parameters, String readToken) {
            this(taskId, script, inputCatalog, parameters, readToken, Map.of());
        }
    }

    /** 按计划分页读取源端并累积为有界完整输入；超过内联上限时转 ObjectRef。 */
    private ScriptTaskInputRegistry.PreparedInput materialize(DatasetAccessContext context, String alias,
                                                              DatasetInputDescriptor descriptor, DatasetQueryPlanDTO plan) {
        if (plan.shortCircuitEmpty()) {
            // 显式空集合：不访问数据源，也不需要 Adapter
            return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), List.of(), null, 0,
                    System.currentTimeMillis() + PREPARED_INPUT_TTL_MS);
        }
        boolean useStrategyFixture = strategyReadoutFixtures != null
                && strategyReadoutFixtures.supports(descriptor.datasetId());
        DatasetSourceAdapter adapter = useStrategyFixture ? null : adapters.stream()
                .filter(a -> a.supports(descriptor.sourceType())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no adapter for plan input: " + alias));
        int readLimit = plan.readLimit() != null ? Math.min(plan.readLimit(), 100_000) : PAGE_SIZE;
        int offset = plan.pushdown().pagination() && plan.pagination() != null ? plan.pagination().offset() : 0;
        int effectiveLimit = plan.pushdown().pagination() && plan.pagination() != null
                ? plan.pagination().pageSize() : readLimit;
        List<Map<String, Object>> rows = new ArrayList<>();
        int pageOffset = offset;
        int pages = 0;
        while (pages++ < MAX_PAGES && rows.size() < effectiveLimit) {
            int size = Math.min(PAGE_SIZE, effectiveLimit - rows.size());
            DatasetReadRequest readRequest = new DatasetReadRequest(datasetIdOf(plan, context, alias), alias,
                    plan.columns(), filtersOf(plan), plan.orders().stream()
                            .map(o -> new DatasetSort(o.field(), o.direction())).toList(),
                    size, pageOffset, Map.of(), false);
            DatasetBatch batch = useStrategyFixture
                    ? strategyReadoutFixtures.read(context, readRequest)
                    : adapter.read(context, readRequest);
            List<Map<String, Object>> pageRows = batch.rows() == null ? List.of() : batch.rows();
            rows.addAll(pageRows);
            if (pageRows.size() < size || batch.last()) break;
            pageOffset += pageRows.size();
        }
        long expiresAt = System.currentTimeMillis() + PREPARED_INPUT_TTL_MS;
        if (rows.size() > INLINE_MAX_ROWS) {
            DatasetBatch spill = new DatasetBatch(rows, null, rows.size(), true, null);
            ObjectRef reference = DatasetBatchCodec.writeParquet(context, spill, objectRefs);
            return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), List.of(), reference, rows.size(), expiresAt);
        }
        return new ScriptTaskInputRegistry.PreparedInput(alias, descriptor.schema(), rows, null, rows.size(), expiresAt);
    }

    private long datasetIdOf(DatasetQueryPlanDTO plan, DatasetAccessContext context, String alias) {
        String id = plan.datasetId();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("plan datasetId is required for input: " + alias);
        try {
            long parsed = Long.parseLong(id);
            if (!context.canRead(parsed)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取计划数据集");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("plan datasetId is not numeric: " + id);
        }
    }

    private List<DatasetFilter> filtersOf(DatasetQueryPlanDTO plan) {
        return plan.filters().stream()
                .map(f -> new DatasetFilter(f.field(), "dimension", f.operator(), f.value()))
                .toList();
    }
}
