package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.BusinessTermCreateRequest;
import vip.mate.dataagent.dto.BusinessTermRef;
import vip.mate.dataagent.dto.BusinessTermReferenceOptions;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermUpdateRequest;
import vip.mate.dataagent.dto.BusinessTermVO;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.BusinessTermEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.BusinessTermMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.dataagent.service.BusinessTermService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.service.WikiEmbeddingService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 业务术语服务实现
 */
@Service
@RequiredArgsConstructor
public class BusinessTermServiceImpl implements BusinessTermService {

    private static final Logger log = LoggerFactory.getLogger(BusinessTermServiceImpl.class);

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 术语 ES 异步同步执行器：写操作（增/删/改/启停用）后局部重索引或删除，
     * 保证词典变更立即对语义检索生效，同时不阻塞管理请求。
     */
    private static final ExecutorService TERM_ES_SYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "dataagent-term-es-sync");
        t.setDaemon(true);
        return t;
    });

    private final BusinessTermMapper businessTermMapper;
    private final BusinessTermEsService businessTermEsService;
    private final ModelConfigService modelConfigService;
    private final AloudataSemanticSyncService aloudataSemanticSyncService;
    private final DatasourceMapper datasourceMapper;

    /** 可选依赖：Embedding 模型工厂 */
    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    /**
     * 列出所有已存在术语数据的租户编码（去重）
     */
    @Override
    public List<String> listTenantCodes() {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(BusinessTermEntity::getTenantCode);
        wrapper.groupBy(BusinessTermEntity::getTenantCode);
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        return entities.stream()
                .map(BusinessTermEntity::getTenantCode)
                .filter(tc -> tc != null && !tc.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按租户查询术语（includeDisabled=true 时包含停用术语，供管理界面展示）
     */
    @Override
    public List<BusinessTermVO> listByTenantCode(String tenantCode, boolean includeDisabled) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        if (!includeDisabled) {
            wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        }
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        Map<Long, String> parentNameMap = buildParentNameMap(entities);
        return entities.stream().map(e -> toVO(e, parentNameMap)).collect(Collectors.toList());
    }

    /**
     * 按租户和类目查询术语（includeDisabled=true 时包含停用术语）
     */
    @Override
    public List<BusinessTermVO> listByTenantCodeAndCategory(String tenantCode, String category, boolean includeDisabled) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getCategory, category);
        if (!includeDisabled) {
            wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        }
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        Map<Long, String> parentNameMap = buildParentNameMap(entities);
        return entities.stream().map(e -> toVO(e, parentNameMap)).collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取术语
     */
    @Override
    public BusinessTermVO getById(Long id) {
        BusinessTermEntity entity = businessTermMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        return toVO(entity, buildParentNameMap(List.of(entity)));
    }

    /**
     * 创建术语
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessTermVO create(BusinessTermCreateRequest request) {
        // 检查唯一约束：同一租户下相同术语名不允许重复
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, request.getTenantCode());
        wrapper.eq(BusinessTermEntity::getTermName, request.getTermName());
        Long count = businessTermMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("术语已存在: " + request.getTermName());
        }
        BusinessTermEntity entity = new BusinessTermEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setRelatedMetricsJson(toRefsJson(request.getRelatedMetrics()));
        entity.setRelatedDimensionsJson(toRefsJson(request.getRelatedDimensions()));
        entity.setDeleted(0);
        entity.setStatus(DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        businessTermMapper.insert(entity);
        // 写操作后异步同步 ES（重建嵌入文本/向量并索引），保证新术语立即对语义检索生效
        asyncIndexTermToEs(entity);
        return toVO(entity, buildParentNameMap(List.of(entity)));
    }

    /**
     * 更新术语
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessTermVO update(Long id, BusinessTermUpdateRequest request) {
        BusinessTermEntity entity = businessTermMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        if (request.getTermName() != null) {
            // 检查唯一约束：同一租户下相同术语名不允许重复（排除自身）
            LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BusinessTermEntity::getTenantCode, entity.getTenantCode());
            wrapper.eq(BusinessTermEntity::getTermName, request.getTermName());
            wrapper.ne(BusinessTermEntity::getId, id);
            Long count = businessTermMapper.selectCount(wrapper);
            if (count > 0) {
                throw new RuntimeException("术语已存在: " + request.getTermName());
            }
            entity.setTermName(request.getTermName());
        }
        entity.setSynonyms(request.getSynonyms());
        entity.setDescription(request.getDescription());
        entity.setCalculationFormula(request.getCalculationFormula());
        entity.setDataCaliber(request.getDataCaliber());
        entity.setDataSource(request.getDataSource());
        entity.setOwner(request.getOwner());
        entity.setBusinessRule(request.getBusinessRule());
        entity.setRelatedTerms(request.getRelatedTerms());
        entity.setRelatedMetricsJson(toRefsJson(request.getRelatedMetrics()));
        entity.setRelatedDimensionsJson(toRefsJson(request.getRelatedDimensions()));
        entity.setRelatedMetrics(request.getRelatedMetrics());
        entity.setRelatedDimensions(request.getRelatedDimensions());
        entity.setExample(request.getExample());
        entity.setSecurityLevel(request.getSecurityLevel());
        entity.setCategory(request.getCategory());
        entity.setParentId(request.getParentId());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        businessTermMapper.updateById(entity);
        // 写操作后异步同步 ES，保证修改立即对语义检索生效（含启停用状态）
        asyncIndexTermToEs(entity);
        return toVO(entity, buildParentNameMap(List.of(entity)));
    }

    /**
     * 删除术语（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BusinessTermEntity entity = businessTermMapper.selectById(id);
        if (entity == null) {
            return;
        }
        businessTermMapper.deleteById(id);
        // 同步从 ES 删除文档，保证已删除术语不再出现在检索结果中
        asyncDeleteTermFromEs(entity);
    }

    /**
     * 按租户删除所有术语（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByTenantCode(String tenantCode) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        businessTermMapper.delete(wrapper);
        // 同步删除 ES 索引
        try {
            businessTermEsService.deleteByTenantCode(tenantCode);
        } catch (Exception e) {
            log.warn("删除租户 [{}] 术语 ES 索引失败: {}", tenantCode, e.getMessage());
        }
    }

    /**
     * 启用术语
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        BusinessTermEntity entity = businessTermMapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setStatus(DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        businessTermMapper.updateById(entity);
        // 同步 ES 状态，启用后立即恢复可检索
        asyncIndexTermToEs(entity);
    }

    /**
     * 停用术语
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        BusinessTermEntity entity = businessTermMapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setStatus(DataAgentConstants.BUSINESS_TERM_STATUS_DISABLED);
        businessTermMapper.updateById(entity);
        // 同步 ES 状态，停用后立即从检索结果中剔除
        asyncIndexTermToEs(entity);
    }

    /**
     * 关键词搜索术语
     * <p>
     * 在 term_name, synonyms, description, category 字段中做 LIKE 搜索
     */
    @Override
    public List<BusinessTermVO> searchByKeyword(String tenantCode, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listByTenantCode(tenantCode, false);
        }
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        String likePattern = "%" + keyword + "%";
        wrapper.and(w -> {
            w.like(BusinessTermEntity::getTermName, likePattern)
                    .or().like(BusinessTermEntity::getSynonyms, likePattern)
                    .or().like(BusinessTermEntity::getDescription, likePattern)
                    .or().like(BusinessTermEntity::getCalculationFormula, likePattern)
                    .or().like(BusinessTermEntity::getDataCaliber, likePattern)
                    .or().like(BusinessTermEntity::getBusinessRule, likePattern)
                    .or().like(BusinessTermEntity::getCategory, likePattern);
        });
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        Map<Long, String> parentNameMap = buildParentNameMap(entities);
        return entities.stream().map(e -> toVO(e, parentNameMap)).collect(Collectors.toList());
    }

    /**
     * 为租户下的所有术语生成嵌入向量并写入 ES 索引
     */
    @Override
    public int embedAndIndexAll(String tenantCode) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        if (entities.isEmpty()) {
            return 0;
        }

        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        if (embeddingModel == null) {
            log.warn("Embedding 模型不可用，仅写入 ES 关键词索引（不生成向量）");
            businessTermEsService.ensureIndex(DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION);
            for (BusinessTermEntity entity : entities) {
                entity.setEmbeddingText(entity.buildEmbeddingText());
                businessTermMapper.updateById(entity);
            }
            businessTermEsService.indexTerms(entities);
            return entities.size();
        }

        int embeddedCount = 0;
        for (BusinessTermEntity entity : entities) {
            String embeddingText = entity.buildEmbeddingText();
            entity.setEmbeddingText(embeddingText);

            try {
                EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(embeddingText), null));
                float[] vector = resp.getResults().get(0).getOutput();
                if (vector != null && vector.length > 0) {
                    entity.setEmbedding(WikiEmbeddingService.floatsToBytes(vector));
                    entity.setEmbeddingModelId(resolveEmbeddingModelId());
                }
                embeddedCount++;
            } catch (Exception e) {
                log.warn("术语 [{}] 向量化失败: {}", entity.getTermName(), e.getMessage());
            }

            businessTermMapper.updateById(entity);
        }

        // 写入 ES
        businessTermEsService.ensureIndex(DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION);
        businessTermEsService.indexTerms(entities);

        log.info("租户 [{}] 术语嵌入完成，成功向量化 {} / {} 条", tenantCode, embeddedCount, entities.size());
        return embeddedCount;
    }

    /**
     * 从 MySQL 已同步数据重新向量化并写入 ES
     */
    @Override
    public int rebuildEsIndex(String tenantCode) {
        // 先删除旧 ES 索引
        businessTermEsService.deleteByTenantCode(tenantCode);
        // 重新嵌入和索引
        return embedAndIndexAll(tenantCode);
    }

    // ==================== 写操作后 ES 异步同步 ====================

    /**
     * 写操作后异步重索引单个术语到 ES（重建嵌入文本/向量并写入，含启用状态）。
     * 失败不影响 MySQL 写操作的返回，仅记录日志；下次全量重索引可自愈。
     */
    private void asyncIndexTermToEs(BusinessTermEntity entity) {
        try {
            TERM_ES_SYNC_EXECUTOR.submit(() -> {
                try {
                    indexTermToEs(entity);
                } catch (Exception e) {
                    log.warn("术语 [{}] ES 异步重索引失败: {}", entity.getTermName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交术语 [{}] ES 异步重索引任务失败: {}", entity.getTermName(), e.getMessage());
        }
    }

    /**
     * 删除后异步从 ES 移除单个术语文档。
     */
    private void asyncDeleteTermFromEs(BusinessTermEntity entity) {
        try {
            TERM_ES_SYNC_EXECUTOR.submit(() -> {
                try {
                    businessTermEsService.deleteTerm(entity);
                } catch (Exception e) {
                    log.warn("术语 [{}] ES 文档删除失败: {}", entity.getTermName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交术语 [{}] ES 文档删除任务失败: {}", entity.getTermName(), e.getMessage());
        }
    }

    /**
     * 重建术语嵌入文本与向量并索引到 ES，同时回写 MySQL 保持两端一致。
     * 向量化失败时仅索引关键词字段（向量路降级，不影响关键词检索）。
     */
    private void indexTermToEs(BusinessTermEntity entity) {
        String embeddingText = entity.buildEmbeddingText();
        entity.setEmbeddingText(embeddingText);
        try {
            float[] vector = generateEmbedding(embeddingText);
            if (vector != null && vector.length > 0) {
                entity.setEmbedding(WikiEmbeddingService.floatsToBytes(vector));
                entity.setEmbeddingModelId(resolveEmbeddingModelId());
            }
        } catch (Exception e) {
            log.warn("术语 [{}] 向量化失败，仅索引关键词字段: {}", entity.getTermName(), e.getMessage());
        }
        // 回写 MySQL，保证嵌入文本/向量与 ES 一致（embedAndIndexAll 同样会持久化）
        businessTermMapper.updateById(entity);
        businessTermEsService.indexTerm(entity);
    }

    /**
     * 生成单条文本的嵌入向量；Embedding 模型不可用或调用失败时返回 null。
     */
    private float[] generateEmbedding(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        EmbeddingModel embeddingModel = resolveEmbeddingModel();
        if (embeddingModel == null) {
            return null;
        }
        EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        float[] vector = resp.getResults().get(0).getOutput();
        return (vector == null || vector.length == 0) ? null : vector;
    }

    /**
     * 语义混合检索术语
     */
    @Override
    public BusinessTermSearchResult semanticSearch(String query, int topK, double threshold) {
        return businessTermEsService.hybridSearch(query, topK, threshold);
    }

    /**
     * 查询关联引用候选（跨数据源的指标 / 维度）
     */
    @Override
    public BusinessTermReferenceOptions listReferenceOptions(String keyword, int limit) {
        int max = Math.min(Math.max(limit, 1), DataAgentConstants.BUSINESS_TERM_REF_OPTIONS_LIMIT_MAX);

        // 复用语义层公共查询：跨数据源检索指标 / 维度候选（datasourceId 传 null）
        List<AloudataMetricEntity> metrics = aloudataSemanticSyncService.pageMetricEntities(null, keyword, 0, max);
        List<AloudataDimensionEntity> dimensions = aloudataSemanticSyncService.pageDimensionEntities(null, keyword, 0, max);

        // 批量构建数据源名称映射
        Map<Long, String> datasourceNameMap = buildDatasourceNameMap(buildDatasourceIds(metrics, dimensions));

        BusinessTermReferenceOptions options = new BusinessTermReferenceOptions();
        options.setMetrics(metrics.stream()
                .map(m -> toRef(m.getId(), m.getDatasourceId(), datasourceNameMap,
                        m.getMetricName(), m.getMetricDisplayName()))
                .collect(Collectors.toList()));
        options.setDimensions(dimensions.stream()
                .map(d -> toRef(d.getId(), d.getDatasourceId(), datasourceNameMap,
                        d.getDimName(), d.getDimDisplayName()))
                .collect(Collectors.toList()));
        return options;
    }

    /**
     * 序列化关联引用列表为 JSON 字符串
     */
    private String toRefsJson(List<BusinessTermRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(refs);
        } catch (Exception e) {
            log.warn("序列化关联引用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 收集指标 / 维度引用的数据源 ID 集合
     */
    private Collection<Long> buildDatasourceIds(List<AloudataMetricEntity> metrics, List<AloudataDimensionEntity> dimensions) {
        Collection<Long> ids = new java.util.HashSet<>();
        if (metrics != null) {
            metrics.forEach(m -> ids.add(m.getDatasourceId()));
        }
        if (dimensions != null) {
            dimensions.forEach(d -> ids.add(d.getDatasourceId()));
        }
        return ids;
    }

    /**
     * 构建数据源 ID → 名称映射
     */
    private Map<Long, String> buildDatasourceNameMap(Collection<Long> datasourceIds) {
        List<Long> ids = datasourceIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return datasourceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(DatasourceEntity::getId,
                        d -> d.getName() == null ? "" : d.getName()));
    }

    /**
     * 构造关联引用对象
     */
    private BusinessTermRef toRef(Long id, Long datasourceId, Map<Long, String> datasourceNameMap,
                                  String name, String displayName) {
        BusinessTermRef ref = new BusinessTermRef();
        ref.setId(id);
        ref.setDatasourceId(datasourceId);
        ref.setDatasourceName(datasourceNameMap.get(datasourceId));
        ref.setName(name);
        ref.setDisplayName(displayName);
        return ref;
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

    /**
     * 解析当前 Embedding 模型 ID
     */
    private Long resolveEmbeddingModelId() {
        ModelConfigEntity config = resolveEmbeddingModelConfig();
        return config != null ? config.getId() : null;
    }

    /**
     * 构建父术语 ID → 名称映射
     */
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

    /**
     * Entity 转 VO
     */
    private BusinessTermVO toVO(BusinessTermEntity entity, Map<Long, String> parentNameMap) {
        BusinessTermVO vo = new BusinessTermVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setPromptInfo(entity.getPromptInfo());
        vo.setRelatedMetrics(entity.parseRelatedMetrics());
        vo.setRelatedDimensions(entity.parseRelatedDimensions());
        if (entity.getParentId() != null) {
            vo.setParentTermName(parentNameMap.get(entity.getParentId()));
        }
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }
}
