package vip.mate.dataagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
import vip.mate.dataagent.dto.BusinessTermRef;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermSearchResult.TermHit;
import vip.mate.dataagent.model.BusinessTermEntity;
import vip.mate.dataagent.repository.BusinessTermMapper;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.dataagent.support.NameMatchSupport;
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
            log.error("Elasticsearch 客户端不可用，跳过术语索引创建");
            return;
        }

        String indexName = DataAgentConstants.BUSINESS_TERM_ES_INDEX;
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                // 存量索引补充 status 字段映射（putMapping 幂等合并），保证状态过滤对新旧文档语义一致
                try {
                    client.indices().putMapping(p -> p
                            .index(indexName)
                            .properties("status", pp -> pp.keyword(k -> k)));
                } catch (Exception e) {
                    log.warn("术语索引 [{}] 补充 status 字段映射失败: {}", indexName, e.getMessage());
                }
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
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("synonyms", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("description", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("calculationFormula", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("dataCaliber", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("dataSource", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.keyword(k -> k))
                            .properties("businessRule", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("relatedTerms", p -> p.keyword(k -> k))
                            .properties("example", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("securityLevel", p -> p.keyword(k -> k))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("category", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("relatedMetricNames", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("relatedDimensionNames", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.info("Elasticsearch 术语索引 [{}] 创建成功，向量维度: {}", indexName, dims);
        } catch (Exception e) {
            log.error("创建术语索引失败(ik)，尝试标准分词器: {}", e.getMessage());
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
                            .properties("synonyms", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("description", p -> p.text(t -> t))
                            .properties("calculationFormula", p -> p.text(t -> t))
                            .properties("dataCaliber", p -> p.text(t -> t))
                            .properties("dataSource", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.keyword(k -> k))
                            .properties("businessRule", p -> p.text(t -> t))
                            .properties("relatedTerms", p -> p.keyword(k -> k))
                            .properties("example", p -> p.text(t -> t))
                            .properties("securityLevel", p -> p.keyword(k -> k))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("category", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("relatedMetricNames", p -> p.text(t -> t))
                            .properties("relatedDimensionNames", p -> p.text(t -> t))
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
                    log.error("ES Bulk 写入术语部分失败，失败数: {}", failCount);
                }
            } catch (IOException e) {
                log.error("ES Bulk 写入术语失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
        log.info("ES Bulk 索引写入术语完成，数量: {}", entities.size());
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
            log.error("ES 术语索引删除失败，租户: {} - {}", tenantCode, e.getMessage());
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
            log.error("ES 术语文档删除失败: {}", e.getMessage());
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
            log.error("ES 不可用，降级为 MySQL LIKE 查询");
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
        // 放大候选池，融合/排序后再由调用方截断到 topK
        int pool = retrievalPoolSize(topK);
        Query keywordQuery = buildTermKeywordQuery(tenantCode, query);
        try {
            boolean hasVector = hasTermEmbeddings(tenantCode);
            List<Float> queryVector = hasVector ? getQueryVector(query) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：分别执行关键词查询和 kNN 查询，应用层 RRF 融合
                // （ES 内置 RRF 需要 Platinum 许可证，Basic 许可证不支持）

                // 1. 关键词查询
                SearchResponse<Map> keywordResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );

                // 2. kNN 向量查询
                SearchResponse<Map> knnResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .knn(knn -> {
                                    knn.field(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD)
                                            .queryVector(queryVector)
                                            .k(pool)
                                            .numCandidates(Math.max(DataAgentConstants.ES_KNN_NUM_CANDIDATES, pool));
                                    if (tenantCode != null) {
                                        knn.filter(f -> f.term(t -> t.field("tenantCode").value(tenantCode)));
                                    }
                                    // 启用状态过滤（与关键词路一致）：剔除停用术语，存量文档（无 status 字段）保留
                                    knn.filter(f -> f.bool(bb -> bb
                                            .mustNot(mn -> mn.term(t -> t.field("status")
                                                    .value(String.valueOf(DataAgentConstants.BUSINESS_TERM_STATUS_DISABLED))))));
                                    return knn;
                                }),
                        Map.class
                );

                // 3. 应用层 RRF 融合（按相关性阈值过滤，杜绝金融属性词等宽泛词的低相关噪声）
                return rrfMergeTermHits(keywordResponse, knnResponse, threshold);
            } else {
                // 仅关键词检索
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );
                return extractTermHits(response, "keyword", threshold);
            }
        } catch (Exception e) {
            log.error("ES 术语检索失败，降级为 MySQL: {}", e.getMessage());
            return fallbackMySqlSearchTerms(tenantCode, query, topK);
        }
    }

    /**
     * 构建术语关键词查询。
     * <p>
     * 查询结构：
     * <ul>
     *   <li>filter: tenantCode 精确过滤（可选）</li>
     *   <li>must: multiMatch（BestFields + tieBreaker）只作用于核心语义字段
     *       termName/synonyms，保证"名称/同义词命中"强制召回；描述、口径、规则等
     *       外围字段不再参与 must，避免输入金融属性词（如"金融""利率"）时，
     *       仅描述里碰巧提及该词的弱相关术语被强制拉入结果产生噪声</li>
     *   <li>should: 外围字段与 .keyword 精确匹配仅提权，命中加分、不命中不影响 must 召回</li>
     * </ul>
     * <p>
     * .keyword 字段使用 keyword analyzer（不分词），与 IK 分词器不同组，
     * 混入 CrossFields 会因分析器分组割裂扭曲打分；改为独立 should term 提权。
     */
    private Query buildTermKeywordQuery(String tenantCode, String query) {
        return Query.of(q -> q.bool(b -> {
            if (tenantCode != null) {
                b.filter(f -> f.term(t -> t.field("tenantCode").value(tenantCode)));
            }
            // 启用状态过滤：显式剔除停用术语（status="0"）；存量文档（无 status 字段）与启用术语均保留
            b.filter(f -> f.bool(bb -> bb
                    .mustNot(mn -> mn.term(t -> t.field("status")
                            .value(String.valueOf(DataAgentConstants.BUSINESS_TERM_STATUS_DISABLED))))));
            // BestFields：取单个最高分字段，tieBreaker 让次高分字段也贡献部分分数
            // 核心语义字段走 must：名称/同义词强制召回，杜绝描述命中弱相关噪声
            b.must(m -> m.multiMatch(mm -> mm
                    .fields("termName^3", "termName.ikmax^2", "synonyms^2", "synonyms.ikmax^1")
                    .type(TextQueryType.BestFields)
                    .tieBreaker(0.3)
                    .query(query)));
            // 外围字段仅提权（低权重）：描述、口径、规则里碰巧提及查询词的术语
            // 不再被强制召回，命中仅适度加分、不命中不影响召回
            b.should(sh -> sh.multiMatch(mm -> mm
                    .fields("description^0.5", "calculationFormula^0.3", "dataCaliber^0.3",
                            "businessRule^0.3", "category^0.5",
                            DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^0.3")
                    .type(TextQueryType.BestFields)
                    .tieBreaker(0.3)
                    .query(query)));
            // .keyword 精确匹配提权：命中加分，不命中不影响 must 召回
            b.should(sh -> sh.term(t -> t.field("termName.keyword").value(query).boost(5.0f)));
            b.should(sh -> sh.term(t -> t.field("synonyms.keyword").value(query).boost(2.0f)));
            b.should(sh -> sh.term(t -> t.field("category.keyword").value(query).boost(1.0f)));
            return b;
        }));
    }

    /**
     * 计算每路（关键词 / 向量）在 RRF 融合前的候选召回数量。
     * <p>
     * 混合检索必须先各路放大召回、融合后再截断到 topK；否则两路都排在 topK 之外的
     * 真实命中会永远进不了 RRF，表现为"数据存在却检索不出"。
     */
    private int retrievalPoolSize(int topK) {
        int base = Math.max(topK, 1);
        return Math.min(base * DataAgentConstants.ALOUDATA_SEARCH_RETRIEVAL_POOL_FACTOR,
                DataAgentConstants.ALOUDATA_SEARCH_MAX_RETRIEVAL_POOL);
    }

    /**
     * 应用层 RRF 融合关键词和向量检索结果
     * <p>
     * RRF 公式: score = Σ 1/(k + rank_i)，k 为 rankConstant
     * 双通道同时命中的结果得分更高，自然排在前面
     * <p>
     * 融合后按归一化相关分与 threshold 过滤：归一化分低于阈值的低相关候选直接丢弃，
     * 防止金融属性词等宽泛词双通道命中导致噪声术语 RRF 分被抬高后混入结果。
     *
     * @param keywordResponse 关键词检索响应
     * @param knnResponse     向量检索响应
     * @param threshold       相关性过滤阈值（<=0 表示不过滤）
     */
    private List<TermHit> rrfMergeTermHits(SearchResponse<Map> keywordResponse,
                                           SearchResponse<Map> knnResponse,
                                           double threshold) {
        int rankConstant = DataAgentConstants.SCHEMA_SEARCH_RRF_K;

        // 关键词结果按排名计算 RRF 分数
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, TermHit> hitMap = new LinkedHashMap<>();

        if (keywordResponse != null && keywordResponse.hits() != null) {
            List<Hit<Map>> keywordHits = keywordResponse.hits().hits();
            for (int i = 0; i < keywordHits.size(); i++) {
                Hit<Map> hit = keywordHits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                TermHit th = buildTermHit(hit.source(), hit.score(), "keyword");
                if (th != null) {
                    hitMap.putIfAbsent(id, th);
                }
            }
        }

        // 向量结果按排名计算 RRF 分数
        if (knnResponse != null && knnResponse.hits() != null) {
            List<Hit<Map>> knnHits = knnResponse.hits().hits();
            for (int i = 0; i < knnHits.size(); i++) {
                Hit<Map> hit = knnHits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                if (!hitMap.containsKey(id)) {
                    TermHit th = buildTermHit(hit.source(), hit.score(), "vector");
                    if (th != null) {
                        hitMap.put(id, th);
                    }
                }
            }
        }

        // 按 RRF 分数降序排序
        // RRF 原始分为 Σ1/(k+rank)，量级仅 0.0x，与展示/阈值语义（0~1）不可比；
        // 按最大值归一化到 (0,1]，使 top 命中≈1.0，展示与相似度阈值可解释。
        double maxScore = scoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    TermHit th = hitMap.get(entry.getKey());
                    if (th != null) {
                        th.setScore(maxScore > 0 ? entry.getValue() / maxScore : entry.getValue());
                        th.setMatchSource("hybrid");
                    }
                    return th;
                })
                .filter(Objects::nonNull)
                // 相关性阈值闸门：归一化分低于阈值的弱相关候选直接丢弃
                .filter(th -> threshold <= 0 || th.getScore() >= threshold)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private TermHit buildTermHit(Map<String, Object> source, Double score, String matchSource) {
        if (source == null) {
            return null;
        }
        TermHit th = new TermHit();
        th.setTermName(getString(source, "termName"));
        th.setSynonyms(getJoinedString(source, "synonyms"));
        th.setDescription(getString(source, "description"));
        th.setCalculationFormula(getString(source, "calculationFormula"));
        th.setDataCaliber(getString(source, "dataCaliber"));
        th.setBusinessRule(getString(source, "businessRule"));
        th.setCategory(getString(source, "category"));
        th.setRelatedMetricNames(getStringList(source, "relatedMetricNames"));
        th.setRelatedDimensionNames(getStringList(source, "relatedDimensionNames"));
        th.setScore(score != null ? score : 0.0);
        th.setMatchSource(matchSource);
        return th;
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

            TermHit th = new TermHit();
            th.setTermName(getString(source, "termName"));
            th.setSynonyms(getJoinedString(source, "synonyms"));
            th.setDescription(getString(source, "description"));
            th.setCalculationFormula(getString(source, "calculationFormula"));
            th.setDataCaliber(getString(source, "dataCaliber"));
            th.setBusinessRule(getString(source, "businessRule"));
            th.setCategory(getString(source, "category"));
            th.setRelatedMetricNames(getStringList(source, "relatedMetricNames"));
            th.setRelatedDimensionNames(getStringList(source, "relatedDimensionNames"));
            th.setScore(score);
            th.setMatchSource(matchSource);
            hits.add(th);
        }
        // 归一化 BM25 原始分到 (0,1]，与 hybrid 路径展示口径一致
        double max = hits.stream().mapToDouble(TermHit::getScore).max().orElse(0.0);
        if (max > 0) {
            hits.forEach(h -> h.setScore(h.getScore() / max));
        }
        // 相关性阈值闸门：归一化后再过滤，与 hybrid 路径阈值语义一致
        // （BM25 原始分量级 5~30，归一化前过滤形同虚设）
        if (threshold > 0) {
            hits = hits.stream()
                    .filter(h -> h.getScore() >= threshold)
                    .collect(Collectors.toList());
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
        // 标点不敏感模式：拆段 % 连接（「客户 - 收入」→「%客户%收入%」），
        // 覆盖 LLM 格式化（加空格/改标点/全角半角）导致的字面 LIKE 失配；无有效段时为 null
        String segmentedPattern = buildPunctuationInsensitivePattern(query);
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantCode != null) {
            wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        }
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        wrapper.and(w -> {
            w.like(BusinessTermEntity::getTermName, likePattern)
                    .or().like(BusinessTermEntity::getSynonyms, likePattern)
                    .or().like(BusinessTermEntity::getDescription, likePattern)
                    .or().like(BusinessTermEntity::getCalculationFormula, likePattern)
                    .or().like(BusinessTermEntity::getDataCaliber, likePattern)
                    .or().like(BusinessTermEntity::getBusinessRule, likePattern)
                    .or().like(BusinessTermEntity::getCategory, likePattern)
                    .or().like(BusinessTermEntity::getRelatedMetricsJson, likePattern)
                    .or().like(BusinessTermEntity::getRelatedDimensionsJson, likePattern);
            // 标点不敏感召回只加在核心字段 termName/synonyms，避免外围字段放宽后噪声变多
            if (segmentedPattern != null) {
                w.or().like(BusinessTermEntity::getTermName, segmentedPattern)
                        .or().like(BusinessTermEntity::getSynonyms, segmentedPattern);
            }
        });
        wrapper.last("LIMIT " + topK);

        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);

        // 补充父术语名称
        Map<Long, String> parentNameMap = buildParentNameMap(entities);

        return entities.stream()
                .map(e -> toTermHit(e, parentNameMap))
                .collect(Collectors.toList());
    }

    /**
     * 术语精确命中检索（确定性，不走 ES 打分 / 向量 / topK 截断）。
     * <p>
     * 关键词与某术语的 termName 或任一同义词在去除标点/空白/全角半角差异后**完全相等**时判定命中，
     * 覆盖 LLM 格式化（加空格/改标点/全角半角）与用户直接使用缩写/同义词（如「GMV」命中术语「成交总额」的
     * 同义词字段）的场景。候选集先用「首个核心段」LIKE 裁剪（SQL 无法表达"去标点后相等"），
     * 再在内存中归一化比对，避免全表扫描。
     *
     * @param keyword    搜索关键词
     * @param tenantCode 租户编码，null 表示跨所有租户
     * @return 精确命中术语列表，无命中或检索异常时为空列表
     */
    @Override
    public List<TermHit> exactSearch(String keyword, String tenantCode) {
        String normKw = NameMatchSupport.normalizeKey(keyword);
        if (normKw.isEmpty()) {
            return List.of();
        }
        try {
            LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
            if (tenantCode != null) {
                wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
            }
            wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
            // 候选裁剪：termName/synonyms 含「首个核心段」的启用术语（MySQL 默认不区分大小写排序规则可命中缩写）
            String firstSegment = firstLikeSegment(keyword);
            if (firstSegment != null) {
                wrapper.and(w -> w
                        .like(BusinessTermEntity::getTermName, "%" + firstSegment + "%")
                        .or().like(BusinessTermEntity::getSynonyms, "%" + firstSegment + "%"));
            }
            wrapper.last("LIMIT " + EXACT_SEARCH_CANDIDATE_MAX);

            List<BusinessTermEntity> candidates = businessTermMapper.selectList(wrapper);
            Map<Long, String> parentNameMap = buildParentNameMap(candidates);

            List<TermHit> hits = new ArrayList<>();
            for (BusinessTermEntity e : candidates) {
                if (isExactTermMatch(e, normKw)) {
                    TermHit th = toTermHit(e, parentNameMap);
                    th.setMatchSource("exact");
                    hits.add(th);
                }
            }
            if (!hits.isEmpty()) {
                log.info("业务术语精确命中：keyword='{}' → {}", keyword,
                        hits.stream().map(TermHit::getTermName).collect(Collectors.joining(",")));
            }
            return hits;
        } catch (Exception ex) {
            log.error("术语精确命中检索失败，跳过（不影响原有检索）: {}", ex.getMessage());
            return List.of();
        }
    }

    /** 术语精确命中候选集上限（首个核心段命中面过宽时截断，真实词典远小于此） */
    private static final int EXACT_SEARCH_CANDIDATE_MAX = 1000;

    /** 取文本的第一个"字母/数字/汉字"连续段（全角 ASCII 先转半角），用作候选 LIKE 裁剪；无有效段返回 null */
    private static String firstLikeSegment(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        StringBuilder seg = new StringBuilder();
        for (char c : text.toCharArray()) {
            char cc = c;
            if (cc >= 0xFF01 && cc <= 0xFF5E) {
                cc = (char) (cc - 0xFEE0);
            }
            if (cc == 0x3000) {
                cc = ' ';
            }
            if (Character.isLetterOrDigit(cc)) {
                seg.append(cc);
                if (seg.length() >= 8) {
                    break;
                }
            } else if (seg.length() > 0) {
                break;
            }
        }
        return seg.length() == 0 ? null : seg.toString();
    }

    /** 判定术语是否与归一化后的关键词完全相等（termName 或任一同义词，同义词按常见分隔符拆分） */
    private static boolean isExactTermMatch(BusinessTermEntity e, String normKw) {
        String name = e.getTermName();
        if (name != null && NameMatchSupport.normalizeKey(name).equals(normKw)) {
            return true;
        }
        String synonyms = e.getSynonyms();
        if (synonyms != null && !synonyms.isBlank()) {
            for (String syn : synonyms.split("[,，;；]")) {
                String s = syn.trim();
                if (!s.isEmpty() && NameMatchSupport.normalizeKey(s).equals(normKw)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 术语实体 → 检索命中项（public 展示用） */
    private TermHit toTermHit(BusinessTermEntity e, Map<Long, String> parentNameMap) {
        TermHit th = new TermHit();
        th.setTermName(e.getTermName());
        th.setSynonyms(e.getSynonyms());
        th.setDescription(e.getDescription());
        th.setCalculationFormula(e.getCalculationFormula());
        th.setDataCaliber(e.getDataCaliber());
        th.setBusinessRule(e.getBusinessRule());
        th.setCategory(e.getCategory());
        th.setRelatedMetricNames(toRefNames(e.parseRelatedMetrics()));
        th.setRelatedDimensionNames(toRefNames(e.parseRelatedDimensions()));
        th.setParentTermName(e.getParentId() != null ? parentNameMap.get(e.getParentId()) : null);
        th.setScore(1.0);
        th.setMatchSource("keyword");
        return th;
    }

    /**
     * 标点不敏感 LIKE 模式。
     * <p>
     * 将查询拆分为连续「字母/数字/汉字」段（范式与 AloudataCallTool.normalizeKey 一致：
     * 全角 ASCII 转半角、丢弃空白/标点/括号/连接符），段间用 LIKE 通配符 {@code %} 连接：
     * 「客户 - 收入」→「%客户%收入%」，可命中库内「客户-收入」「客户(收入)」「客户收入」等写法。
     * <p>
     * 仅用于 MySQL LIKE 降级路径的 termName/synonyms 核心字段，避免 LLM 格式化（加空格/改标点/
     * 全角半角）导致业务词典召回落空；ES 主路径走 ik 分词（拆词后天然免疫），无需此处理。
     * 全角字母数字（如「ＡＢＣ」）先转半角再分段。无有效段（如纯标点查询）时返回 null，
     * 由调用方跳过该模式。实现委托 {@link vip.mate.dataagent.support.NameMatchSupport}。
     */
    private static String buildPunctuationInsensitivePattern(String query) {
        return NameMatchSupport.likePattern(query);
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
        doc.put("relatedMetricNames", toRefNames(entity.parseRelatedMetrics()));
        doc.put("relatedDimensionNames", toRefNames(entity.parseRelatedDimensions()));
        doc.put("example", entity.getExample());
        doc.put("securityLevel", entity.getSecurityLevel());
        // 启用状态（keyword），供检索侧状态过滤使用；null 视为启用（兼容旧数据）
        doc.put("status", entity.getStatus() != null
                ? String.valueOf(entity.getStatus())
                : String.valueOf(DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED));
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
            log.error("生成查询向量失败: {}", e.getMessage());
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
            log.error("构建 EmbeddingModel 失败: {}", e.getMessage());
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

    @SuppressWarnings("unchecked")
    private String getJoinedString(Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof List) {
            return String.join(",", ((List<String>) val).stream().filter(Objects::nonNull).toList());
        }
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val instanceof List) {
            return ((List<Object>) val).stream().map(String::valueOf).filter(Objects::nonNull).toList();
        }
        if (val != null) {
            return List.of(val.toString());
        }
        return null;
    }

    /**
     * 提取引用列表中的名称（metricName / dimName）
     */
    private List<String> toRefNames(List<BusinessTermRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        return refs.stream()
                .map(BusinessTermRef::getName)
                .filter(Objects::nonNull)
                .toList();
    }
}
