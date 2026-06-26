package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.BusinessTermCreateRequest;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermUpdateRequest;
import vip.mate.dataagent.dto.BusinessTermVO;
import vip.mate.dataagent.model.BusinessTermEntity;
import vip.mate.dataagent.repository.BusinessTermMapper;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.dataagent.service.BusinessTermService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.service.WikiEmbeddingService;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务术语服务实现
 */
@Service
@RequiredArgsConstructor
public class BusinessTermServiceImpl implements BusinessTermService {

    private static final Logger log = LoggerFactory.getLogger(BusinessTermServiceImpl.class);

    private final BusinessTermMapper businessTermMapper;
    private final BusinessTermEsService businessTermEsService;
    private final ModelConfigService modelConfigService;

    /** 可选依赖：Embedding 模型工厂 */
    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    /**
     * 列出所有已存在术语数据的租户编码（去重）
     */
    @Override
    public List<String> listTenantCodes() {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
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
     * 按租户查询所有启用的术语
     */
    @Override
    public List<BusinessTermVO> listByTenantCode(String tenantCode) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
        List<BusinessTermEntity> entities = businessTermMapper.selectList(wrapper);
        Map<Long, String> parentNameMap = buildParentNameMap(entities);
        return entities.stream().map(e -> toVO(e, parentNameMap)).collect(Collectors.toList());
    }

    /**
     * 按租户和类目查询启用的术语
     */
    @Override
    public List<BusinessTermVO> listByTenantCodeAndCategory(String tenantCode, String category) {
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getCategory, category);
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
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
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
        Long count = businessTermMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("术语已存在: " + request.getTermName());
        }
        BusinessTermEntity entity = new BusinessTermEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setDeleted(0);
        entity.setStatus(DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        businessTermMapper.insert(entity);
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
            wrapper.eq(BusinessTermEntity::getDeleted, 0);
            wrapper.ne(BusinessTermEntity::getId, id);
            Long count = businessTermMapper.selectCount(wrapper);
            if (count > 0) {
                throw new RuntimeException("术语已存在: " + request.getTermName());
            }
            entity.setTermName(request.getTermName());
        }
        if (request.getSynonyms() != null) {
            entity.setSynonyms(request.getSynonyms());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            entity.setCategory(request.getCategory());
        }
        if (request.getParentId() != null) {
            entity.setParentId(request.getParentId());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        businessTermMapper.updateById(entity);
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
        entity.setDeleted(1);
        businessTermMapper.updateById(entity);
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
    }

    /**
     * 关键词搜索术语
     * <p>
     * 在 term_name, synonyms, description, category 字段中做 LIKE 搜索
     */
    @Override
    public List<BusinessTermVO> searchByKeyword(String tenantCode, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listByTenantCode(tenantCode);
        }
        LambdaQueryWrapper<BusinessTermEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTermEntity::getTenantCode, tenantCode);
        wrapper.eq(BusinessTermEntity::getStatus, DataAgentConstants.BUSINESS_TERM_STATUS_ENABLED);
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
        String likePattern = "%" + keyword + "%";
        wrapper.and(w -> {
            w.like(BusinessTermEntity::getTermName, likePattern)
                    .or().like(BusinessTermEntity::getSynonyms, likePattern)
                    .or().like(BusinessTermEntity::getDescription, likePattern)
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
        wrapper.eq(BusinessTermEntity::getDeleted, 0);
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

    /**
     * 语义混合检索术语
     */
    @Override
    public BusinessTermSearchResult semanticSearch(String query, int topK, double threshold) {
        return businessTermEsService.hybridSearch(query, topK, threshold);
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
