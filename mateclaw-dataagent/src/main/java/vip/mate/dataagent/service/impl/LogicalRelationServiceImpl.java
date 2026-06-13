package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.LogicalRelationCreateRequest;
import vip.mate.dataagent.dto.LogicalRelationUpdateRequest;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.model.DatasourceColumnEntity;
import vip.mate.dataagent.model.DatasourceTableEntity;
import vip.mate.dataagent.model.LogicalRelationEntity;
import vip.mate.dataagent.repository.DatasourceColumnMapper;
import vip.mate.dataagent.repository.DatasourceTableMapper;
import vip.mate.dataagent.repository.LogicalRelationMapper;
import vip.mate.dataagent.service.LogicalRelationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 逻辑外键关系服务实现
 */
@Service
@RequiredArgsConstructor
public class LogicalRelationServiceImpl implements LogicalRelationService {

    private static final Logger log = LoggerFactory.getLogger(LogicalRelationServiceImpl.class);

    private final LogicalRelationMapper logicalRelationMapper;
    private final DatasourceColumnMapper datasourceColumnMapper;
    private final DatasourceTableMapper datasourceTableMapper;

    /**
     * 按数据源查询所有逻辑外键关系
     */
    @Override
    public List<LogicalRelationVO> listByDatasourceId(Long datasourceId) {
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        wrapper.eq(LogicalRelationEntity::getDeleted, 0);
        List<LogicalRelationEntity> entities = logicalRelationMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按数据源和表名查询关联的逻辑外键关系
     * <p>
     * 同时匹配源表和目标表
     */
    @Override
    public List<LogicalRelationVO> listByDatasourceIdAndTableNames(Long datasourceId, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        wrapper.and(w -> {
            w.in(LogicalRelationEntity::getSourceTableName, tableNames)
                    .or().in(LogicalRelationEntity::getTargetTableName, tableNames);
        });
        wrapper.eq(LogicalRelationEntity::getDeleted, 0);
        List<LogicalRelationEntity> entities = logicalRelationMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取逻辑外键关系
     */
    @Override
    public LogicalRelationVO getById(Long id) {
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        return toVO(entity);
    }

    /**
     * 创建逻辑外键关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogicalRelationVO create(LogicalRelationCreateRequest request) {
        // 检查唯一约束：同一数据源下相同源表+源字段+目标表+目标字段不允许重复
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, request.getDatasourceId());
        wrapper.eq(LogicalRelationEntity::getSourceTableName, request.getSourceTableName());
        wrapper.eq(LogicalRelationEntity::getSourceColumnName, request.getSourceColumnName());
        wrapper.eq(LogicalRelationEntity::getTargetTableName, request.getTargetTableName());
        wrapper.eq(LogicalRelationEntity::getTargetColumnName, request.getTargetColumnName());
        wrapper.eq(LogicalRelationEntity::getDeleted, 0);
        Long count = logicalRelationMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("逻辑外键关系已存在: "
                    + request.getSourceTableName() + "." + request.getSourceColumnName()
                    + " -> " + request.getTargetTableName() + "." + request.getTargetColumnName());
        }
        LogicalRelationEntity entity = new LogicalRelationEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setDeleted(0);
        if (entity.getRelationType() == null || entity.getRelationType().isBlank()) {
            entity.setRelationType(DataAgentConstants.RELATION_TYPE_ONE_TO_MANY);
        }
        logicalRelationMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 更新逻辑外键关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogicalRelationVO update(Long id, LogicalRelationUpdateRequest request) {
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        if (request.getRelationType() != null) {
            entity.setRelationType(request.getRelationType());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        logicalRelationMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 删除逻辑外键关系（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setDeleted(1);
        logicalRelationMapper.updateById(entity);
    }

    /**
     * 从物理外键自动初始化逻辑外键关系
     * <p>
     * 基于已有的 DatasourceColumnEntity 中的 foreignKeyTable/foreignKeyColumn 信息，
     * 自动生成逻辑外键关系记录，仅创建不存在的记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoInitFromPhysicalForeignKeys(Long datasourceId) {
        // 查询该数据源下所有表
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        tableWrapper.eq(DatasourceTableEntity::getDeleted, 0);
        List<DatasourceTableEntity> tables = datasourceTableMapper.selectList(tableWrapper);
        if (tables.isEmpty()) {
            return 0;
        }

        // 构建表ID到表名的映射
        Map<Long, String> tableNameMap = tables.stream()
                .collect(Collectors.toMap(DatasourceTableEntity::getId, DatasourceTableEntity::getTableName));

        // 查询该数据源下有外键信息的字段
        LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        colWrapper.isNotNull(DatasourceColumnEntity::getForeignKeyTable);
        colWrapper.ne(DatasourceColumnEntity::getForeignKeyTable, "");
        colWrapper.eq(DatasourceColumnEntity::getDeleted, 0);
        List<DatasourceColumnEntity> fkColumns = datasourceColumnMapper.selectList(colWrapper);
        if (fkColumns.isEmpty()) {
            return 0;
        }

        // 查询已有的逻辑外键关系，构建已存在的 key 集合
        LambdaQueryWrapper<LogicalRelationEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        existWrapper.eq(LogicalRelationEntity::getDeleted, 0);
        List<LogicalRelationEntity> existingRelations = logicalRelationMapper.selectList(existWrapper);
        Set<String> existingKeys = existingRelations.stream()
                .map(e -> e.getSourceTableName() + "." + e.getSourceColumnName()
                        + "->" + e.getTargetTableName() + "." + e.getTargetColumnName())
                .collect(Collectors.toSet());

        // 为每个不存在逻辑关系的字段创建一条记录
        int createdCount = 0;
        for (DatasourceColumnEntity column : fkColumns) {
            String sourceTableName = tableNameMap.get(column.getTableId());
            if (sourceTableName == null) {
                continue;
            }
            String sourceColumnName = column.getColumnName();
            String targetTableName = column.getForeignKeyTable();
            String targetColumnName = column.getForeignKeyColumn();
            if (targetColumnName == null || targetColumnName.isBlank()) {
                continue;
            }
            String key = sourceTableName + "." + sourceColumnName
                    + "->" + targetTableName + "." + targetColumnName;
            if (existingKeys.contains(key)) {
                continue;
            }
            LogicalRelationEntity entity = new LogicalRelationEntity();
            entity.setDatasourceId(datasourceId);
            entity.setSourceTableName(sourceTableName);
            entity.setSourceColumnName(sourceColumnName);
            entity.setTargetTableName(targetTableName);
            entity.setTargetColumnName(targetColumnName);
            entity.setRelationType(DataAgentConstants.RELATION_TYPE_MANY_TO_ONE);
            entity.setDeleted(0);
            logicalRelationMapper.insert(entity);
            createdCount++;
        }
        log.info("数据源 [{}] 自动初始化逻辑外键关系完成，新建 {} 条记录", datasourceId, createdCount);
        return createdCount;
    }

    /**
     * Entity 转 VO
     */
    private LogicalRelationVO toVO(LogicalRelationEntity entity) {
        LogicalRelationVO vo = new LogicalRelationVO();
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
}
