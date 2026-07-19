package vip.mate.dataagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermSearchResult.TermHit;
import vip.mate.dataagent.model.BusinessTermEntity;
import vip.mate.dataagent.repository.BusinessTermMapper;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.service.WikiEmbeddingService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务术语 Elasticsearch 检索服务实现
 * <p>
 * 基于 Elasticsearch 8.x Java Client 实现术语的
 * 关键词检索（multi_match）、向量语义检索（kNN）和混合检索（RRF 融合）。
 * ES 不可用时优雅降级为 MySQL LIKE 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessTermEsServiceImpl implements BusinessTermEsService {

    private final BusinessTermMapper businessTermMapper;
    private final ModelConfigService modelConfigService;

    @Autowired(required = false)
    private ElasticsearchClient esClient;

    /** 可选依赖：Embedding 模型工厂，缺失时向量检索降级 */
    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    /** 向量维度缓存 */
    private volatile int cachedVectorDimension = -1;

    /** 索引是否已初始化 */
    private volatile boolean indexInitialized = false;

    /**
     * 获取可用的 ES 客户端
     */
    private ElasticsearchClient getAvailableClient() {
        if (esClient == null) {
            return null;
        }
        try {
            esClient.ping();
            return esClient;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void ensureIndex(int vectorDimension) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.warn("Elasticsearch 客户端不可用，跳过术语索引创建");
            return;
        }

        String indexName = DataAgentConstants.BUSINESS_TERM_ES_INDEX;
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                cachedVectorDimension = vectorDimension;
                indexInitialized = true;
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("tenantCode", p -> p.keyword(k -> k))
                            .properties("termName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("synonyms", p -> p.keyword(k -> k))
                            .properties("description", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("calculationFormula", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("dataCaliber", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("dataSource", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.keyword(k -> k))
                            .properties("businessRule", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("relatedTerms", p -> p.keyword(k -> k))
                            .properties("example", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("securityLevel", p -> p.keyword(k -> k))
                            .properties("category", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.info("Elasticsearch 术语索引 [{}] 创建成功，向量维度: {}", indexName, dims);
        } catch (Exception e) {
            log.warn("创建术语索引失败(ik)，尝试标准分词器: {}", e.getMessage());
            tryCreateIndexWithStandardAnalyzer(client, indexName, vectorDimension);
        }

        cachedVectorDimension = vectorDimension;
        indexInitialized = true;
    }

    private void tryCreateIndexWithStandardAnalyzer(ElasticsearchClient client, String indexName, int vectorDimension) {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("tenantCode", p -> p.keyword(k -> k))
                            .properties("termName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("synonyms", p -> p.keyword(k -> k))
                            .properties("description", p -> p.text(t -> t))
                            .properties("calculationFormula", p -> p.text(t -> t))
                            .properties("dataCaliber", p -> p.text(t -> t))
                            .properties("dataSource", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.keyword(k -> k))
                            .properties("businessRule", p -> p.text(t -> t))
                            .properties("relatedTerms", p -> p.keyword(k -> k))
                            .properties("example", p -> p.text(t -> t))
                            .properties("securityLevel", p -> p.keyword(k -> k))
                            .properties("category", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.info("Elasticsearch 术语索引 [{}] 创建成功（标准分词器），向量维度: {}", indexName, dims);
        } catch (Exception ex) {
            log.error("Elasticsearch 术语索引创建失败（标准分词器降级）: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public void indexTerm(BusinessTermEntity entity) {
        indexTerms(List.of(entity));
    }

    @Override
    public void indexTerms(List<BusinessTermEntity> entities) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null || entities == null || entities.isEmpty()) {
            return;
        }

        ensureIndexIfNeeded();

        String indexName = DataAgentConstants.BUSINESS_TERM_ES_INDEX;
        int batchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;

        for (int i = 0; i < entities.size(); i += batchSize) {
            List<BusinessTermEntity> batch = entities.subList(i, Math.min(i + batchSize, entities.size()));
            try {
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
                for (BusinessTermEntity entity : batch) {
                    String docId = entity.getTenantCode() + "_" + entity.getTermName();
                    Map<String, Object> doc = buildTermDocument(entity);
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(docId)
                                    .document(doc)
                            )
                    );
                }
                BulkResponse bulkResponse = client.bulk(bulkBuilder.build());
                if (bulkResponse.errors()) {
                    long failCount = bulkResponse.items().stream().filter(item -> item.error() != null).count();
                    log.warn("ES Bulk 写入术语部分失败，失败数: {}", failCount);
                }
            } catch (IOException e) {
                log.warn("ES Bulk 写入术语失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
        log.debug("ES Bulk 索引写入术语完成，数量: {}", entities.size());
    }

    @Override
    public void deleteByTenantCode(String tenantCode) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            return;
        }

        try {
            client.deleteByQuery(d -> d
                    .index(DataAgentConstants.BUSINESS_TERM_ES_INDEX)
                    .query(q -> q.term(t -> t.field("tenantCode").value(tenantCode)))
            );
            log.info("ES 术语索引删除完成，租户: {}", tenantCode);
        } catch (IOException e) {
            log.warn("ES 术语索引删除失败，租户: {} - {}", tenantCode, e.getMessage());
        }
    }

    @Override
    public void deleteTerm(BusinessTermEntity entity) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            return;
        }

        String docId = entity.getTenantCode() + "_" + entity.getTermName();
        try {
            client.delete(d -> d
                    .index(DataAgentConstants.BUSINESS_TERM_ES_INDEX)
                    .id(docId)
            );
        } catch (IOException e) {
            log.warn("ES 术语文档删除失败: {}", e.getMessage());
        }
    }

    @Override
    public BusinessTermSearchResult hybridSearch(String query, int topK, double similarityThreshold) {
        long startTime = System.currentTimeMillis();
        BusinessTermSearchResult result = new BusinessTermSearchResult();
        result.setQuery(query);
        // 跨所有租户检索术语，结果不再绑定具体业务域
        result.setTenantCode(null);

        if (!StringUtils.hasText(query)) {
            result.setTermHits(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.debug("ES 不可用，降级为 MySQL LIKE 查询");
            return fallbackMySqlSearch(null, query, topK, startTime);
        }

        ensureIndexIfNeeded();

        List<TermHit> termHits = esSearchTerms(client, null, query, topK, similarityThreshold);

        result.setTermHits(termHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== ES 检索 ====================

    private List<TermHit> esSearchTerms(ElasticsearchClient client, String tenantCode, String query, int topK, double threshold) {
        String indexName = DataAgentConstants.BUSINESS_TERM_ES_INDEX;
        try {
            boolean hasVector = hasTermEmbeddings(tenantCode);
            List<Float> queryVector = hasVector ? getQueryVector(query) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：multi_match(cross_fields + 字段权重) + kNN + RRF
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(topK)
                                .query(q -> q.bool(b -> {
                                    if (tenantCode != null) {
                                        b.filter(f -> f.term(t -> t.field("tenantCode").value(tenantCode)));
                                    }
                                    b.should(sh -> sh.multiMatch(mm -> mm
                                            .fields("termName^3", "termName.keyword^3", "synonyms^2", "description^1", "calculationFormula^1", "dataCaliber^1", "businessRule^1", "category^1", DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^1")
                                            .type(TextQueryType.CrossFields)
                                            .query(query)));
                                    b.minimumShouldMatch("1");
                                    return b;
                                }))
                                .knn(knn -> {
                                    knn.field(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD)
                                            .queryVector(queryVector)
                                            .k(topK)
                                            .numCandidates(DataAgentConstants.ES_KNN_NUM_CANDIDATES);
                                    if (tenantCode != null) {
                                        knn.filter(f -> f.term(t -> t.field("tenantCode").value(tenantCode)));
                                    }
                                    return knn;
                                })
                                .rank(r -> r.rrf(rrf -> rrf.rankConstant((long) DataAgentConstants.SCHEMA_SEARCH_RRF_K))),
                        Map.class
                );
                // RRF 分数尺度远小于 BM25，不适用 BM25 的 threshold，直接传 0 避免误过滤
                return extractTermHits(response, "hybrid", 0);
            } else {
                // 仅关键词检索
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(topK)
                                .query(q -> q.bool(b -> {
                                    if (tenantCode != null) {
                                        b.filter(f -> f.term(t -> t.field("tenantCode").value(tenantCode)));
                                    }
                                    b.must(m -> m.multiMatch(mm -> mm
                                            .fields("termName^3", "termName.keyword^3", "synonyms^2", "description^1", "calculationFormula^1", "dataCaliber^1", "businessRule^1", "category^1", DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^1")
                                            .type(TextQueryType.CrossFields)
                                            .query(query)));
                                    return b;
                                })),
                        Map.class
                );
                return extractTermHits(response, "keyword", 0);
            }
        } catch (Exception e) {
            log.warn("ES 术语检索失败，降级为 MySQL: {}", e.getMessage());
            return fallbackMySqlSearchTerms(tenantCode, query, topK);
        }
    }

    // ==================== ES 结果提取 ====================

    @SuppressWarnings("unchecked")
    private List<TermHit> extractTermHits(SearchResponse<Map> response, String matchSource, double threshold) {
        List<TermHit> hits = new ArrayList<>();
        if (response == null || response.hits() == null) {
            return hits;
        }

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }

            double score = hit.score() != null ? hit.score() : 0.0;
            if (threshold > 0 && score < threshold) {
                continue;
            }

            TermHit th = new TermHit();
            th.setTermName(getString(source, "termName"));
            th.setSynonyms(getString(source, "synonyms"));
            th.setDescription(getString(source, "description"));
            th.setCalculationFormula(getString(source, "calculationFormula"));
            th.setDataCaliber(getString(source, "dataCaliber"));
            th.setBusinessRule(getString(source, "businessRule"));
            th.setCategory(getString(source, "category"));
            th.setScore(score);
            th.setMatchSource(matchSource);
            hits.add(th);
        }
        return hits;
    }

    // ==================== MySQL 降级检索 ====================

    private BusinessTermSearchResult fallbackMySqlSearch(String tenantCode, String query, int topK, long startTime) {
        BusinessTermSearchResult result = new BusinessTermSearchResult();
        result.setQuery(query);
        result.setTenantCode(tenantCode);

        List<TermHit> termHits = fallbackMySqlSearchTerms(tenantCode, query, topK);

        result.setTermHits(termHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private List<TermHit> fallbackMySqlSearchTerms(String tenantCode, String query, int topK) {
        String likePattern = "%" + query + "%";
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantCode != null) {
            wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        }
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
        wrapper.and(w -> w
                .like(BusinessTermEntity::getTermName, likePattern)
                .or().like(BusinessTermEntity::getSynonyms, likePattern)
                .or().like(BusinessTermEntity::getDescription, likePattern)
                .or().like(BusinessTermEntity::getCalculationFormula, likePattern)
                .or().like(BusinessTermEntity::getDataCaliber, likePattern)
                .or().like(BusinessTermEntity::getBusinessRule, likePattern)
                .or().like(BusinessTermEntity::getCategory, likePattern)
        );
        wrapper.last("LIMIT " + topK);

        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);

        // 补充父术语名称
        Map<Long, String> parentNameMap = buildParentNameMap(entities);

        return entities.stream().map(e -> {
            TermHit th = new TermHit();
            th.setTermName(e.getTermName());
            th.setSynonyms(e.getSynonyms());
            th.setDescription(e.getDescription());
            th.setCalculationFormula(e.getCalculationFormula());
            th.setDataCaliber(e.getDataCaliber());
            th.setBusinessRule(e.getBusinessRule());
            th.setCategory(e.getCategory());
            th.setParentTermName(e.getParentId() != null ? parentNameMap.get(e.getParentId()) : null);
            th.setScore(1.0);
            th.setMatchSource("keyword");
            return th;
        }).collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> buildTermDocument(BusinessTermEntity entity) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tenantCode", entity.getTenantCode());
        doc.put("termName", entity.getTermName());
        doc.put("synonyms", splitToList(entity.getSynonyms()));
        doc.put("description", entity.getDescription());
        doc.put("calculationFormula", entity.getCalculationFormula());
        doc.put("dataCaliber", entity.getDataCaliber());
        doc.put("dataSource", entity.getDataSource());
        doc.put("owner", entity.getOwner());
        doc.put("businessRule", entity.getBusinessRule());
        doc.put("relatedTerms", splitToList(entity.getRelatedTerms()));
        doc.put("example", entity.getExample());
        doc.put("securityLevel", entity.getSecurityLevel());
        doc.put("category", entity.getCategory());
        doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, entity.getEmbeddingText());
        if (entity.getEmbedding() != null && entity.getEmbedding().length > 0) {
            doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, bytesToFloatList(entity.getEmbedding()));
        }
        return doc;
    }

    private void ensureIndexIfNeeded() {
        if (indexInitialized && cachedVectorDimension > 0) {
            return;
        }
        int dim = cachedVectorDimension > 0 ? cachedVectorDimension : DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION;
        ensureIndex(dim);
    }

    private boolean hasTermEmbeddings(String tenantCode) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantCode != null) {
            wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        }
        wrapper.isNotNull(BusinessTermEntity::getEmbedding);
        wrapper.last("LIMIT 1");
        return businessTermMapper.selectCount(wrapper) > 0;
    }

    /**
     * 生成查询向量
     */
    private List<Float> getQueryVector(String query) {
        if (embeddingModelFactory == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            EmbeddingModel embeddingModel = resolveEmbeddingModel();
            if (embeddingModel == null) {
                return List.of();
            }
            EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
            float[] vector = resp.getResults().get(0).getOutput();
            if (vector == null || vector.length == 0) {
                return List.of();
            }
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
        } catch (Exception e) {
            log.warn("生成查询向量失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析可用的 EmbeddingModel 实例
     */
    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModelFactory == null) {
            return null;
        }
        try {
            ModelConfigEntity config = resolveEmbeddingModelConfig();
            if (config == null) {
                return null;
            }
            return embeddingModelFactory.build(config);
        } catch (Exception e) {
            log.warn("构建 EmbeddingModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析应使用的 Embedding 模型配置
     */
    private ModelConfigEntity resolveEmbeddingModelConfig() {
        ModelConfigEntity marked = modelConfigService.listEnabledModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled())
                        && "embedding".equals(m.getModelType())
                        && Boolean.TRUE.equals(m.getIsDefault()))
                .findFirst().orElse(null);
        if (marked != null) {
            return marked;
        }
        return modelConfigService.findFirstEnabledEmbedding();
    }

    private Map<Long, String> buildParentNameMap(List<BusinessTermEntity> entities) {
        List<Long> parentIds = entities.stream()
                .map(BusinessTermEntity::getParentId)
                .filter(pid -> pid != null && pid > 0)
                .distinct()
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return new HashMap<>();
        }
        List<BusinessTermEntity> parents = businessTermMapper.selectBatchIds(parentIds);
        return parents.stream()
                .collect(Collectors.toMap(BusinessTermEntity::getId, BusinessTermEntity::getTermName));
    }

    private List<String> splitToList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<Float> bytesToFloatList(byte[] bytes) {
        float[] floats = WikiEmbeddingService.bytesToFloats(bytes);
        List<Float> list = new ArrayList<>(floats.length);
        for (float f : floats) {
            list.add(f);
        }
        return list;
    }

    private String getString(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return val != null ? val.toString() : null;
    }
}
