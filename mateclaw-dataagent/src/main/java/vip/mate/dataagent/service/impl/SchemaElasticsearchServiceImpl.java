package vip.mate.dataagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.service.SchemaElasticsearchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema Elasticsearch 检索服务实现
 * <p>
 * 基于 Elasticsearch 8.x Java Client 实现关键词检索（multi_match）、
 * 向量语义检索（kNN）和混合检索（RRF 融合）。
 * ElasticsearchClient 为可选依赖，缺失时优雅降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaElasticsearchServiceImpl implements SchemaElasticsearchService {

    private final ElasticsearchClient esClient;

    /** 向量维度缓存 */
    private volatile int cachedVectorDimension = -1;

    /** 索引是否已初始化 */
    private volatile boolean indexInitialized = false;

    /** 可选依赖：ES 不可用时降级 */
    @Autowired(required = false)
    private ElasticsearchClient optionalEsClient;

    /**
     * 获取可用的 ES 客户端
     * <p>
     * 优先使用 optionalEsClient（允许缺失降级），回退到构造注入的 esClient
     */
    private ElasticsearchClient getAvailableClient() {
        if (optionalEsClient != null) {
            return optionalEsClient;
        }
        try {
            esClient.ping();
            return esClient;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 确保索引存在，若不存在则自动创建
     */
    @Override
    public void ensureIndex(int vectorDimension) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.warn("Elasticsearch 客户端不可用，跳过索引创建");
            return;
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        try {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(e -> e.index(indexName))
            ).value();

            if (exists) {
                cachedVectorDimension = vectorDimension;
                indexInitialized = true;
                return;
            }

            /* 创建索引，包含 dense_vector 字段用于 kNN 检索 */
            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("tableName", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.SCHEMA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t
                                    .analyzer("ik_max_word")
                                    .searchAnalyzer("ik_smart")
                            ))
                            .properties(DataAgentConstants.SCHEMA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv
                                            .dims(dims)
                                            .index(true)
                                            .similarity(DenseVectorSimilarity.Cosine)
                                    )
                            )
                    )
            ));

            cachedVectorDimension = vectorDimension;
            indexInitialized = true;
            log.info("Elasticsearch 索引 [{}] 创建成功，向量维度: {}", indexName, dims);
        } catch (IOException e) {
            log.error("Elasticsearch 索引创建失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            /* ik_max_word 分词器可能不存在，回退到标准分词器 */
            log.warn("使用 ik 分词器创建索引失败，回退到标准分词器: {}", e.getMessage());
            tryCreateWithStandardAnalyzer(client, indexName, vectorDimension);
        }
    }

    /**
     * 使用标准分词器创建索引（ik 分词器不可用时的降级方案）
     */
    private void tryCreateWithStandardAnalyzer(ElasticsearchClient client, String indexName, int vectorDimension) {
        try {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(e -> e.index(indexName))
            ).value();
            if (exists) {
                indexInitialized = true;
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("tableName", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.SCHEMA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t))
                            .properties(DataAgentConstants.SCHEMA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv
                                            .dims(dims)
                                            .index(true)
                                            .similarity(DenseVectorSimilarity.Cosine)
                                    )
                            )
                    )
            ));
            indexInitialized = true;
            log.info("Elasticsearch 索引 [{}] 创建成功（标准分词器），向量维度: {}", indexName, dims);
        } catch (Exception ex) {
            log.error("Elasticsearch 索引创建失败（标准分词器降级）: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 索引单张表的 Schema 嵌入文档
     */
    @Override
    public void indexSchema(Long datasourceId, String tableName, String embeddingText, float[] embedding) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.debug("Elasticsearch 客户端不可用，跳过索引写入");
            return;
        }

        /* 确保索引已创建 */
        if (!indexInitialized && embedding != null && embedding.length > 0) {
            ensureIndex(embedding.length);
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        String docId = datasourceId + "_" + tableName;

        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("datasourceId", datasourceId);
            doc.put("tableName", tableName);
            doc.put(DataAgentConstants.SCHEMA_ES_EMBEDDING_TEXT_FIELD, embeddingText);
            if (embedding != null && embedding.length > 0) {
                /* dense_vector 字段需要 float[] 格式 */
                List<Float> embeddingList = new ArrayList<>(embedding.length);
                for (float v : embedding) {
                    embeddingList.add(v);
                }
                doc.put(DataAgentConstants.SCHEMA_ES_EMBEDDING_FIELD, embeddingList);
            }

            client.index(idx -> idx
                    .index(indexName)
                    .id(docId)
                    .document(doc)
            );
            log.debug("ES 索引写入成功: {}/{}", datasourceId, tableName);
        } catch (IOException e) {
            log.warn("ES 索引写入失败: {}/{} - {}", datasourceId, tableName, e.getMessage());
        }
    }

    /**
     * 删除数据源的所有 Schema 嵌入文档
     */
    @Override
    public void deleteByDatasourceId(Long datasourceId) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            return;
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        try {
            client.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q
                            .term(t -> t
                                    .field("datasourceId")
                                    .value(datasourceId)
                            )
                    )
            );
            log.info("ES 索引删除完成，数据源: {}", datasourceId);
        } catch (IOException e) {
            log.warn("ES 索引删除失败，数据源: {} - {}", datasourceId, e.getMessage());
        }
    }

    /**
     * 删除单张表的 Schema 嵌入文档
     */
    @Override
    public void deleteByTableName(Long datasourceId, String tableName) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            return;
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        String docId = datasourceId + "_" + tableName;
        try {
            client.delete(d -> d
                    .index(indexName)
                    .id(docId)
            );
            log.debug("ES 索引删除完成: {}/{}", datasourceId, tableName);
        } catch (IOException e) {
            log.warn("ES 索引删除失败: {}/{} - {}", datasourceId, tableName, e.getMessage());
        }
    }

    /**
     * 关键词检索
     * <p>
     * 使用 Elasticsearch multi_match 查询在嵌入文本中匹配关键词
     */
    @Override
    public List<SchemaSearchResult.TableHit> keywordSearch(Long datasourceId, String query, int topK) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null || !StringUtils.hasText(query)) {
            return List.of();
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index(indexName)
                            .size(topK)
                            .query(q -> q
                                    .bool(b -> b
                                            .filter(f -> f
                                                    .term(t -> t
                                                            .field("datasourceId")
                                                            .value(datasourceId)
                                                    )
                                            )
                                            .must(m -> m
                                                    .multiMatch(mm -> mm
                                                            .fields(DataAgentConstants.SCHEMA_ES_EMBEDDING_TEXT_FIELD)
                                                            .type(TextQueryType.CrossFields)
                                                            .query(query)
                                                    )
                                            )
                                    )
                            ),
                    Map.class
            );

            return extractTableHits(response, "keyword");
        } catch (IOException e) {
            log.warn("ES 关键词检索失败: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("ES 关键词检索异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 向量语义检索
     * <p>
     * 使用 Elasticsearch kNN 搜索进行近似最近邻检索
     */
    @Override
    public List<SchemaSearchResult.TableHit> semanticSearch(Long datasourceId, float[] queryVector,
                                                             int topK, double similarityThreshold) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null || queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        try {
            /* 构建查询向量 */
            List<Float> queryVectorList = new ArrayList<>(queryVector.length);
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            SearchResponse<Map> response = client.search(s -> s
                            .index(indexName)
                            .size(topK)
                            .knn(knn -> knn
                                    .field(DataAgentConstants.SCHEMA_ES_EMBEDDING_FIELD)
                                    .queryVector(queryVectorList)
                                    .k(topK)
                                    .numCandidates(DataAgentConstants.ES_KNN_NUM_CANDIDATES)
                                    .filter(f -> f
                                            .term(t -> t
                                                    .field("datasourceId")
                                                    .value(datasourceId)
                                            )
                                    )
                            ),
                    Map.class
            );

            List<SchemaSearchResult.TableHit> hits = extractTableHits(response, "semantic");
            /* 过滤低于阈值的结果 */
            hits = hits.stream()
                    .filter(h -> h.getScore() >= similarityThreshold)
                    .toList();
            return hits;
        } catch (IOException e) {
            log.warn("ES 向量语义检索失败: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("ES 向量语义检索异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 混合检索（关键词 + 向量语义）
     * <p>
     * 使用 Elasticsearch RRF（Reciprocal Rank Fusion）融合关键词和向量检索结果
     */
    @Override
    public SchemaSearchResult hybridSearch(SchemaSearchRequest request, float[] queryVector) {
        long startTime = System.currentTimeMillis();
        SchemaSearchResult result = new SchemaSearchResult();

        if (request == null || request.getDatasourceId() == null || !StringUtils.hasText(request.getQuery())) {
            result.setTableHits(List.of());
            result.setRelations(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            result.setTableHits(List.of());
            result.setRelations(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        int topK = request.getTopK() != null ? request.getTopK() : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_TOP_K;
        double threshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold()
                : DataAgentConstants.SCHEMA_SEARCH_DEFAULT_THRESHOLD;

        String indexName = DataAgentConstants.SCHEMA_ELASTICSEARCH_INDEX;
        List<SchemaSearchResult.TableHit> tableHits;
        String matchSource = "keyword";

        boolean hasVector = queryVector != null && queryVector.length > 0;

        try {
            if (hasVector) {
                /* 混合检索：RRF 融合关键词和 kNN 向量检索 */
                List<Float> queryVectorList = new ArrayList<>(queryVector.length);
                for (float v : queryVector) {
                    queryVectorList.add(v);
                }

                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(topK)
                                .query(q -> q
                                        .bool(b -> b
                                                .filter(f -> f
                                                        .term(t -> t
                                                                .field("datasourceId")
                                                                .value(request.getDatasourceId())
                                                        )
                                                )
                                                .should(sh -> sh
                                                        .multiMatch(mm -> mm
                                                                .fields(DataAgentConstants.SCHEMA_ES_EMBEDDING_TEXT_FIELD)
                                                                .type(TextQueryType.CrossFields)
                                                                .query(request.getQuery())
                                                        )
                                                )
                                                .minimumShouldMatch("1")
                                        )
                                )
                                .knn(knn -> knn
                                        .field(DataAgentConstants.SCHEMA_ES_EMBEDDING_FIELD)
                                        .queryVector(queryVectorList)
                                        .k(topK)
                                        .numCandidates(DataAgentConstants.ES_KNN_NUM_CANDIDATES)
                                        .filter(f -> f
                                                .term(t -> t
                                                        .field("datasourceId")
                                                        .value(request.getDatasourceId())
                                                )
                                        )
                                )
                                .rank(r -> r
                                        .rrf(rrf -> rrf
                                                .rankConstant((long) DataAgentConstants.SCHEMA_SEARCH_RRF_K)
                                        )
                                ),
                        Map.class
                );

                tableHits = extractTableHits(response, "hybrid");
                matchSource = "hybrid";
            } else {
                /* 仅关键词检索 */
                tableHits = keywordSearch(request.getDatasourceId(), request.getQuery(), topK);
                matchSource = "keyword";
            }

            /* 设置 matchSource */
            for (SchemaSearchResult.TableHit h : tableHits) {
                h.setMatchSource(matchSource);
            }

            /* RRF 分数尺度远小于 BM25，不适用 BM25 的 threshold 过滤 */
        } catch (IOException e) {
            log.warn("ES 混合检索失败，降级为关键词检索: {}", e.getMessage());
            tableHits = keywordSearch(request.getDatasourceId(), request.getQuery(), topK);
            matchSource = "keyword";
        } catch (Exception e) {
            log.warn("ES 混合检索异常: {}", e.getMessage());
            tableHits = List.of();
            matchSource = "keyword";
        }

        result.setTableHits(tableHits);
        result.setRelations(List.of());
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== private 方法 ====================

    /**
     * 从 ES 搜索响应中提取 TableHit 列表
     */
    @SuppressWarnings("unchecked")
    private List<SchemaSearchResult.TableHit> extractTableHits(SearchResponse<Map> response, String matchSource) {
        List<SchemaSearchResult.TableHit> hits = new ArrayList<>();
        if (response == null || response.hits() == null) {
            return hits;
        }

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }

            SchemaSearchResult.TableHit tableHit = new SchemaSearchResult.TableHit();
            Object tableNameObj = source.get("tableName");
            if (tableNameObj != null) {
                tableHit.setTableName(tableNameObj.toString());
            }
            tableHit.setScore(hit.score() != null ? hit.score() : 0.0);
            tableHit.setMatchSource(matchSource);
            hits.add(tableHit);
        }

        return hits;
    }
}
