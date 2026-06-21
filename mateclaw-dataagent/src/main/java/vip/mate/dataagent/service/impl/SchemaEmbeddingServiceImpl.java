package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.dto.SemanticModelVO;
import vip.mate.dataagent.model.*;
import vip.mate.dataagent.repository.*;
import vip.mate.dataagent.service.SchemaElasticsearchService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.system.service.SystemSettingService;
import vip.mate.wiki.service.WikiEmbeddingService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 向量化检索服务实现
 * <p>
 * 将数据源表级 Schema 信息嵌入为向量，使用 Elasticsearch 进行关键词检索和向量语义检索。
 * 支持混合检索（RRF 融合）。Elasticsearch 不可用时自动降级为 MySQL 模糊匹配。
 */
@Service
@RequiredArgsConstructor
public class SchemaEmbeddingServiceImpl implements SchemaEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SchemaEmbeddingServiceImpl.class);

    private final SchemaEmbeddingMapper schemaEmbeddingMapper;
    private final DatasourceTableMapper datasourceTableMapper;
    private final DatasourceColumnMapper datasourceColumnMapper;
    private final SemanticModelMapper semanticModelMapper;
    private final LogicalRelationMapper logicalRelationMapper;
    private final ModelConfigService modelConfigService;
    private final SystemSettingService systemSettingService;

    /** Elasticsearch 检索服务（可选，缺失时降级为 MySQL 检索） */
    @Autowired(required = false)
    private SchemaElasticsearchService schemaElasticsearchService;

    /** 可选依赖：Embedding 模型工厂，缺失时嵌入操作降级为仅保存文本 */
    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    /** 可选依赖：Wiki 嵌入服务，提供向量工具方法，缺失时语义检索降级 */
    @Autowired(required = false)
    private WikiEmbeddingService wikiEmbeddingService;

    /** 可选依赖：模型供应商服务，用于检查 Provider 是否可用 */
    @Autowired(required = false)
    private ModelProviderService modelProviderService;

    /**
     * 为数据源的所有表生成 Schema 嵌入
     */
    @Override
    public int embedSchema(Long datasourceId) {
        if (datasourceId == null) {
            return 0;
        }
        List<DatasourceTableEntity> tables = listActiveTables(datasourceId);
        int count = 0;
        for (DatasourceTableEntity table : tables) {
            try {
                boolean result = doEmbedTable(datasourceId, table.getTableName());
                if (result) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("嵌入表 [{}.{}] 失败: {}", datasourceId, table.getTableName(), e.getMessage());
            }
        }
        log.info("数据源 [{}] Schema 嵌入完成，共处理 {} 张表", datasourceId, count);
        return count;
    }

    /**
     * 为单张表生成 Schema 嵌入
     */
    @Override
    public boolean embedTable(Long datasourceId, String tableName) {
        if (datasourceId == null || !StringUtils.hasText(tableName)) {
            return false;
        }
        try {
            return doEmbedTable(datasourceId, tableName);
        } catch (Exception e) {
            log.error("嵌入表 [{}.{}] 失败: {}", datasourceId, tableName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 语义检索相关表
     * <p>
     * 优先使用 Elasticsearch 进行关键词和向量语义检索（混合模式），
     * ES 不可用时降级为 MySQL LIKE 模糊匹配 + 内存余弦相似度计算。
     */
    @Override
    public SchemaSearchResult searchSchema(SchemaSearchRequest request) {
        /* 优先使用 Elasticsearch 检索 */
        if (schemaElasticsearchService != null) {
            return searchSchemaWithElasticsearch(request);
        }
        /* 降级：使用 MySQL 检索 */
        return searchSchemaWithMySQL(request);
    }

    /**
     * 删除数据源的所有 Schema 嵌入
     */
    @Override
    public void deleteByDatasourceId(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        LambdaQueryWrapper<SchemaEmbeddingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchemaEmbeddingEntity::getDatasourceId, datasourceId);
        schemaEmbeddingMapper.delete(wrapper);
        log.info("已删除数据源 [{}] 的所有 Schema 嵌入", datasourceId);

        /* 同步删除 ES 索引 */
        if (schemaElasticsearchService != null) {
            try {
                schemaElasticsearchService.deleteByDatasourceId(datasourceId);
            } catch (Exception e) {
                log.warn("ES 索引删除失败，数据源: {} - {}", datasourceId, e.getMessage());
            }
        }
    }

    /**
     * 删除单张表的 Schema 嵌入
     */
    @Override
    public void deleteByTableName(Long datasourceId, String tableName) {
        if (datasourceId == null || !StringUtils.hasText(tableName)) {
            return;
        }
        LambdaQueryWrapper<SchemaEmbeddingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchemaEmbeddingEntity::getDatasourceId, datasourceId);
        wrapper.eq(SchemaEmbeddingEntity::getTableName, tableName);
        schemaEmbeddingMapper.delete(wrapper);
        log.info("已删除表 [{}.{}] 的 Schema 嵌入", datasourceId, tableName);

        /* 同步删除 ES 索引 */
        if (schemaElasticsearchService != null) {
            try {
                schemaElasticsearchService.deleteByTableName(datasourceId, tableName);
            } catch (Exception e) {
                log.warn("ES 索引删除失败: {}/{} - {}", datasourceId, tableName, e.getMessage());
            }
        }
    }

    /**
     * 构建表级 Schema 嵌入文本
     * <p>
     * 格式: "表名 表注释 | 字段: 列名1(业务别名1) [类型1] - 描述1, 列名2(业务别名2) [类型2] - 描述2, ..."
     */
    @Override
    public String buildEmbeddingText(Long datasourceId, String tableName) {
        if (datasourceId == null || !StringUtils.hasText(tableName)) {
            return "";
        }

        /* 查询表注释 */
        String tableComment = getTableComment(datasourceId, tableName);

        /* 查询列信息 */
        DatasourceTableEntity tableEntity = getTableEntity(datasourceId, tableName);
        List<DatasourceColumnEntity> columns = listColumns(datasourceId, tableEntity);

        /* 查询语义模型（status=1 启用） */
        Map<String, SemanticModelEntity> semanticMap = listEnabledSemanticModels(datasourceId, tableName);

        /* 构建嵌入文本 */
        StringBuilder sb = new StringBuilder();
        sb.append(tableName);
        if (StringUtils.hasText(tableComment)) {
            sb.append(" ").append(tableComment);
        }
        sb.append(" | 字段: ");

        List<String> columnParts = new ArrayList<>();
        for (DatasourceColumnEntity column : columns) {
            SemanticModelEntity semantic = semanticMap.get(column.getColumnName());
            String part = buildColumnPart(column, semantic);
            columnParts.add(part);
        }
        sb.append(String.join(", ", columnParts));

        return sb.toString();
    }

    // ==================== Elasticsearch 检索 ====================

    /**
     * 使用 Elasticsearch 进行检索
     * <p>
     * 支持三种模式：
     * 1. 混合检索（关键词 + 向量 kNN + RRF 融合）
     * 2. 仅向量语义检索
     * 3. 仅关键词检索
     */
    private SchemaSearchResult searchSchemaWithElasticsearch(SchemaSearchRequest request) {
        long startTime = System.currentTimeMillis();
        SchemaSearchResult result = new SchemaSearchResult();

        if (request == null || request.getDatasourceId() == null || !StringUtils.hasText(request.getQuery())) {
            result.setTableHits(List.of());
            result.setRelations(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        int topK = request.getTopK() != null ? request.getTopK() : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_TOP_K;
        double threshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold()
                : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD;

        /* 尝试获取查询向量 */
        float[] queryVector = null;
        boolean semanticAvailable = isSemanticAvailable();
        if (semanticAvailable) {
            queryVector = generateQueryVector(request.getQuery());
        }

        /* 使用 ES 混合检索 */
        SchemaSearchResult esResult;
        try {
            esResult = schemaElasticsearchService.hybridSearch(request, queryVector);
        } catch (Exception e) {
            log.warn("Elasticsearch 检索失败，降级为 MySQL: {}", e.getMessage());
            return searchSchemaWithMySQL(request);
        }

        /* 补充表注释和语义字段信息（ES 仅返回表名和分数，需从 MySQL 补充） */
        List<SchemaSearchResult.TableHit> enrichedHits = new ArrayList<>();
        Set<String> hitTableNames = new HashSet<>();
        for (SchemaSearchResult.TableHit hit : esResult.getTableHits()) {
            hitTableNames.add(hit.getTableName());
            hit.setTableComment(getTableComment(request.getDatasourceId(), hit.getTableName()));
            hit.setSemanticFields(listSemanticModelVOs(request.getDatasourceId(), hit.getTableName()));
            enrichedHits.add(hit);
        }

        /* 查询命中表相关的逻辑外键关系 */
        List<LogicalRelationVO> relations = listRelatedRelations(request.getDatasourceId(), hitTableNames);

        result.setTableHits(enrichedHits);
        result.setRelations(relations);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 生成查询向量
     */
    private float[] generateQueryVector(String query) {
        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        if (embeddingModel == null) {
            return null;
        }
        try {
            EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
            return resp.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.warn("查询文本向量化失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== MySQL 降级检索 ====================

    /**
     * MySQL 降级检索（ES 不可用时使用）
     */
    private SchemaSearchResult searchSchemaWithMySQL(SchemaSearchRequest request) {
        long startTime = System.currentTimeMillis();
        SchemaSearchResult result = new SchemaSearchResult();

        if (request == null || request.getDatasourceId() == null || !StringUtils.hasText(request.getQuery())) {
            result.setTableHits(List.of());
            result.setRelations(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        int topK = request.getTopK() != null ? request.getTopK() : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_TOP_K;
        double threshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold()
                : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD;

        /* 判断是否具备语义检索能力 */
        boolean semanticAvailable = isSemanticAvailable();

        /* 关键词检索 */
        Map<String, Double> keywordScores = keywordSearch(request.getDatasourceId(), request.getQuery());

        /* 向量语义检索 */
        Map<String, Double> semanticScores = new HashMap<>();
        if (semanticAvailable) {
            semanticScores = semanticSearch(request.getDatasourceId(), request.getQuery(), threshold);
        }

        /* RRF 融合或单一检索 */
        Map<String, Double> fusedScores;
        String matchSource;
        if (!keywordScores.isEmpty() && !semanticScores.isEmpty()) {
            fusedScores = rrfFuse(keywordScores, semanticScores);
            matchSource = "hybrid";
        } else if (!semanticScores.isEmpty()) {
            fusedScores = semanticScores;
            matchSource = "semantic";
        } else {
            fusedScores = keywordScores;
            matchSource = "keyword";
        }

        /* 按 score 降序排序，取 Top-K */
        List<Map.Entry<String, Double>> sorted = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .toList();

        /* 构建 TableHit 列表 */
        List<SchemaSearchResult.TableHit> tableHits = new ArrayList<>();
        Set<String> hitTableNames = new HashSet<>();
        for (Map.Entry<String, Double> entry : sorted) {
            String tableName = entry.getKey();
            double score = entry.getValue();
            hitTableNames.add(tableName);

            SchemaSearchResult.TableHit hit = new SchemaSearchResult.TableHit();
            hit.setTableName(tableName);
            hit.setTableComment(getTableComment(request.getDatasourceId(), tableName));
            hit.setScore(score);
            hit.setMatchSource(matchSource);
            hit.setSemanticFields(listSemanticModelVOs(request.getDatasourceId(), tableName));
            tableHits.add(hit);
        }

        /* 查询命中表相关的逻辑外键关系 */
        List<LogicalRelationVO> relations = listRelatedRelations(request.getDatasourceId(), hitTableNames);

        result.setTableHits(tableHits);
        result.setRelations(relations);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== private 方法 ====================

    /**
     * 执行单张表的嵌入操作
     * <p>
     * 构建嵌入文本，尝试获取 EmbeddingModel 生成向量。
     * 若模型不可用则仅保存嵌入文本，向量字段留空。
     * 同时写入 Elasticsearch 索引。
     */
    private boolean doEmbedTable(Long datasourceId, String tableName) {
        /* 构建嵌入文本 */
        String embeddingText = buildEmbeddingText(datasourceId, tableName);
        if (!StringUtils.hasText(embeddingText)) {
            log.warn("表 [{}.{}] 嵌入文本为空，跳过", datasourceId, tableName);
            return false;
        }

        /* 查询已有嵌入记录 */
        SchemaEmbeddingEntity existing = getExistingEmbedding(datasourceId, tableName);

        /* 版本一致且文本未变则跳过 */
        if (existing != null
                && existing.getEmbeddingTextVersion() != null
                && existing.getEmbeddingTextVersion() == DataAgentConstants.SCHEMA_EMBEDDING_TEXT_VERSION
                && embeddingText.equals(existing.getEmbeddingText())) {
            log.debug("表 [{}.{}] 嵌入已是最新版本，跳过", datasourceId, tableName);
            return false;
        }

        /* 尝试生成向量 */
        byte[] embeddingBytes = null;
        float[] embeddingVector = null;
        Long embeddingModelId = null;
        ModelConfigEntity resolvedConfig = resolveEmbeddingModelConfig();
        EmbeddingModel embeddingModel = null;
        if (resolvedConfig != null && embeddingModelFactory != null) {
            try {
                embeddingModel = embeddingModelFactory.build(resolvedConfig);
            } catch (Exception e) {
                log.warn("构建 EmbeddingModel 失败: {}", e.getMessage());
            }
        }
        if (embeddingModel != null) {
            try {
                float[] vector = embeddingModel.embed(embeddingText);
                if (vector != null && vector.length > 0) {
                    embeddingBytes = WikiEmbeddingService.floatsToBytes(vector);
                    embeddingVector = vector;
                    embeddingModelId = resolvedConfig.getId();
                }
            } catch (Exception e) {
                log.warn("表 [{}.{}] 向量化失败，仅保存嵌入文本: {}", datasourceId, tableName, e.getMessage());
            }
        } else {
            log.info("EmbeddingModel 不可用，表 [{}.{}] 仅保存嵌入文本", datasourceId, tableName);
        }

        /* 插入或更新 MySQL 记录 */
        if (existing != null) {
            existing.setEmbeddingText(embeddingText);
            existing.setEmbedding(embeddingBytes);
            existing.setEmbeddingModelId(embeddingModelId);
            existing.setEmbeddingTextVersion(DataAgentConstants.SCHEMA_EMBEDDING_TEXT_VERSION);
            schemaEmbeddingMapper.updateById(existing);
        } else {
            SchemaEmbeddingEntity entity = new SchemaEmbeddingEntity();
            entity.setDatasourceId(datasourceId);
            entity.setTableName(tableName);
            entity.setEmbeddingText(embeddingText);
            entity.setEmbedding(embeddingBytes);
            entity.setEmbeddingModelId(embeddingModelId);
            entity.setEmbeddingTextVersion(DataAgentConstants.SCHEMA_EMBEDDING_TEXT_VERSION);
            schemaEmbeddingMapper.insert(entity);
        }

        /* 同步写入 Elasticsearch 索引 */
        if (schemaElasticsearchService != null) {
            try {
                schemaElasticsearchService.indexSchema(datasourceId, tableName, embeddingText, embeddingVector);
            } catch (Exception e) {
                log.warn("ES 索引写入失败: {}/{} - {}", datasourceId, tableName, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 解析可用的 EmbeddingModel 实例
     */
    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModelFactory == null) {
            return null;
        }
        try {
            ModelConfigEntity modelConfig = resolveEmbeddingModelConfig();
            if (modelConfig == null) {
                return null;
            }
            return embeddingModelFactory.build(modelConfig);
        } catch (Exception e) {
            log.warn("解析 EmbeddingModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析应使用的 Embedding 模型配置
     * <p>
     * 解析优先级：
     * <ol>
     *   <li>系统默认：{@code mate_system_setting} 表中 {@code embedding.default.model.id} 配置</li>
     *   <li>任意 enabled 的 embedding 模型（取第一个）</li>
     *   <li>全无 → 返回 null</li>
     * </ol>
     */
    private ModelConfigEntity resolveEmbeddingModelConfig() {
        /* 优先级 1：is_default=1 的 embedding 模型（与 chat 模型统一存储于 is_default 字段） */
        ModelConfigEntity marked = modelConfigService.listEnabledModels()
                .stream()
                .filter(modelConfigEntity -> modelConfigEntity.getEnabled()
                        && "embedding".equals(modelConfigEntity.getModelType()) && modelConfigEntity.getIsDefault())
                .findFirst()
                .orElse(null);
        if (isUsable(marked)) {
            return marked;
        }

        /* 优先级 2：兼容旧版，从 mate_system_setting 表读取 */
        Long defaultId = readSystemDefaultEmbeddingId();
        if (defaultId != null) {
            ModelConfigEntity model = safeGetModel(defaultId);
            if (isUsable(model)) {
                return model;
            }
            log.warn("系统默认 embedding 模型 {} 不可用，回退", defaultId);
        }

        /* 优先级 3：任意 enabled */
        ModelConfigEntity anyEnabled = modelConfigService.findFirstEnabledEmbedding();
        if (isUsable(anyEnabled)) {
            return anyEnabled;
        }

        log.warn("没有可用的 embedding 模型配置，请在「设置 → 模型」中配置向量模型");
        return null;
    }

    /**
     * 安全获取模型配置
     */
    private ModelConfigEntity safeGetModel(Long id) {
        try {
            return modelConfigService.getModel(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断模型是否可用（启用 + embedding 类型 + Provider 已配置）
     */
    private boolean isUsable(ModelConfigEntity model) {
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                || !"embedding".equals(model.getModelType())) {
            return false;
        }
        if (modelProviderService == null) {
            return true;
        }
        try {
            return modelProviderService.isProviderConfigured(model.getProvider());
        } catch (Exception e) {
            log.debug("Provider 检查失败，模型 {}: {}", model.getId(), e.getMessage());
            return true;
        }
    }

    /**
     * 读取系统默认 embedding 模型 ID
     */
    private Long readSystemDefaultEmbeddingId() {
        try {
            String value = systemSettingService.getString(
                    WikiEmbeddingService.SYSTEM_SETTING_DEFAULT_EMBEDDING_ID, null);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            log.debug("读取系统默认 embedding 模型 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断语义检索是否可用
     */
    private boolean isSemanticAvailable() {
        if (wikiEmbeddingService == null) {
            return false;
        }
        try {
            return wikiEmbeddingService.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 关键词检索（MySQL 降级方案）
     * <p>
     * 在 SemanticModelEntity 的多个字段中模糊匹配，聚合到表级别
     */
    private Map<String, Double> keywordSearch(Long datasourceId, String query) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (!StringUtils.hasText(query)) {
            return scores;
        }

        String likePattern = "%" + query + "%";
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        wrapper.and(w -> {
            for (String field : DataAgentConstants.SCHEMA_KEYWORD_SEARCH_FIELDS) {
                switch (field) {
                    case "table_name" -> w.or().like(SemanticModelEntity::getTableName, likePattern);
                    case "column_name" -> w.or().like(SemanticModelEntity::getColumnName, likePattern);
                    case "business_name" -> w.or().like(SemanticModelEntity::getBusinessName, likePattern);
                    case "business_description" -> w.or().like(SemanticModelEntity::getBusinessDescription, likePattern);
                    case "synonyms" -> w.or().like(SemanticModelEntity::getSynonyms, likePattern);
                    case "column_comment" -> w.or().like(SemanticModelEntity::getColumnComment, likePattern);
                    default -> { /* 忽略未知字段 */ }
                }
            }
        });

        List<SemanticModelEntity> matched = semanticModelMapper.selectList(wrapper);

        /* 按表名聚合，命中字段数越多分数越高 */
        Map<String, Long> tableHitCounts = matched.stream()
                .collect(Collectors.groupingBy(SemanticModelEntity::getTableName, Collectors.counting()));

        for (Map.Entry<String, Long> entry : tableHitCounts.entrySet()) {
            scores.put(entry.getKey(), entry.getValue().doubleValue());
        }

        /* 同时在表名中直接匹配 */
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        tableWrapper.eq(DatasourceTableEntity::getDeleted, 0);
        tableWrapper.like(DatasourceTableEntity::getTableName, likePattern);
        List<DatasourceTableEntity> tableMatches = datasourceTableMapper.selectList(tableWrapper);
        for (DatasourceTableEntity table : tableMatches) {
            scores.merge(table.getTableName(), 5.0, Double::sum);
        }

        return scores;
    }

    /**
     * 向量语义检索（MySQL 降级方案）
     * <p>
     * 将查询文本向量化，与数据源下所有 Schema 嵌入计算余弦相似度
     */
    private Map<String, Double> semanticSearch(Long datasourceId, String query, double threshold) {
        Map<String, Double> scores = new LinkedHashMap<>();

        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        if (embeddingModel == null) {
            return scores;
        }

        /* 将查询文本向量化 */
        float[] queryVector;
        try {
            EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
            queryVector = resp.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.warn("查询文本向量化失败: {}", e.getMessage());
            return scores;
        }

        if (queryVector == null || queryVector.length == 0) {
            return scores;
        }

        /* 查询该数据源的所有嵌入记录 */
        LambdaQueryWrapper<SchemaEmbeddingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchemaEmbeddingEntity::getDatasourceId, datasourceId);
        wrapper.isNotNull(SchemaEmbeddingEntity::getEmbedding);
        List<SchemaEmbeddingEntity> embeddings = schemaEmbeddingMapper.selectList(wrapper);

        /* 计算余弦相似度 */
        for (SchemaEmbeddingEntity entity : embeddings) {
            if (entity.getEmbedding() == null || entity.getEmbedding().length == 0) {
                continue;
            }
            try {
                float[] tableVector = WikiEmbeddingService.bytesToFloats(entity.getEmbedding());
                float similarity = WikiEmbeddingService.cosine(queryVector, tableVector);
                if (similarity >= threshold) {
                    scores.put(entity.getTableName(), (double) similarity);
                }
            } catch (Exception e) {
                log.debug("计算表 [{}] 相似度失败: {}", entity.getTableName(), e.getMessage());
            }
        }

        return scores;
    }

    /**
     * RRF 融合关键词和语义检索结果
     * <p>
     * score = Σ 1/(k + rank_i)，k = SCHEMA_SEARCH_RRF_K
     */
    private Map<String, Double> rrfFuse(Map<String, Double> keywordScores, Map<String, Double> semanticScores) {
        Map<String, Double> fusedScores = new HashMap<>();

        /* 关键词检索排名 */
        List<Map.Entry<String, Double>> keywordSorted = keywordScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        /* 语义检索排名 */
        List<Map.Entry<String, Double>> semanticSorted = semanticScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        /* 收集所有表名 */
        Set<String> allTables = new HashSet<>();
        for (Map.Entry<String, Double> entry : keywordSorted) {
            allTables.add(entry.getKey());
        }
        for (Map.Entry<String, Double> entry : semanticSorted) {
            allTables.add(entry.getKey());
        }

        /* 构建排名映射 */
        Map<String, Integer> keywordRanks = new HashMap<>();
        for (int i = 0; i < keywordSorted.size(); i++) {
            keywordRanks.put(keywordSorted.get(i).getKey(), i + 1);
        }

        Map<String, Integer> semanticRanks = new HashMap<>();
        for (int i = 0; i < semanticSorted.size(); i++) {
            semanticRanks.put(semanticSorted.get(i).getKey(), i + 1);
        }

        /* RRF 融合计算 */
        int k = DataAgentConstants.SCHEMA_SEARCH_RRF_K;
        for (String tableName : allTables) {
            double score = 0.0;
            if (keywordRanks.containsKey(tableName)) {
                score += 1.0 / (k + keywordRanks.get(tableName));
            }
            if (semanticRanks.containsKey(tableName)) {
                score += 1.0 / (k + semanticRanks.get(tableName));
            }
            fusedScores.put(tableName, score);
        }

        return fusedScores;
    }

    /**
     * 查询已有嵌入记录
     */
    private SchemaEmbeddingEntity getExistingEmbedding(Long datasourceId, String tableName) {
        LambdaQueryWrapper<SchemaEmbeddingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchemaEmbeddingEntity::getDatasourceId, datasourceId);
        wrapper.eq(SchemaEmbeddingEntity::getTableName, tableName);
        wrapper.last("LIMIT 1");
        return schemaEmbeddingMapper.selectOne(wrapper);
    }

    /**
     * 查询数据源下所有未删除的表
     */
    private List<DatasourceTableEntity> listActiveTables(Long datasourceId) {
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceTableEntity::getDeleted, 0);
        wrapper.orderByAsc(DatasourceTableEntity::getTableName);
        return datasourceTableMapper.selectList(wrapper);
    }

    /**
     * 获取表注释
     */
    private String getTableComment(Long datasourceId, String tableName) {
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceTableEntity::getTableName, tableName);
        wrapper.eq(DatasourceTableEntity::getDeleted, 0);
        wrapper.last("LIMIT 1");
        DatasourceTableEntity table = datasourceTableMapper.selectOne(wrapper);
        return table != null ? table.getTableComment() : "";
    }

    /**
     * 获取表实体
     */
    private DatasourceTableEntity getTableEntity(Long datasourceId, String tableName) {
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceTableEntity::getTableName, tableName);
        wrapper.eq(DatasourceTableEntity::getDeleted, 0);
        wrapper.last("LIMIT 1");
        return datasourceTableMapper.selectOne(wrapper);
    }

    /**
     * 查询表的列信息
     */
    private List<DatasourceColumnEntity> listColumns(Long datasourceId, DatasourceTableEntity tableEntity) {
        if (tableEntity == null) {
            return List.of();
        }
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableEntity.getId());
        wrapper.eq(DatasourceColumnEntity::getDeleted, 0);
        wrapper.orderByAsc(DatasourceColumnEntity::getOrdinalPosition);
        return datasourceColumnMapper.selectList(wrapper);
    }

    /**
     * 查询表的所有启用语义模型，以 columnName 为 key
     */
    private Map<String, SemanticModelEntity> listEnabledSemanticModels(Long datasourceId, String tableName) {
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getTableName, tableName);
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        List<SemanticModelEntity> list = semanticModelMapper.selectList(wrapper);
        return list.stream()
                .collect(Collectors.toMap(SemanticModelEntity::getColumnName, e -> e, (a, b) -> a));
    }

    /**
     * 构建单列的嵌入文本片段
     * <p>
     * 格式: "列名(业务别名) [类型] - 描述"
     * 优先使用语义模型中的 businessName/businessDescription，没有则回退到 columnComment
     */
    private String buildColumnPart(DatasourceColumnEntity column, SemanticModelEntity semantic) {
        StringBuilder sb = new StringBuilder();
        sb.append(column.getColumnName());

        /* 业务别名：优先语义模型，无则留空 */
        String businessName = null;
        if (semantic != null && StringUtils.hasText(semantic.getBusinessName())) {
            businessName = semantic.getBusinessName();
        }
        if (StringUtils.hasText(businessName)) {
            sb.append("(").append(businessName).append(")");
        }

        /* 数据类型 */
        String dataType = column.getDataType();
        if (semantic != null && StringUtils.hasText(semantic.getDataType())) {
            dataType = semantic.getDataType();
        }
        if (StringUtils.hasText(dataType)) {
            sb.append(" [").append(dataType).append("]");
        }

        /* 描述：优先语义模型 businessDescription，回退到 columnComment */
        String description = null;
        if (semantic != null && StringUtils.hasText(semantic.getBusinessDescription())) {
            description = semantic.getBusinessDescription();
        } else if (semantic != null && StringUtils.hasText(semantic.getColumnComment())) {
            description = semantic.getColumnComment();
        } else if (StringUtils.hasText(column.getColumnComment())) {
            description = column.getColumnComment();
        }
        if (StringUtils.hasText(description)) {
            sb.append(" - ").append(description);
        }

        return sb.toString();
    }

    /**
     * 查询表的语义模型 VO 列表
     */
    private List<SemanticModelVO> listSemanticModelVOs(Long datasourceId, String tableName) {
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getTableName, tableName);
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        List<SemanticModelEntity> entities = semanticModelMapper.selectList(wrapper);
        return entities.stream().map(this::toSemanticModelVO).collect(Collectors.toList());
    }

    /**
     * 查询命中表相关的逻辑外键关系
     */
    private List<LogicalRelationVO> listRelatedRelations(Long datasourceId, Set<String> tableNames) {
        if (tableNames.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        wrapper.eq(LogicalRelationEntity::getDeleted, 0);
        wrapper.and(w -> {
            w.in(LogicalRelationEntity::getSourceTableName, tableNames)
                    .or().in(LogicalRelationEntity::getTargetTableName, tableNames);
        });
        List<LogicalRelationEntity> entities = logicalRelationMapper.selectList(wrapper);
        return entities.stream().map(this::toLogicalRelationVO).collect(Collectors.toList());
    }

    /**
     * SemanticModelEntity 转 SemanticModelVO
     */
    private SemanticModelVO toSemanticModelVO(SemanticModelEntity entity) {
        SemanticModelVO vo = new SemanticModelVO();
        vo.setId(entity.getId());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setTableName(entity.getTableName());
        vo.setColumnName(entity.getColumnName());
        vo.setBusinessName(entity.getBusinessName());
        vo.setBusinessDescription(entity.getBusinessDescription());
        vo.setSynonyms(entity.getSynonyms());
        vo.setDataType(entity.getDataType());
        vo.setColumnComment(entity.getColumnComment());
        vo.setExampleValues(entity.getExampleValues());
        vo.setEnumValues(entity.getEnumValues());
        vo.setUnit(entity.getUnit());
        vo.setValueRange(entity.getValueRange());
        vo.setStatus(entity.getStatus());
        vo.setPromptInfo(entity.getPromptInfo());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

    /**
     * LogicalRelationEntity 转 LogicalRelationVO
     */
    private LogicalRelationVO toLogicalRelationVO(LogicalRelationEntity entity) {
        LogicalRelationVO vo = new LogicalRelationVO();
        vo.setId(entity.getId());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setSourceTableName(entity.getSourceTableName());
        vo.setSourceColumnName(entity.getSourceColumnName());
        vo.setTargetTableName(entity.getTargetTableName());
        vo.setTargetColumnName(entity.getTargetColumnName());
        vo.setRelationType(entity.getRelationType());
        vo.setDescription(entity.getDescription());
        vo.setPromptInfo(entity.getPromptInfo());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }
}
