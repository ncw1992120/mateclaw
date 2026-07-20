package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.DatasourceColumnEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.model.DatasourceTableEntity;
import vip.mate.dataagent.model.SemanticModelEntity;
import vip.mate.dataagent.repository.DatasourceColumnMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.repository.DatasourceTableMapper;
import vip.mate.dataagent.repository.SemanticModelMapper;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.SemanticModelService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字段级语义模型服务实现
 */
@Service
@RequiredArgsConstructor
public class SemanticModelServiceImpl implements SemanticModelService {

    private static final Logger log = LoggerFactory.getLogger(SemanticModelServiceImpl.class);

    private final SemanticModelMapper semanticModelMapper;
    private final DatasourceColumnMapper datasourceColumnMapper;
    private final DatasourceTableMapper datasourceTableMapper;
    private final DatasourceMapper datasourceMapper;
    private final AloudataService aloudataService;
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceManageService datasourceManageService;

    /** Aloudata 指标在语义模型中的虚拟表名前缀 */
    private static final String ALOUDATA_METRIC_TABLE_PREFIX = "指标";

    /** Aloudata 维度在语义模型中的虚拟表名前缀 */
    private static final String ALOUDATA_DIMENSION_TABLE_PREFIX = "维度";

    /**
     * 按数据源查询所有启用的语义模型
     */
    @Override
    public List<SemanticModelVO> listByDatasourceId(Long datasourceId) {
        datasourceManageService.checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        List<SemanticModelEntity> entities = semanticModelMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按数据源和表名查询启用的语义模型
     */
    @Override
    public List<SemanticModelVO> listByDatasourceIdAndTableNames(Long datasourceId, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return new ArrayList<>();
        }
        datasourceManageService.checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.in(SemanticModelEntity::getTableName, tableNames);
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        List<SemanticModelEntity> entities = semanticModelMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取语义模型
     */
    @Override
    public SemanticModelVO getById(Long id) {
        requireSemanticOwnership(id);
        SemanticModelEntity entity = semanticModelMapper.selectById(id);
        return toVO(entity);
    }

    /**
     * 创建语义模型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SemanticModelVO create(SemanticModelCreateRequest request) {
        datasourceManageService.getDatasource(request.getDatasourceId());
        // 检查唯一约束：同一数据源下相同表名+字段名不允许重复
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, request.getDatasourceId());
        wrapper.eq(SemanticModelEntity::getTableName, request.getTableName());
        wrapper.eq(SemanticModelEntity::getColumnName, request.getColumnName());
        Long count = semanticModelMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("语义模型已存在: " + request.getTableName() + "." + request.getColumnName());
        }
        SemanticModelEntity entity = new SemanticModelEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        entity.setDeleted(0);
        entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        semanticModelMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 更新语义模型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SemanticModelVO update(Long id, SemanticModelUpdateRequest request) {
        requireSemanticOwnership(id);
        SemanticModelEntity entity = semanticModelMapper.selectById(id);
        if (request.getBusinessName() != null) {
            entity.setBusinessName(request.getBusinessName());
        }
        if (request.getBusinessDescription() != null) {
            entity.setBusinessDescription(request.getBusinessDescription());
        }
        if (request.getSynonyms() != null) {
            entity.setSynonyms(request.getSynonyms());
        }
        if (request.getExampleValues() != null) {
            entity.setExampleValues(request.getExampleValues());
        }
        if (request.getEnumValues() != null) {
            entity.setEnumValues(request.getEnumValues());
        }
        if (request.getUnit() != null) {
            entity.setUnit(request.getUnit());
        }
        if (request.getValueRange() != null) {
            entity.setValueRange(request.getValueRange());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        semanticModelMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 删除语义模型（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSemanticOwnership(id);
        semanticModelMapper.deleteById(id);
    }

    /**
     * 启用语义模型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        requireSemanticOwnership(id);
        SemanticModelEntity entity = semanticModelMapper.selectById(id);
        entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        semanticModelMapper.updateById(entity);
    }

    /**
     * 停用语义模型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        requireSemanticOwnership(id);
        SemanticModelEntity entity = semanticModelMapper.selectById(id);
        entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_DISABLED);
        semanticModelMapper.updateById(entity);
    }

    /**
     * 关键词搜索语义模型
     * <p>
     * 在 table_name, column_name, business_name, business_description, synonyms, column_comment 字段中做 LIKE 搜索
     */
    @Override
    public List<SemanticModelVO> searchByKeyword(Long datasourceId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listByDatasourceId(datasourceId);
        }
        datasourceManageService.checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<SemanticModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        wrapper.eq(SemanticModelEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.eq(SemanticModelEntity::getStatus, DataAgentConstants.SEMANTIC_STATUS_ENABLED);
        String likePattern = "%" + keyword + "%";
        wrapper.and(w -> {
            w.like(SemanticModelEntity::getTableName, likePattern)
                    .or().like(SemanticModelEntity::getColumnName, likePattern)
                    .or().like(SemanticModelEntity::getBusinessName, likePattern)
                    .or().like(SemanticModelEntity::getBusinessDescription, likePattern)
                    .or().like(SemanticModelEntity::getSynonyms, likePattern)
                    .or().like(SemanticModelEntity::getColumnComment, likePattern);
        });
        List<SemanticModelEntity> entities = semanticModelMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 从物理 Schema 自动初始化语义模型
     * <p>
     * 基于已有的 DatasourceColumnEntity 自动生成基础语义模型记录，
     * 仅创建不存在的记录，已存在的不覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoInitFromSchema(Long datasourceId) {
        datasourceManageService.checkDatasourceReadable(datasourceId);
        // 查询该数据源下所有表
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        List<DatasourceTableEntity> tables = datasourceTableMapper.selectList(tableWrapper);
        if (tables.isEmpty()) {
            return 0;
        }
        Set<Long> tableIds = tables.stream().map(DatasourceTableEntity::getId).collect(Collectors.toSet());

        // 查询该数据源下所有字段
        LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        colWrapper.in(DatasourceColumnEntity::getTableId, tableIds);
        List<DatasourceColumnEntity> columns = datasourceColumnMapper.selectList(colWrapper);
        if (columns.isEmpty()) {
            return 0;
        }

        // 查询已有的语义模型，构建已存在的 key 集合
        LambdaQueryWrapper<SemanticModelEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        List<SemanticModelEntity> existingModels = semanticModelMapper.selectList(existWrapper);
        Set<String> existingKeys = existingModels.stream()
                .map(e -> e.getTableName() + "." + e.getColumnName())
                .collect(Collectors.toSet());

        // 构建表ID到表名的映射
        java.util.Map<Long, String> tableNameMap = tables.stream()
                .collect(Collectors.toMap(DatasourceTableEntity::getId, DatasourceTableEntity::getTableName));

        // 为每个不存在语义模型的字段创建基础记录
        int createdCount = 0;
        for (DatasourceColumnEntity column : columns) {
            String tableName = tableNameMap.get(column.getTableId());
            if (tableName == null) {
                continue;
            }
            String key = tableName + "." + column.getColumnName();
            if (existingKeys.contains(key)) {
                continue;
            }
            SemanticModelEntity entity = new SemanticModelEntity();
            entity.setDatasourceId(datasourceId);
            entity.setTableName(tableName);
            entity.setColumnName(column.getColumnName());
            entity.setDataType(column.getDataType());
            entity.setColumnComment(column.getColumnComment());
            // 从 columnComment 填充 businessDescription
            if (column.getColumnComment() != null && !column.getColumnComment().isBlank()) {
                entity.setBusinessDescription(column.getColumnComment());
            }
            entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
            entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_ENABLED);
            entity.setDeleted(0);
            semanticModelMapper.insert(entity);
            createdCount++;
        }
        log.info("数据源 [{}] 自动初始化语义模型完成，新建 {} 条记录", datasourceId, createdCount);
        return createdCount;
    }

    /**
     * 校验当前用户对指定语义模型是否具有访问权限
     * <p>
     * 校验语义模型存在性 + workspaceId 一致性，不匹配抛出 BusinessException。
     */
    private void requireSemanticOwnership(Long id) {
        SemanticModelEntity entity = semanticModelMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "语义模型不存在: " + id);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该语义模型");
        }
    }

    /**
     * Entity 转 VO
     */
    private SemanticModelVO toVO(SemanticModelEntity entity) {
        SemanticModelVO vo = new SemanticModelVO();
        BeanUtils.copyProperties(entity, vo);
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
     * 从 Aloudata 指标平台同步语义模型
     * <p>
     * 指标平台的数据已经是业务语义化的（指标+维度），不需要映射到物理表/字段。
     * 映射规则：
     * <ul>
     *   <li>指标 → tableName=指标类目名称（或"指标"前缀）, columnName=metricName,
     *       businessName=metricDisplayName, businessDescription=businessCaliber,
     *       synonyms=同义词列表, unit=单位, dataType=type</li>
     *   <li>维度 → tableName=维度类目名称（或"维度"前缀）, columnName=dimName,
     *       businessName=dimDisplayName, businessDescription=dimDescription,
     *       synonyms=同义词列表, dataType=originDataType</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncFromAloudata(Long datasourceId) {
        // 校验数据源可读性（owner / meta_shared / 资源授权），写操作由 Controller @RequireGlobalAdmin 限制
        datasourceManageService.checkDatasourceReadable(datasourceId);
        // 取数据源类型判断（直接查 entity 避免权限校验二次执行）
        DatasourceEntity dsEntity = datasourceMapper.selectById(datasourceId);
        if (dsEntity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        if (!DataAgentConstants.SOURCE_TYPE_ALOUDATA.equals(dsEntity.getSourceType())) {
            throw new RuntimeException("仅支持 Aloudata 类型的数据源同步，当前类型: " + dsEntity.getSourceType());
        }

        // 查询已有的语义模型，构建已存在的 key 集合
        LambdaQueryWrapper<SemanticModelEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(SemanticModelEntity::getDatasourceId, datasourceId);
        List<SemanticModelEntity> existingModels = semanticModelMapper.selectList(existWrapper);
        Set<String> existingKeys = existingModels.stream()
                .map(e -> e.getTableName() + "." + e.getColumnName())
                .collect(Collectors.toSet());

        int createdCount = 0;

        // 1. 同步指标
        try {
            List<AloudataMetricSemanticDTO> metrics = aloudataService.listMetricSemantics(datasourceId);
            for (AloudataMetricSemanticDTO metric : metrics) {
                // tableName 使用类目名称，如果没有则使用"指标"
                String tableName = metric.getMetricCategoryName();
                if (tableName == null || tableName.isBlank()) {
                    tableName = ALOUDATA_METRIC_TABLE_PREFIX;
                }
                String columnName = metric.getMetricName();
                String key = tableName + "." + columnName;

                if (existingKeys.contains(key)) {
                    continue;
                }

                SemanticModelEntity entity = new SemanticModelEntity();
                entity.setDatasourceId(datasourceId);
                entity.setTableName(tableName);
                entity.setColumnName(columnName);
                entity.setBusinessName(metric.getMetricDisplayName());
                entity.setBusinessDescription(metric.getBusinessCaliber());
                entity.setDataType(metric.getType());
                entity.setUnit(metric.getUnit());
                // 同义词列表转逗号分隔
                if (metric.getSynonyms() != null && !metric.getSynonyms().isEmpty()) {
                    entity.setSynonyms(String.join(",", metric.getSynonyms()));
                }
                // 可用维度作为示例值（帮助 LLM 理解指标可按哪些维度拆解）
                if (metric.getAvailableDimensions() != null && !metric.getAvailableDimensions().isEmpty()) {
                    entity.setExampleValues("可用维度: " + String.join(",", metric.getAvailableDimensions()));
                }
                entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
                entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_ENABLED);
                entity.setDeleted(0);
                semanticModelMapper.insert(entity);
                existingKeys.add(key);
                createdCount++;
            }
            log.info("数据源 [{}] 从 Aloudata 同步指标语义模型完成，新建 {} 条", datasourceId, createdCount);
        } catch (Exception e) {
            log.error("数据源 [{}] 从 Aloudata 同步指标语义模型失败: {}", datasourceId, e.getMessage());
            throw new RuntimeException("同步指标语义模型失败: " + e.getMessage(), e);
        }

        // 2. 同步维度
        try {
            int dimCreated = 0;
            List<AloudataDimensionSemanticDTO> dimensions = aloudataService.listDimensionSemantics(datasourceId);
            for (AloudataDimensionSemanticDTO dim : dimensions) {
                String tableName = ALOUDATA_DIMENSION_TABLE_PREFIX;
                String columnName = dim.getDimName();
                String key = tableName + "." + columnName;

                if (existingKeys.contains(key)) {
                    continue;
                }

                SemanticModelEntity entity = new SemanticModelEntity();
                entity.setDatasourceId(datasourceId);
                entity.setTableName(tableName);
                entity.setColumnName(columnName);
                entity.setBusinessName(dim.getDimDisplayName());
                entity.setBusinessDescription(dim.getDimDescription());
                entity.setDataType(dim.getOriginDataType());
                // 同义词列表转逗号分隔
                if (dim.getSynonyms() != null && !dim.getSynonyms().isEmpty()) {
                    entity.setSynonyms(String.join(",", dim.getSynonyms()));
                }
                entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
                entity.setStatus(DataAgentConstants.SEMANTIC_STATUS_ENABLED);
                entity.setDeleted(0);
                semanticModelMapper.insert(entity);
                existingKeys.add(key);
                dimCreated++;
            }
            createdCount += dimCreated;
            log.info("数据源 [{}] 从 Aloudata 同步维度语义模型完成，新建 {} 条", datasourceId, dimCreated);
        } catch (Exception e) {
            log.error("数据源 [{}] 从 Aloudata 同步维度语义模型失败: {}", datasourceId, e.getMessage());
            // 维度同步失败不影响指标同步结果，仅记录日志
        }

        return createdCount;
    }
}
