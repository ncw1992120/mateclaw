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
import vip.mate.dataagent.dto.LogicalRelationCreateRequest;
import vip.mate.dataagent.dto.LogicalRelationUpdateRequest;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.DatasourceColumnEntity;
import vip.mate.dataagent.model.DatasourceTableEntity;
import vip.mate.dataagent.model.LogicalRelationEntity;
import vip.mate.dataagent.repository.DatasourceColumnMapper;
import vip.mate.dataagent.repository.DatasourceTableMapper;
import vip.mate.dataagent.repository.LogicalRelationMapper;
import vip.mate.dataagent.service.DatasourceManageService;
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
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceManageService datasourceManageService;

    /**
     * 按数据源查询所有逻辑外键关系
     */
    @Override
    public List<LogicalRelationVO> listByDatasourceId(Long datasourceId) {
        datasourceManageService.getDatasource(datasourceId);
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        wrapper.eq(LogicalRelationEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
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
        datasourceManageService.getDatasource(datasourceId);
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
        wrapper.eq(LogicalRelationEntity::getWorkspaceId, workspaceGuard.currentWorkspaceId());
        wrapper.and(w -> {
            w.in(LogicalRelationEntity::getSourceTableName, tableNames)
                    .or().in(LogicalRelationEntity::getTargetTableName, tableNames);
        });
        List<LogicalRelationEntity> entities = logicalRelationMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取逻辑外键关系
     */
    @Override
    public LogicalRelationVO getById(Long id) {
        requireLogicalOwnership(id);
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
        return toVO(entity);
    }

    /**
     * 创建逻辑外键关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogicalRelationVO create(LogicalRelationCreateRequest request) {
        datasourceManageService.getDatasource(request.getDatasourceId());
        // 检查唯一约束：同一数据源下相同源表+源字段+目标表+目标字段不允许重复
        LambdaQueryWrapper<LogicalRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogicalRelationEntity::getDatasourceId, request.getDatasourceId());
        wrapper.eq(LogicalRelationEntity::getSourceTableName, request.getSourceTableName());
        wrapper.eq(LogicalRelationEntity::getSourceColumnName, request.getSourceColumnName());
        wrapper.eq(LogicalRelationEntity::getTargetTableName, request.getTargetTableName());
        wrapper.eq(LogicalRelationEntity::getTargetColumnName, request.getTargetColumnName());
        Long count = logicalRelationMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("逻辑外键关系已存在: "
                    + request.getSourceTableName() + "." + request.getSourceColumnName()
                    + " -> " + request.getTargetTableName() + "." + request.getTargetColumnName());
        }
        LogicalRelationEntity entity = new LogicalRelationEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        entity.setOwnerId(workspaceGuard.currentUserId());
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
        requireLogicalOwnership(id);
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
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
        requireLogicalOwnership(id);
        logicalRelationMapper.deleteById(id);
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
        datasourceManageService.getDatasource(datasourceId);
        // 查询该数据源下所有表
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
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
        List<DatasourceColumnEntity> fkColumns = datasourceColumnMapper.selectList(colWrapper);
        if (fkColumns.isEmpty()) {
            return 0;
        }

        // 查询已有的逻辑外键关系，构建已存在的 key 集合
        LambdaQueryWrapper<LogicalRelationEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LogicalRelationEntity::getDatasourceId, datasourceId);
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
            entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
            entity.setRelationType(DataAgentConstants.RELATION_TYPE_MANY_TO_ONE);
            entity.setDeleted(0);
            logicalRelationMapper.insert(entity);
            createdCount++;
        }
        log.info("数据源 [{}] 自动初始化逻辑外键关系完成，新建 {} 条记录", datasourceId, createdCount);
        return createdCount;
    }

    /**
     * 校验当前用户对指定逻辑外键关系是否具有访问权限
     * <p>
     * 校验存在性 + workspaceId 一致性；写操作另需满足：
     * 创建者本人 或 工作区 admin/owner（归属校验）。
     */
    private void requireLogicalOwnership(Long id) {
        LogicalRelationEntity entity = logicalRelationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "逻辑外键关系不存在: " + id);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该逻辑外键关系");
        }
        // 归属校验：仅创建者本人或工作区管理员层级可修改/删除
        workspaceGuard.requireResourceOwner(entity.getOwnerId());
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
