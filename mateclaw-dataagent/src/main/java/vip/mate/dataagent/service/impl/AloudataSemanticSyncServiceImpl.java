package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.dto.AloudataDimensionSemanticDTO;
import vip.mate.dataagent.dto.AloudataMetricSemanticDTO;
import vip.mate.dataagent.dto.DimensionCategoryGroupDTO;
import vip.mate.dataagent.dto.MetricCategoryGroupDTO;
import vip.mate.dataagent.model.AloudataCategoryEntity;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.AloudataCategoryMapper;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataSemanticEsService;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.service.WikiEmbeddingService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aloudata 语义层同步服务实现
 * <p>
 * 从 Aloudata API 同步指标、维度、类目元数据到本地 MySQL + ES。
 * 关键优化：使用 metric_batch_detail 替代逐个 metric_detail（1 次 vs N 次 HTTP）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AloudataSemanticSyncServiceImpl implements AloudataSemanticSyncService {

    private final AloudataMetricMapper metricMapper;
    private final AloudataDimensionMapper dimensionMapper;
    private final AloudataMetricDimensionMapper metricDimensionMapper;
    private final AloudataCategoryMapper categoryMapper;
    private final DatasourceMapper datasourceMapper;
    private final AloudataApiClient apiClient;
    private final AloudataConfigHelper configHelper;
    private final AloudataEndpointService endpointService;
    private final AloudataSemanticEsService esService;
    private final ModelConfigService modelConfigService;

    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    private static final String ENDPOINT_CATEGORY_LIST = "category_list";
    private static final String ENDPOINT_METRIC_LIST = "metric_list";
    private static final String ENDPOINT_METRIC_BATCH_DETAIL = "metric_batch_detail";
    private static final String ENDPOINT_METRIC_ALL_DIMENSIONS = "metric_all_dimensions";
    private static final String ENDPOINT_DIMENSION_LIST = "dimension_list";
    private static final String ENDPOINT_DIMENSION_DETAIL = "dimension_detail";

    @Override
    public SyncResult fullSync(Long datasourceId) {
        long startTime = System.currentTimeMillis();

        // 校验数据源
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            return new SyncResult(0, 0, 0, 0, 0, "failed", "数据源不存在: " + datasourceId);
        }
        if (!"aloudata".equalsIgnoreCase(entity.getSourceType())) {
            return new SyncResult(0, 0, 0, 0, 0, "failed", "数据源类型不是 aloudata");
        }

        AloudataConfigDTO config = configHelper.parseConfig(entity);

        try {
            // 计算新版本号
            int newVersion = getNextSyncVersion(datasourceId);

            // 1. 获取类目并持久化
            Map<String, String> categoryMap = fetchAndSaveCategories(datasourceId, config, newVersion);
            int categoryCount = categoryMap.size();
            log.info("[Aloudata同步] 类目数量: {}", categoryCount);

            // 2. 流式处理指标：分页获取 → 详情 → 写入MySQL → 关联维度
            int metricCount = streamSyncMetrics(datasourceId, config, categoryMap, newVersion);
            log.info("[Aloudata同步] 指标同步完成: {}", metricCount);

            // 3. 流式处理维度：分页获取 → 详情 → 写入MySQL
            int dimensionCount = streamSyncDimensions(datasourceId, config, categoryMap, newVersion);
            log.info("[Aloudata同步] 维度同步完成: {}", dimensionCount);

            // 4. 清理旧版本数据
            cleanOldVersionData(datasourceId, newVersion);

            // 5. 向量化 + ES 索引（分页加载，避免全量驻留内存）
            int metricDimensionCount = countMetricDimensions(datasourceId);
            embedAndIndexAll(datasourceId);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Aloudata同步] 完成，指标: {}, 维度: {}, 关联: {}, 耗时: {}ms",
                    metricCount, dimensionCount, metricDimensionCount, elapsed);

            return new SyncResult(metricCount, dimensionCount, metricDimensionCount,
                    categoryCount, elapsed, "completed", "同步成功");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[Aloudata同步] 失败: {}", e.getMessage(), e);
            return new SyncResult(0, 0, 0, 0, elapsed, "failed", "同步失败: " + e.getMessage());
        }
    }

    @Override
    public List<AloudataMetricSemanticDTO> listSyncedMetrics(Long datasourceId, int pageNumber, int pageSize) {
        LambdaQueryWrapper<AloudataMetricEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricEntity::getDatasourceId, datasourceId);
        wrapper.orderByDesc(AloudataMetricEntity::getUpdateTime);

        int offset = (pageNumber - 1) * pageSize;
        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);

        List<AloudataMetricEntity> entities = metricMapper.selectList(wrapper);
        return entities.stream().map(this::toMetricSemanticDTO).collect(Collectors.toList());
    }

    @Override
    public List<AloudataDimensionSemanticDTO> listSyncedDimensions(Long datasourceId, int pageNumber, int pageSize) {
        LambdaQueryWrapper<AloudataDimensionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataDimensionEntity::getDatasourceId, datasourceId);
        wrapper.orderByDesc(AloudataDimensionEntity::getUpdateTime);

        int offset = (pageNumber - 1) * pageSize;
        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);

        List<AloudataDimensionEntity> entities = dimensionMapper.selectList(wrapper);
        return entities.stream().map(this::toDimensionSemanticDTO).collect(Collectors.toList());
    }

    @Override
    public List<String> listMetricDimensions(Long datasourceId, String metricName) {
        LambdaQueryWrapper<AloudataMetricDimensionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId);
        wrapper.eq(AloudataMetricDimensionEntity::getMetricName, metricName);
        List<AloudataMetricDimensionEntity> relations = metricDimensionMapper.selectList(wrapper);
        return relations.stream().map(AloudataMetricDimensionEntity::getDimName).collect(Collectors.toList());
    }

    @Override
    public SyncResult getSyncStatus(Long datasourceId) {
        Long metricCount = metricMapper.selectCount(new LambdaQueryWrapper<AloudataMetricEntity>()
                .eq(AloudataMetricEntity::getDatasourceId, datasourceId));
        Long dimensionCount = dimensionMapper.selectCount(new LambdaQueryWrapper<AloudataDimensionEntity>()
                .eq(AloudataDimensionEntity::getDatasourceId, datasourceId));
        Long relCount = metricDimensionMapper.selectCount(new LambdaQueryWrapper<AloudataMetricDimensionEntity>()
                .eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId));

        String status = metricCount > 0 ? "completed" : "not_synced";
        return new SyncResult(metricCount.intValue(), dimensionCount.intValue(),
                relCount.intValue(), 0, 0, status,
                metricCount > 0 ? "已同步" : "未同步");
    }

    @Override
    public List<AloudataCategoryEntity> listSyncedCategories(Long datasourceId, String categoryType) {
        LambdaQueryWrapper<AloudataCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataCategoryEntity::getDatasourceId, datasourceId);
        if (categoryType != null && !categoryType.isBlank()) {
            wrapper.eq(AloudataCategoryEntity::getCategoryType, categoryType);
        }
        wrapper.orderByAsc(AloudataCategoryEntity::getCategoryType, AloudataCategoryEntity::getCategoryName);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public List<MetricCategoryGroupDTO> listMetricsGroupByCategory(Long datasourceId) {
        // 直接从指标表查询（实体表已有类目字段，无需额外查询类目表）
        List<AloudataMetricEntity> allMetrics = metricMapper.selectList(
                new LambdaQueryWrapper<AloudataMetricEntity>()
                        .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                        .orderByAsc(AloudataMetricEntity::getMetricCategoryId));

        // 按 metricCategoryId 分组，O(n) 复杂度
        Map<String, List<AloudataMetricEntity>> grouped = allMetrics.stream()
                .collect(Collectors.groupingBy(
                        m -> StringUtils.hasText(m.getMetricCategoryId()) ? m.getMetricCategoryId() : "",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<MetricCategoryGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<AloudataMetricEntity>> entry : grouped.entrySet()) {
            String categoryId = entry.getKey();
            List<AloudataMetricEntity> metricsInCategory = entry.getValue();

            MetricCategoryGroupDTO group = new MetricCategoryGroupDTO();
            if (categoryId.isEmpty()) {
                group.setCategoryId("uncategorized");
                group.setCategoryName("未分类指标");
            } else {
                group.setCategoryId(categoryId);
                group.setCategoryName(metricsInCategory.get(0).getMetricCategoryName());
            }
            group.setMetricCount(metricsInCategory.size());
            group.setMetrics(metricsInCategory.stream().map(this::toMetricSemanticDTO).collect(Collectors.toList()));
            result.add(group);
        }

        return result;
    }

    @Override
    public List<DimensionCategoryGroupDTO> listDimensionsGroupByCategory(Long datasourceId) {
        // 直接从维度表查询（实体表已有类目字段，无需额外查询类目表）
        List<AloudataDimensionEntity> allDimensions = dimensionMapper.selectList(
                new LambdaQueryWrapper<AloudataDimensionEntity>()
                        .eq(AloudataDimensionEntity::getDatasourceId, datasourceId)
                        .orderByAsc(AloudataDimensionEntity::getDimCategoryId));

        // 按 dimCategoryId 分组，O(n) 复杂度
        Map<String, List<AloudataDimensionEntity>> grouped = allDimensions.stream()
                .collect(Collectors.groupingBy(
                        d -> StringUtils.hasText(d.getDimCategoryId()) ? d.getDimCategoryId() : "",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DimensionCategoryGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<AloudataDimensionEntity>> entry : grouped.entrySet()) {
            String categoryId = entry.getKey();
            List<AloudataDimensionEntity> dimensionsInCategory = entry.getValue();

            DimensionCategoryGroupDTO group = new DimensionCategoryGroupDTO();
            if (categoryId.isEmpty()) {
                group.setCategoryId("uncategorized");
                group.setCategoryName("未分类维度");
            } else {
                group.setCategoryId(categoryId);
                group.setCategoryName(dimensionsInCategory.get(0).getDimCategoryName());
            }
            group.setDimensionCount(dimensionsInCategory.size());
            group.setDimensions(dimensionsInCategory.stream().map(this::toDimensionSemanticDTO).collect(Collectors.toList()));
            result.add(group);
        }

        return result;
    }

    @Override
    public SyncResult rebuildEsIndex(Long datasourceId) {
        long startTime = System.currentTimeMillis();

        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            return new SyncResult(0, 0, 0, 0, 0, "failed", "数据源不存在: " + datasourceId);
        }

        try {
            /* 确保 ES 索引存在，不存在则创建 */
            int vectorDim = resolveVectorDimension(datasourceId);
            esService.ensureIndices(vectorDim);

            /* 清除该数据源的旧 ES 文档 */
            esService.deleteByDatasourceId(datasourceId);

            embedAndIndexAll(datasourceId);

            Long metricCount = metricMapper.selectCount(new LambdaQueryWrapper<AloudataMetricEntity>()
                    .eq(AloudataMetricEntity::getDatasourceId, datasourceId));
            Long dimensionCount = dimensionMapper.selectCount(new LambdaQueryWrapper<AloudataDimensionEntity>()
                    .eq(AloudataDimensionEntity::getDatasourceId, datasourceId));

            long elapsed = System.currentTimeMillis() - startTime;
            return new SyncResult(metricCount.intValue(), dimensionCount.intValue(), 0, 0,
                    elapsed, "completed", "ES 索引重建成功");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[Aloudata同步] ES 索引重建失败: {}", e.getMessage(), e);
            return new SyncResult(0, 0, 0, 0, elapsed, "failed", "ES 索引重建失败: " + e.getMessage());
        }
    }

    // ==================== 同步步骤实现 ====================

    /**
     * 获取类目映射 (categoryId → categoryName) 并持久化到数据库
     * <p>
     * 同时获取指标类目和维度类目，写入 dataagent_aloudata_category 表。
     * 返回的 Map 用于指标写入时填充 categoryName。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> fetchAndSaveCategories(Long datasourceId, AloudataConfigDTO config, int syncVersion) {
        Map<String, String> map = new HashMap<>();
        List<AloudataCategoryEntity> categoryEntities = new ArrayList<>();

        /* 分别获取指标类目和维度类目 */
        for (String categoryType : List.of("CATEGORY_METRIC", "CATEGORY_DIMENSION")) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("categoryType", categoryType);

                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(ENDPOINT_CATEGORY_LIST, config, input);
                ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_CATEGORY_LIST, config, params);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    continue;
                }
                Boolean success = (Boolean) response.getBody().get("success");
                if (!Boolean.TRUE.equals(success)) {
                    continue;
                }

                List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("data");
                if (categories == null) {
                    continue;
                }

                for (Map<String, Object> cat : categories) {
                    String id = cat.get("id") != null ? cat.get("id").toString() : null;
                    String name = (String) cat.get("name");
                    if (id == null) {
                        continue;
                    }

                    /* 写入内存映射 */
                    if (name != null) {
                        map.put(id, name);
                    }

                    /* 构建实体，后续批量 upsert */
                    AloudataCategoryEntity entity = new AloudataCategoryEntity();
                    entity.setDatasourceId(datasourceId);
                    entity.setCategoryId(id);
                    entity.setCategoryName(name);
                    entity.setCategoryType(categoryType);
                    entity.setParentId(cat.get("parentId") != null ? cat.get("parentId").toString() : null);
                    entity.setFrontId(cat.get("frontId") != null ? cat.get("frontId").toString() : null);
                    entity.setType((String) cat.get("type"));
                    entity.setSyncVersion(syncVersion);
                    categoryEntities.add(entity);
                }
            } catch (Exception e) {
                log.warn("[Aloudata同步] 获取类目失败(type={}): {}", categoryType, e.getMessage());
            }
        }

        /* 批量 upsert 类目 */
        if (!categoryEntities.isEmpty()) {
            int batchUpsertSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;
            for (int i = 0; i < categoryEntities.size(); i += batchUpsertSize) {
                List<AloudataCategoryEntity> batch = categoryEntities.subList(i, Math.min(i + batchUpsertSize, categoryEntities.size()));
                categoryMapper.upsertBatch(batch);
            }
        }

        return map;
    }

    // ==================== 流式同步管道 ====================

    /**
     * 流式同步指标：分页获取 → 批量详情 → 批量写入 MySQL → 批量获取维度关联
     * <p>
     * 每页处理完成后立即释放，不累积全量 List 和 detailMap。
     */
    @SuppressWarnings("unchecked")
    private int streamSyncMetrics(Long datasourceId, AloudataConfigDTO config,
                                   Map<String, String> categoryMap, int syncVersion) {
        int totalMetricCount = 0;
        int pageNumber = 1;
        int pageSize = DataAgentConstants.ALOUDATA_SYNC_METRIC_PAGE_SIZE;
        boolean hasNext = true;
        /* 基于 total 计算最大页数，防止分页参数不生效时死循环 */
        long totalFromApi = -1;
        int maxPages = -1;

        /* 收集所有 metricName，用于后续批量获取维度关联 */
        List<String> allMetricNames = new ArrayList<>();

        while (hasNext) {
            /* 1. 分页获取指标列表 */
            Map<String, Object> input = new HashMap<>();
            input.put("statusFilters", List.of("PUBLISHED"));
            input.put("pageNumber", pageNumber);
            input.put("pageSize", pageSize);

            Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(ENDPOINT_METRIC_LIST, config, input);
            ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_METRIC_LIST, config, params);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Aloudata同步] 指标列表接口响应异常，HTTP: {}", response.getStatusCode());
                break;
            }
            Map<String, Object> body = response.getBody();
            Boolean success = (Boolean) body.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.warn("[Aloudata同步] 指标列表接口返回失败: errorMsg={}", body.get("errorMsg"));
                break;
            }

            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) break;

            /* 从首次响应中获取 total，计算最大页数 */
            if (totalFromApi < 0 && data.get("total") != null) {
                totalFromApi = ((Number) data.get("total")).longValue();
                maxPages = (int) Math.ceil((double) totalFromApi / pageSize) + 1;
                log.info("[Aloudata同步] 指标列表 total={}, 预计最大 {} 页", totalFromApi, maxPages);
            }

            List<Map<String, Object>> pageMetrics = (List<Map<String, Object>>) data.get("data");
            if (pageMetrics == null || pageMetrics.isEmpty()) {
                break;
            }

            /* 安全保护：超过最大页数则终止 */
            if (maxPages > 0 && pageNumber > maxPages) {
                log.warn("[Aloudata同步] 指标分页已超过最大页数 {}，终止循环（可能分页参数未生效）", maxPages);
                break;
            }

            /* 2. 批量获取本页指标详情 */
            Map<String, Map<String, Object>> detailMap = fetchMetricDetailBatch(config, pageMetrics);

            /* 3. 构建实体并批量写入 MySQL */
            List<AloudataMetricEntity> entities = buildMetricEntities(datasourceId, pageMetrics, detailMap, categoryMap, syncVersion);
            int batchUpsertSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;
            for (int i = 0; i < entities.size(); i += batchUpsertSize) {
                List<AloudataMetricEntity> batch = entities.subList(i, Math.min(i + batchUpsertSize, entities.size()));
                metricMapper.upsertBatch(batch);
            }
            totalMetricCount += entities.size();

            /* 4. 收集 metricName */
            for (Map<String, Object> m : pageMetrics) {
                String name = (String) m.get("metricName");
                if (name != null) {
                    allMetricNames.add(name);
                }
            }

            log.info("[Aloudata同步] 指标进度: 页 {}, 本页 {}, 累计 {}", pageNumber, pageMetrics.size(), totalMetricCount);
            hasNext = Boolean.TRUE.equals(data.get("hasNext"));
            pageNumber++;
        }

        /* 5. 批量获取指标-维度关联（使用 metric_all_dimensions） */
        fetchAndSaveMetricDimensions(datasourceId, config, allMetricNames, syncVersion);

        return totalMetricCount;
    }

    /**
     * 流式同步维度：分页获取 → 批量详情 → 批量写入 MySQL
     */
    @SuppressWarnings("unchecked")
    private int streamSyncDimensions(Long datasourceId, AloudataConfigDTO config,
                                     Map<String, String> categoryMap, int syncVersion) {
        int totalDimCount = 0;
        int pageNumber = 1;
        int pageSize = DataAgentConstants.ALOUDATA_SYNC_DIMENSION_PAGE_SIZE;
        boolean hasNext = true;
        /* 基于 total 计算最大页数，防止分页参数不生效时死循环 */
        long totalFromApi = -1;
        int maxPages = -1;

        while (hasNext) {
            /* 1. 分页获取维度列表 */
            Map<String, Object> input = new HashMap<>();
            input.put("statusFilters", List.of("PUBLISHED"));
            input.put("pager", Map.of("pageNumber", pageNumber, "pageSize", pageSize));

            Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(ENDPOINT_DIMENSION_LIST, config, input);
            ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_DIMENSION_LIST, config, params);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Aloudata同步] 维度列表接口响应异常");
                break;
            }
            Map<String, Object> body = response.getBody();
            Boolean success = (Boolean) body.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.warn("[Aloudata同步] 维度列表接口返回失败: errorMsg={}", body.get("errorMsg"));
                break;
            }

            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) break;

            /* 从首次响应中获取 total，计算最大页数 */
            if (totalFromApi < 0 && data.get("total") != null) {
                totalFromApi = ((Number) data.get("total")).longValue();
                maxPages = (int) Math.ceil((double) totalFromApi / pageSize) + 1;
                log.info("[Aloudata同步] 维度列表 total={}, 预计最大 {} 页", totalFromApi, maxPages);
            }

            List<Map<String, Object>> pageDims = (List<Map<String, Object>>) data.get("data");
            if (pageDims == null || pageDims.isEmpty()) {
                break;
            }

            /* 安全保护：超过最大页数则终止 */
            if (maxPages > 0 && pageNumber > maxPages) {
                log.warn("[Aloudata同步] 维度分页已超过最大页数 {}，终止循环（可能分页参数未生效）", maxPages);
                break;
            }

            /* 2. 批量获取本页维度详情 */
            Map<String, Map<String, Object>> detailMap = fetchDimensionDetailBatch(config, pageDims);

            /* 3. 构建实体并批量写入 MySQL */
            List<AloudataDimensionEntity> entities = buildDimensionEntities(datasourceId, pageDims, detailMap, categoryMap, syncVersion);
            int batchUpsertSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;
            for (int i = 0; i < entities.size(); i += batchUpsertSize) {
                List<AloudataDimensionEntity> batch = entities.subList(i, Math.min(i + batchUpsertSize, entities.size()));
                dimensionMapper.upsertBatch(batch);
            }
            totalDimCount += entities.size();

            log.info("[Aloudata同步] 维度进度: 页 {}, 本页 {}, 累计 {}", pageNumber, pageDims.size(), totalDimCount);
            hasNext = Boolean.TRUE.equals(data.get("hasNext"));
            pageNumber++;
        }

        return totalDimCount;
    }

    /**
     * 向量化 + ES 索引（分页加载，避免全量驻留内存）
     */
    private void embedAndIndexAll(Long datasourceId) {
        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        Long modelId = resolveEmbeddingModelId();
        boolean embeddingAvailable = embeddingModel != null;

        if (!embeddingAvailable) {
            log.warn("[Aloudata同步] EmbeddingModel 不可用，向量化跳过，ES 仅写入关键词字段");
        }

        int batchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;
        int vectorDim = 0;

        // 分页向量化 + 索引指标
        int metricPage = 0;
        long totalMetrics = 0;
        while (true) {
            List<AloudataMetricEntity> metrics = metricMapper.selectList(new LambdaQueryWrapper<AloudataMetricEntity>()
                    .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                    .last("LIMIT " + batchSize + " OFFSET " + (metricPage * batchSize)));
            if (metrics.isEmpty()) break;

            for (AloudataMetricEntity metric : metrics) {
                if (metric.getEmbedding() == null && StringUtils.hasText(metric.getEmbeddingText()) && embeddingAvailable) {
                    try {
                        float[] vector = embeddingModel.embed(metric.getEmbeddingText());
                        if (vector != null && vector.length > 0) {
                            metric.setEmbedding(WikiEmbeddingService.floatsToBytes(vector));
                            metric.setEmbeddingModelId(modelId);
                            metricMapper.updateById(metric);
                            if (vectorDim == 0) {
                                vectorDim = vector.length;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[Aloudata同步] 指标 [{}] 向量化失败: {}", metric.getMetricName(), e.getMessage());
                    }
                }
            }

            if (vectorDim > 0) {
                esService.ensureIndices(vectorDim);
            } else if (metricPage == 0) {
                esService.ensureIndices(DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION);
            }
            esService.indexMetrics(metrics);
            totalMetrics += metrics.size();
            metricPage++;
        }

        // 分页向量化 + 索引维度
        int dimPage = 0;
        long totalDims = 0;
        while (true) {
            List<AloudataDimensionEntity> dimensions = dimensionMapper.selectList(new LambdaQueryWrapper<AloudataDimensionEntity>()
                    .eq(AloudataDimensionEntity::getDatasourceId, datasourceId)
                    .last("LIMIT " + batchSize + " OFFSET " + (dimPage * batchSize)));
            if (dimensions.isEmpty()) break;

            for (AloudataDimensionEntity dim : dimensions) {
                if (dim.getEmbedding() == null && StringUtils.hasText(dim.getEmbeddingText()) && embeddingAvailable) {
                    try {
                        float[] vector = embeddingModel.embed(dim.getEmbeddingText());
                        if (vector != null && vector.length > 0) {
                            dim.setEmbedding(WikiEmbeddingService.floatsToBytes(vector));
                            dim.setEmbeddingModelId(modelId);
                            dimensionMapper.updateById(dim);
                            if (vectorDim == 0) vectorDim = vector.length;
                        }
                    } catch (Exception e) {
                        log.warn("[Aloudata同步] 维度 [{}] 向量化失败: {}", dim.getDimName(), e.getMessage());
                    }
                }
            }

            esService.indexDimensions(dimensions);
            totalDims += dimensions.size();
            dimPage++;
        }

        log.info("[Aloudata同步] ES 索引完成，指标: {}, 维度: {}, 向量化: {}",
                totalMetrics, totalDims, embeddingAvailable ? "已启用" : "已跳过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 批量获取指标详情（单页数据量，使用 metric_batch_detail）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> fetchMetricDetailBatch(AloudataConfigDTO config,
                                                                      List<Map<String, Object>> pageMetrics) {
        Map<String, Map<String, Object>> detailMap = new HashMap<>();
        if (pageMetrics.isEmpty()) {
            return detailMap;
        }

        List<String> metricNames = pageMetrics.stream()
                .map(m -> (String) m.get("metricName"))
                .filter(Objects::nonNull)
                .toList();

        int batchSize = 50;
        for (int i = 0; i < metricNames.size(); i += batchSize) {
            List<String> batch = metricNames.subList(i, Math.min(i + batchSize, metricNames.size()));
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("metricNames", batch);

                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                        ENDPOINT_METRIC_BATCH_DETAIL, config, input);
                ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_METRIC_BATCH_DETAIL, config, params);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    Boolean success = (Boolean) body.get("success");
                    if (Boolean.TRUE.equals(success) && body.get("data") != null) {
                        List<Map<String, Object>> details = (List<Map<String, Object>>) body.get("data");
                        if (details != null) {
                            for (Map<String, Object> detail : details) {
                                String name = (String) detail.get("metricName");
                                if (name != null) {
                                    detailMap.put(name, detail);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[Aloudata同步] 批量获取指标详情失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
        return detailMap;
    }

    /**
     * 批量获取维度详情（单页数据量，逐个调用 dimension_detail）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> fetchDimensionDetailBatch(AloudataConfigDTO config,
                                                                         List<Map<String, Object>> pageDims) {
        Map<String, Map<String, Object>> detailMap = new HashMap<>();
        if (pageDims.isEmpty()) {
            return detailMap;
        }

        for (Map<String, Object> dimData : pageDims) {
            String dimName = (String) dimData.get("dimName");
            if (dimName == null) continue;
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("dimName", dimName);
                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                        ENDPOINT_DIMENSION_DETAIL, config, input);
                ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_DIMENSION_DETAIL, config, params);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    Boolean success = (Boolean) body.get("success");
                    if (Boolean.TRUE.equals(success) && body.get("data") != null) {
                        detailMap.put(dimName, (Map<String, Object>) body.get("data"));
                    }
                }
            } catch (Exception e) {
                log.debug("[Aloudata同步] 获取维度 [{}] 详情失败: {}", dimName, e.getMessage());
            }
        }
        return detailMap;
    }

    /**
     * 构建指标实体列表（从列表数据 + 详情数据 + 类目映射）
     */
    @SuppressWarnings("unchecked")
    private List<AloudataMetricEntity> buildMetricEntities(Long datasourceId, List<Map<String, Object>> pageMetrics,
                                                            Map<String, Map<String, Object>> detailMap,
                                                            Map<String, String> categoryMap, int syncVersion) {
        List<AloudataMetricEntity> entities = new ArrayList<>(pageMetrics.size());
        for (Map<String, Object> metricData : pageMetrics) {
            String metricName = (String) metricData.get("metricName");
            if (metricName == null) continue;

            AloudataMetricEntity entity = new AloudataMetricEntity();
            entity.setDatasourceId(datasourceId);
            entity.setMetricName(metricName);
            entity.setMetricCode((String) metricData.get("metricCode"));
            entity.setMetricDisplayName((String) metricData.get("metricDisplayName"));
            entity.setVersion((Integer) metricData.get("version"));
            entity.setType((String) metricData.get("type"));
            entity.setStatus((String) metricData.get("status"));
            entity.setPublishStatus((String) metricData.get("publishStatus"));
            entity.setDisplayStatus((String) metricData.get("displayStatus"));
            entity.setBusinessCaliber((String) metricData.get("businessCaliber"));
            entity.setOwner((String) metricData.get("owner"));
            entity.setBusinessOwner((String) metricData.get("businessOwner"));
            entity.setMetricCategoryId(metricData.get("metricCategoryId") != null
                    ? metricData.get("metricCategoryId").toString() : null);
            entity.setUnit((String) metricData.get("unit"));
            entity.setCnUnit((String) metricData.get("cnUnit"));
            entity.setMetricViewCount((Integer) metricData.get("metricViewCount"));
            entity.setTimeGranularity((String) metricData.get("timeGranularity"));
            entity.setHasDateLimit((Boolean) metricData.get("hasDateLimit"));
            entity.setHasDerivationMethod((Boolean) metricData.get("hasDerivationMethod"));
            entity.setMetricTimeDataType((String) metricData.get("metricTimeDataType"));
            entity.setCanEdit((Boolean) metricData.get("canEdit"));
            entity.setCanDelete((Boolean) metricData.get("canDelete"));
            entity.setCanUsage((Boolean) metricData.get("canUsage"));
            entity.setCanAuth((Boolean) metricData.get("canAuth"));
            entity.setCanTransfer((Boolean) metricData.get("canTransfer"));
            entity.setGmtCreate((String) metricData.get("gmtCreate"));
            entity.setGmtUpdate((String) metricData.get("gmtUpdate"));
            entity.setSyncVersion(syncVersion);

            // properties JSON 数组
            Object propertiesObj = metricData.get("properties");
            if (propertiesObj instanceof List) {
                entity.setProperties(propertiesObj.toString());
            }

            if (entity.getMetricCategoryId() != null) {
                entity.setMetricCategoryName(categoryMap.get(entity.getMetricCategoryId()));
            }

            Map<String, Object> detail = detailMap.get(metricName);
            if (detail != null) {
                Object synonymsObj = detail.get("synonyms");
                if (synonymsObj instanceof List) {
                    entity.setSynonyms(String.join(",", ((List<String>) synonymsObj)));
                }
                String catName = (String) detail.get("metricCategoryName");
                if (catName != null) {
                    entity.setMetricCategoryName(catName);
                }
            }

            entity.setEmbeddingText(entity.buildEmbeddingText());
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 构建维度实体列表（从列表数据 + 详情数据）
     */
    @SuppressWarnings("unchecked")
    private List<AloudataDimensionEntity> buildDimensionEntities(Long datasourceId, List<Map<String, Object>> pageDims,
                                                                   Map<String, Map<String, Object>> detailMap,
                                                                   Map<String, String> categoryMap, int syncVersion) {
        List<AloudataDimensionEntity> entities = new ArrayList<>(pageDims.size());
        for (Map<String, Object> dimData : pageDims) {
            String dimName = (String) dimData.get("dimName");
            if (dimName == null) continue;

            AloudataDimensionEntity entity = new AloudataDimensionEntity();
            entity.setDatasourceId(datasourceId);
            entity.setDimName(dimName);
            entity.setDimCode((String) dimData.get("dimCode"));
            entity.setDimDisplayName((String) dimData.get("dimDisplayName"));
            entity.setOriginDataType((String) dimData.get("originDataType"));
            entity.setDimDescription((String) dimData.get("dimDescription"));
            entity.setDisplayStatus((String) dimData.get("displayStatus"));
            entity.setDatasetName((String) dimData.get("datasetName"));
            entity.setSyncVersion(syncVersion);
            entity.setIsTimeDimension(dimName.startsWith("metric_time__"));

            // 从列表数据提取类目信息（字段名为 dimCategoryId）
            Object categoryIdObj = dimData.get("dimCategoryId");
            if (categoryIdObj != null) {
                entity.setDimCategoryId(categoryIdObj.toString());
                entity.setDimCategoryName(categoryMap.get(entity.getDimCategoryId()));
            }

            Map<String, Object> detail = detailMap.get(dimName);
            if (detail != null) {
                Object synonymsObj = detail.get("synonyms");
                if (synonymsObj instanceof List) {
                    entity.setSynonyms(String.join(",", ((List<String>) synonymsObj)));
                }
                Object configObj = detail.get("config");
                if (configObj instanceof Map) {
                    Map<String, Object> configMap = (Map<String, Object>) configObj;
                    entity.setConfigType((String) configMap.get("type"));
                    entity.setConfigValue((String) configMap.get("value"));
                }
                entity.setDatasetName((String) detail.get("datasetName"));
                // 从详情数据提取类目信息（字段名为 dimCategoryId，优先级高于列表数据）
                Object detailCategoryId = detail.get("dimCategoryId");
                if (detailCategoryId != null) {
                    entity.setDimCategoryId(detailCategoryId.toString());
                    entity.setDimCategoryName(categoryMap.get(entity.getDimCategoryId()));
                }
                Object detailCategoryName = detail.get("dimCategoryName");
                if (detailCategoryName != null) {
                    entity.setDimCategoryName((String) detailCategoryName);
                }
            }

            entity.setEmbeddingText(entity.buildEmbeddingText());
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 批量获取指标-维度关联并写入
     * <p>
     * 使用 metric_all_dimensions（dimensionAll）接口，响应按指标名分组。
     */
    @SuppressWarnings("unchecked")
    private void fetchAndSaveMetricDimensions(Long datasourceId, AloudataConfigDTO config,
                                               List<String> metricNames, int syncVersion) {
        int batchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_SIZE;
        for (int i = 0; i < metricNames.size(); i += batchSize) {
            List<String> batch = metricNames.subList(i, Math.min(i + batchSize, metricNames.size()));
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("metricNames", batch);

                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                        ENDPOINT_METRIC_ALL_DIMENSIONS, config, input);
                ResponseEntity<Map> response = apiClient.callWithParams(ENDPOINT_METRIC_ALL_DIMENSIONS, config, params);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    continue;
                }
                Boolean success = (Boolean) response.getBody().get("success");
                if (!Boolean.TRUE.equals(success) || response.getBody().get("data") == null) {
                    continue;
                }

                /* data 格式: { "metricName1": [dim1, ...], "metricName2": [dim3, ...] } */
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                List<AloudataMetricDimensionEntity> rels = new ArrayList<>();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String metricName = entry.getKey();
                    if (!(entry.getValue() instanceof List)) continue;
                    List<Map<String, Object>> dimensions = (List<Map<String, Object>>) entry.getValue();
                    for (Map<String, Object> dim : dimensions) {
                        String dimName = (String) dim.get("dimName");
                        if (dimName == null) continue;

                        AloudataMetricDimensionEntity rel = new AloudataMetricDimensionEntity();
                        rel.setDatasourceId(datasourceId);
                        rel.setMetricName(metricName);
                        rel.setDimName(dimName);
                        rel.setDimDisplayName((String) dim.get("dimDisplayName"));
                        rel.setOriginDataType((String) dim.get("originDataType"));
                        rel.setSyncVersion(syncVersion);
                        rels.add(rel);
                    }
                }

                /* 批量 upsert */
                int upsertBatchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;
                for (int j = 0; j < rels.size(); j += upsertBatchSize) {
                    List<AloudataMetricDimensionEntity> upsertBatch = rels.subList(j, Math.min(j + upsertBatchSize, rels.size()));
                    metricDimensionMapper.upsertBatch(upsertBatch);
                }

                log.debug("[Aloudata同步] 指标-维度关联进度: {}/{}", Math.min(i + batchSize, metricNames.size()), metricNames.size());
            } catch (Exception e) {
                log.warn("[Aloudata同步] 获取指标-维度关联失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
    }

    /**
     * 统计指标-维度关联数量
     */
    private int countMetricDimensions(Long datasourceId) {
        Long count = metricDimensionMapper.selectCount(new LambdaQueryWrapper<AloudataMetricDimensionEntity>()
                .eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId));
        return count != null ? count.intValue() : 0;
    }

    private int getNextSyncVersion(Long datasourceId) {
        // 获取当前最大版本号
        LambdaQueryWrapper<AloudataMetricEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricEntity::getDatasourceId, datasourceId);
        wrapper.select(AloudataMetricEntity::getSyncVersion);
        wrapper.orderByDesc(AloudataMetricEntity::getSyncVersion);
        wrapper.last("LIMIT 1");

        AloudataMetricEntity latest = metricMapper.selectOne(wrapper);
        return latest != null && latest.getSyncVersion() != null ? latest.getSyncVersion() + 1 : 1;
    }

    private void cleanOldVersionData(Long datasourceId, int currentVersion) {
        // 删除旧版本的指标-维度关联
        metricDimensionMapper.delete(new LambdaQueryWrapper<AloudataMetricDimensionEntity>()
                .eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId)
                .lt(AloudataMetricDimensionEntity::getSyncVersion, currentVersion));

        // 删除旧版本的指标
        metricMapper.delete(new LambdaQueryWrapper<AloudataMetricEntity>()
                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                .lt(AloudataMetricEntity::getSyncVersion, currentVersion));

        // 删除旧版本的维度
        dimensionMapper.delete(new LambdaQueryWrapper<AloudataDimensionEntity>()
                .eq(AloudataDimensionEntity::getDatasourceId, datasourceId)
                .lt(AloudataDimensionEntity::getSyncVersion, currentVersion));

        // 删除旧版本的类目
        categoryMapper.delete(new LambdaQueryWrapper<AloudataCategoryEntity>()
                .eq(AloudataCategoryEntity::getDatasourceId, datasourceId)
                .lt(AloudataCategoryEntity::getSyncVersion, currentVersion));

        log.debug("[Aloudata同步] 旧版本数据清理完成，当前版本: {}", currentVersion);
    }

    /**
     * 解析向量维度：优先从已有 embedding 数据推断，否则使用默认值
     */
    private int resolveVectorDimension(Long datasourceId) {
        /* 尝试从指标的已有 embedding 推断维度 */
        AloudataMetricEntity metricWithEmbedding = metricMapper.selectOne(new LambdaQueryWrapper<AloudataMetricEntity>()
                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                .isNotNull(AloudataMetricEntity::getEmbedding)
                .last("LIMIT 1"));
        if (metricWithEmbedding != null && metricWithEmbedding.getEmbedding() != null) {
            float[] floats = WikiEmbeddingService.bytesToFloats(metricWithEmbedding.getEmbedding());
            if (floats.length > 0) {
                return floats.length;
            }
        }

        /* 尝试从维度的已有 embedding 推断维度 */
        AloudataDimensionEntity dimWithEmbedding = dimensionMapper.selectOne(new LambdaQueryWrapper<AloudataDimensionEntity>()
                .eq(AloudataDimensionEntity::getDatasourceId, datasourceId)
                .isNotNull(AloudataDimensionEntity::getEmbedding)
                .last("LIMIT 1"));
        if (dimWithEmbedding != null && dimWithEmbedding.getEmbedding() != null) {
            float[] floats = WikiEmbeddingService.bytesToFloats(dimWithEmbedding.getEmbedding());
            if (floats.length > 0) {
                return floats.length;
            }
        }

        /* 尝试通过 EmbeddingModel 获取维度 */
        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        if (embeddingModel != null) {
            try {
                float[] vector = embeddingModel.embed("test");
                if (vector != null && vector.length > 0) {
                    return vector.length;
                }
            } catch (Exception e) {
                log.warn("[Aloudata同步] 通过 EmbeddingModel 获取向量维度失败: {}", e.getMessage());
            }
        }

        return DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION;
    }

    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModelFactory == null) return null;
        try {
            ModelConfigEntity config = resolveEmbeddingModelConfig();
            if (config == null) return null;
            return embeddingModelFactory.build(config);
        } catch (Exception e) {
            log.warn("[Aloudata同步] 构建 EmbeddingModel 失败: {}", e.getMessage());
            return null;
        }
    }

    private ModelConfigEntity resolveEmbeddingModelConfig() {
        // 复用 SchemaEmbeddingServiceImpl 的三级解析逻辑
        ModelConfigEntity marked = modelConfigService.listEnabledModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled())
                        && "embedding".equals(m.getModelType())
                        && Boolean.TRUE.equals(m.getIsDefault()))
                .findFirst().orElse(null);
        if (marked != null) return marked;

        return modelConfigService.findFirstEnabledEmbedding();
    }

    private Long resolveEmbeddingModelId() {
        ModelConfigEntity config = resolveEmbeddingModelConfig();
        return config != null ? config.getId() : null;
    }

    private AloudataMetricSemanticDTO toMetricSemanticDTO(AloudataMetricEntity entity) {
        AloudataMetricSemanticDTO dto = new AloudataMetricSemanticDTO();
        dto.setMetricName(entity.getMetricName());
        dto.setMetricDisplayName(entity.getMetricDisplayName());
        dto.setType(entity.getType());
        dto.setBusinessCaliber(entity.getBusinessCaliber());
        dto.setOwner(entity.getOwner());
        dto.setMetricCategoryId(entity.getMetricCategoryId());
        dto.setMetricCategoryName(entity.getMetricCategoryName());
        dto.setUnit(entity.getUnit());
        if (entity.getSynonyms() != null && !entity.getSynonyms().isBlank()) {
            dto.setSynonyms(Arrays.asList(entity.getSynonyms().split(",")));
        }
        // 填充可用维度
        dto.setAvailableDimensions(listMetricDimensions(entity.getDatasourceId(), entity.getMetricName()));
        return dto;
    }

    private AloudataDimensionSemanticDTO toDimensionSemanticDTO(AloudataDimensionEntity entity) {
        AloudataDimensionSemanticDTO dto = new AloudataDimensionSemanticDTO();
        dto.setDimName(entity.getDimName());
        dto.setDimDisplayName(entity.getDimDisplayName());
        dto.setOriginDataType(entity.getOriginDataType());
        dto.setDimDescription(entity.getDimDescription());
        dto.setConfigType(entity.getConfigType());
        dto.setConfigValue(entity.getConfigValue());
        dto.setDatasetName(entity.getDatasetName());
        dto.setCategoryId(entity.getDimCategoryId());
        dto.setCategoryName(entity.getDimCategoryName());
        if (entity.getSynonyms() != null && !entity.getSynonyms().isBlank()) {
            dto.setSynonyms(Arrays.asList(entity.getSynonyms().split(",")));
        }
        return dto;
    }
}
