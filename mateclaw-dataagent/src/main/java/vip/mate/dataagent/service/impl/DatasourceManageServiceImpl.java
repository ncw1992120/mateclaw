package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.auth.service.PermissionChecker;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.DatasourceColumnEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.model.DatasourceTableEntity;
import vip.mate.dataagent.model.ResourceGrantEntity;
import vip.mate.dataagent.repository.DatasourceColumnMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.repository.DatasourceTableMapper;
import vip.mate.dataagent.repository.ResourceGrantMapper;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceManageService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.scheduling.support.CronExpression;

/**
 * 数据源管理服务实现
 */
@Service
@RequiredArgsConstructor
public class DatasourceManageServiceImpl implements DatasourceManageService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceManageServiceImpl.class);
    private final DatasourceMapper datasourceMapper;
    private final DatasourceTableMapper datasourceTableMapper;
    private final DatasourceColumnMapper datasourceColumnMapper;
    private final ResourceGrantMapper resourceGrantMapper;
    private final AloudataService aloudataService;
    private final AloudataConfigHelper aloudataConfigHelper;
    private final PermissionChecker permissionChecker;
    private final WorkspaceGuard workspaceGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取数据源列表
     * <p>
     * 返回当前用户可见的数据源：自己创建的 + 被授权给自己的（view/use/edit 任一权限）。
     *
     * @param ownerId 数据源创建者用户 ID，null 时不按 owner 过滤（仅供内部工具调用）
     * @return 数据源列表
     */
    @Override
    public List<DatasourceVO> listDatasources(Long ownerId) {
        // 内部工具调用：不过滤 owner，也不按授权表过滤，但必须过滤掉未启用（enabled=false）的数据源——
        // 该路径仅供 LLM 侧使用（对话系统提示的数据源列表、list_datasources 工具），未勾选时更需
        // 确保只会暴露"可用"的数据源，避免把停用数据源当可选项推给 LLM 导致误查。
        if (ownerId == null) {
            LambdaQueryWrapper<DatasourceEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DatasourceEntity::getEnabled, true);
            List<DatasourceEntity> entities = datasourceMapper.selectList(wrapper);
            return entities.stream().map(this::toVO).collect(Collectors.toList());
        }

        Set<Long> visibleIds = new HashSet<>();

        // 1. 自己创建的数据源
        LambdaQueryWrapper<DatasourceEntity> ownWrapper = new LambdaQueryWrapper<>();
        ownWrapper.eq(DatasourceEntity::getOwnerId, ownerId);
        List<DatasourceEntity> ownEntities = datasourceMapper.selectList(ownWrapper);
        visibleIds.addAll(ownEntities.stream().map(DatasourceEntity::getId).collect(Collectors.toSet()));

        // 2. 通过资源授权表获得的数据源（按用户授权 + 按角色授权）
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        Long userId = workspaceGuard.currentUserId();
        String userRole = workspaceGuard.getWorkspaceMemberRole(workspaceId, userId);

        LambdaQueryWrapper<ResourceGrantEntity> grantWrapper = new LambdaQueryWrapper<>();
        grantWrapper.eq(ResourceGrantEntity::getWorkspaceId, workspaceId)
                .eq(ResourceGrantEntity::getResourceType, DataAgentConstants.RESOURCE_TYPE_DATASOURCE)
                .eq(ResourceGrantEntity::getStatus, DataAgentConstants.GRANT_STATUS_ACTIVE)
                .and(w -> w.isNull(ResourceGrantEntity::getExpireTime)
                        .or()
                        .gt(ResourceGrantEntity::getExpireTime, LocalDateTime.now()));

        // 按用户授权 或 按角色授权
        if (userRole != null) {
            grantWrapper.and(w -> w.eq(ResourceGrantEntity::getGrantType, DataAgentConstants.GRANT_TYPE_USER)
                    .eq(ResourceGrantEntity::getGranteeId, String.valueOf(userId))
                    .or()
                    .eq(ResourceGrantEntity::getGrantType, DataAgentConstants.GRANT_TYPE_ROLE)
                    .eq(ResourceGrantEntity::getGranteeId, userRole));
        } else {
            grantWrapper.eq(ResourceGrantEntity::getGrantType, DataAgentConstants.GRANT_TYPE_USER)
                    .eq(ResourceGrantEntity::getGranteeId, String.valueOf(userId));
        }

        List<ResourceGrantEntity> grants = resourceGrantMapper.selectList(grantWrapper);
        visibleIds.addAll(grants.stream().map(ResourceGrantEntity::getResourceId).collect(Collectors.toSet()));

        // 3. 工作区内元数据共享的数据源（仅 view 权限，用于元数据查询场景）
        LambdaQueryWrapper<DatasourceEntity> sharedWrapper = new LambdaQueryWrapper<>();
        sharedWrapper.eq(DatasourceEntity::getWorkspaceId, workspaceId)
                .eq(DatasourceEntity::getMetaShared, true);
        List<DatasourceEntity> sharedEntities = datasourceMapper.selectList(sharedWrapper);
        visibleIds.addAll(sharedEntities.stream()
                .map(DatasourceEntity::getId).collect(Collectors.toSet()));

        if (visibleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DatasourceEntity> entities = datasourceMapper.selectList(
                new LambdaQueryWrapper<DatasourceEntity>()
                        .in(DatasourceEntity::getId, visibleIds));
        return entities.stream()
                .map(e -> toVO(e, resolveDatasourcePermission(e)))
                .collect(Collectors.toList());
    }

    /**
     * 解析当前用户对指定数据源的最高权限
     * <p>
     * owner 本人返回 edit，否则通过 PermissionChecker 解析授权表权限。
     * 共享数据源（meta_shared=true）对非 owner 用户返回 view（仅元数据可读，不可编辑）。
     * 无任何权限返回 null。
     */
    private String resolveDatasourcePermission(DatasourceEntity entity) {
        Long currentUserId = workspaceGuard.currentUserId();
        if (currentUserId.equals(entity.getOwnerId())) {
            return DataAgentConstants.PERMISSION_EDIT;
        }
        String permission = permissionChecker.resolveHighestPermission(
                DataAgentConstants.RESOURCE_TYPE_DATASOURCE, entity.getId());
        if (permission != null) {
            return permission;
        }
        // 共享数据源对非 owner 用户授予 view 权限（仅元数据可读，不可编辑）
        if (Boolean.TRUE.equals(entity.getMetaShared())) {
            return DataAgentConstants.PERMISSION_VIEW;
        }
        return null;
    }

    /**
     * 获取数据源列表（全量，不过滤 owner）
     * <p>
     * 仅供内部工具调用，这些场景已通过用户勾选白名单和查询账号绑定约束数据范围。
     */
    @Override
    public List<DatasourceVO> listDatasources() {
        return listDatasources(null);
    }

    /**
     * 获取当前用户「可见且已启用」的数据源列表（LLM 侧专用）
     * <p>
     * 仅返回当前用户有权访问（自己创建 + 资源授权 + 工作区 meta_shared 共享）且 enabled=true 的数据源，
     * 供对话系统提示注入与 list_datasources 工具使用，避免把无权限或已停用的数据源暴露给 LLM。
     */
    @Override
    public List<DatasourceVO> listVisibleEnabledDatasources() {
        try {
            Long uid = workspaceGuard.currentUserId();
            Long wid = workspaceGuard.currentWorkspaceId();
            if (uid != null && wid != null) {
                // 权限感知路径：当前用户可见（自己创建 + 被授权 + 共享）且仅保留已启用
                return listDatasources(uid).stream()
                        .filter(ds -> !Boolean.FALSE.equals(ds.getEnabled()))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // 用户上下文不可用（如工具在无身份上下文的线程执行）时回退为全量已启用列表
            log.warn("按用户权限过滤数据源失败，回退为全量已启用列表: {}", e.getMessage());
        }
        return listDatasources().stream()
                .filter(ds -> !Boolean.FALSE.equals(ds.getEnabled()))
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取数据源
     * <p>
     * 使用宽松校验（checkDatasourceReadable）：共享数据源（meta_shared=true）对同工作区非 owner 用户可读。
     * 返回的 VO 中携带当前用户对该数据源的实际权限（owner=edit / 授权=view|use|edit / 共享=view）。
     */
    @Override
    public DatasourceVO getDatasource(Long id) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        // 校验工作区归属 + 可读性（owner / meta_shared / 资源授权）
        checkDatasourceReadable(entity);
        String permission = resolveDatasourcePermission(entity);
        return toVO(entity, permission);
    }

    /**
     * 校验当前用户对指定数据源是否具有访问权限
     * <p>
     * owner 本人自动放行；其余用户只要有 view/use/edit 任一授权即可访问。
     * 返回当前用户对该数据源的最高权限级别。
     */
    private String requireDatasourceAccess(DatasourceEntity entity) {
        Long currentUserId = workspaceGuard.currentUserId();
        if (currentUserId.equals(entity.getOwnerId())) {
            return DataAgentConstants.PERMISSION_EDIT;
        }
        String permission = permissionChecker.resolveHighestPermission(
                DataAgentConstants.RESOURCE_TYPE_DATASOURCE, entity.getId());
        if (permission == null) {
            throw new BusinessException(403, "无权访问该数据源: " + entity.getName());
        }
        return permission;
    }

    private String requireDatasourceAccess(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new BusinessException(404, "数据源不存在: " + datasourceId);
        }
        return requireDatasourceAccess(entity);
    }

    /**
     * 校验当前用户对指定数据源是否具有可读权限（owner / meta_shared / 资源授权）
     * <p>
     * 用于元数据查询场景，相比 requireDatasourceAccess 放宽：
     * 共享数据源（meta_shared=true）对同工作区所有用户可读。
     *
     * @param datasourceId 数据源 ID
     */
    @Override
    public void checkDatasourceReadable(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new BusinessException(404, "数据源不存在: " + datasourceId);
        }
        checkDatasourceReadable(entity);
    }

    /**
     * 校验当前用户对指定数据源是否具有可读权限（已持有 entity，避免重复查询）
     * <p>
     * 校验：存在性 + 工作区归属 + (owner / meta_shared / 资源授权) 任一条件满足。
     */
    private void checkDatasourceReadable(DatasourceEntity entity) {
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (entity.getWorkspaceId() == null
                || !entity.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该数据源: " + entity.getName());
        }
        Long currentUserId = workspaceGuard.currentUserId();
        // owner 自动放行
        if (currentUserId.equals(entity.getOwnerId())) {
            return;
        }
        // 共享数据源对同工作区所有用户可读
        if (Boolean.TRUE.equals(entity.getMetaShared())) {
            return;
        }
        // 资源授权校验
        String permission = permissionChecker.resolveHighestPermission(
                DataAgentConstants.RESOURCE_TYPE_DATASOURCE, entity.getId());
        if (permission == null) {
            throw new BusinessException(403, "无权访问该数据源: " + entity.getName());
        }
    }

    /**
     * 创建数据源
     * <p>
     * ownerId 由 Controller 层从登录上下文注入，记录数据源创建者。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceVO createDatasource(DatasourceCreateRequest request) {
        DatasourceEntity entity = new DatasourceEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setSchemaStatus("pending");
        entity.setDeleted(0);
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        datasourceMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 创建数据源（带 ownerId）
     *
     * @param request  创建请求
     * @param ownerId  数据源创建者用户 ID
     * @return 创建后的数据源视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceVO createDatasource(DatasourceCreateRequest request, Long ownerId) {
        DatasourceEntity entity = new DatasourceEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setOwnerId(ownerId);
        // 元数据共享：显式传入优先，否则默认 false
        if (request.getMetaShared() != null) {
            entity.setMetaShared(request.getMetaShared());
        } else {
            entity.setMetaShared(false);
        }
        entity.setSchemaStatus("pending");
        entity.setDeleted(0);
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        datasourceMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 更新数据源
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceVO updateDatasource(Long id, DatasourceUpdateRequest request) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        requireDatasourceAccess(entity);
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSourceType() != null) {
            entity.setSourceType(request.getSourceType());
        }
        if (request.getHost() != null) {
            entity.setHost(request.getHost());
        }
        if (request.getProductHost() != null) {
            entity.setProductHost(request.getProductHost());
        }
        if (request.getSemanticHost() != null) {
            entity.setSemanticHost(request.getSemanticHost());
        }
        if (request.getPort() != null) {
            entity.setPort(request.getPort());
        }
        if (request.getDatabaseName() != null) {
            entity.setDatabaseName(request.getDatabaseName());
        }
        if (request.getUsername() != null) {
            entity.setUsername(request.getUsername());
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            entity.setPassword(request.getPassword());
        }
        if (request.getConnectionParams() != null) {
            entity.setConnectionParams(request.getConnectionParams());
        }
        if (request.getSchemaName() != null) {
            entity.setSchemaName(request.getSchemaName());
        }
        if (request.getMetaShared() != null) {
            entity.setMetaShared(request.getMetaShared());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        // Aloudata 语义层定时同步配置：仅 Aloudata 数据源允许设置，cron 必须合法
        if (request.getAloudataSyncEnabled() != null || request.getAloudataSyncCron() != null) {
            if (!DataAgentConstants.SOURCE_TYPE_ALOUDATA.equalsIgnoreCase(entity.getSourceType())) {
                throw new BusinessException(400, "仅 Aloudata 指标平台数据源支持语义层定时同步");
            }
            if (request.getAloudataSyncEnabled() != null) {
                entity.setAloudataSyncEnabled(request.getAloudataSyncEnabled());
            }
            if (request.getAloudataSyncCron() != null) {
                String cron = request.getAloudataSyncCron().trim();
                if (!cron.isEmpty()) {
                    try {
                        // 页面使用 5 段 cron（分 时 日 月 周），补秒段构成 Spring 6 段后校验
                        CronExpression.parse("0 " + cron);
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(400, "定时同步 cron 表达式不合法: " + cron);
                    }
                }
                entity.setAloudataSyncCron(cron.isEmpty() ? null : cron);
            }
            // 兜底校验：开启定时同步后必须配置 cron 表达式，避免"开启但无定时"的无效状态
            if (Boolean.TRUE.equals(entity.getAloudataSyncEnabled())
                    && !org.springframework.util.StringUtils.hasText(entity.getAloudataSyncCron())) {
                throw new BusinessException(400, "开启定时同步后必须填写 cron 表达式");
            }
        }
        datasourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 删除数据源
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDatasource(Long id) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return;
        }
        datasourceMapper.deleteById(id);
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, id);
        List<DatasourceTableEntity> tables = datasourceTableMapper.selectList(tableWrapper);
        for (DatasourceTableEntity table : tables) {
            datasourceTableMapper.deleteById(table.getId());
            LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
            colWrapper.eq(DatasourceColumnEntity::getTableId, table.getId());
            List<DatasourceColumnEntity> columns = datasourceColumnMapper.selectList(colWrapper);
            for (DatasourceColumnEntity column : columns) {
                datasourceColumnMapper.deleteById(column.getId());
            }
        }
    }

    /**
     * 测试数据源连接
     */
    @Override
    public boolean testConnection(Long id) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        requireDatasourceAccess(entity);
        boolean connected = doTestConnection(entity);
        entity.setLastTestTime(LocalDateTime.now());
        entity.setLastTestOk(connected);
        datasourceMapper.updateById(entity);
        return connected;
    }

    /**
     * 使用连接参数测试数据源连通性（不持久化数据源记录）
     */
    @Override
    public boolean testConnectionByParams(DatasourceCreateRequest request) {
        DatasourceEntity entity = new DatasourceEntity();
        BeanUtils.copyProperties(request, entity);
        return doTestConnection(entity);
    }

    /**
     * 切换数据源启停状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceVO toggleDatasource(Long id, boolean enabled) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        requireDatasourceAccess(entity);
        entity.setEnabled(enabled);
        datasourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 触发 Schema 发现
     */
    @Override
    public DatasourceVO triggerSchemaDiscovery(Long id) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        entity.setSchemaStatus("running");
        entity.setLastSchemaDiscoveryTime(LocalDateTime.now());
        datasourceMapper.updateById(entity);
        try {
            discoverSchema(entity);
            entity.setSchemaStatus("completed");
        } catch (Exception e) {
            entity.setSchemaStatus("failed");
            log.error(e.getMessage());
        }
        entity.setLastSchemaDiscoveryTime(LocalDateTime.now());
        datasourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 获取数据源下的表列表
     */
    @Override
    public List<DatasourceTableVO> listTables(Long datasourceId) {
        checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        wrapper.orderByAsc(DatasourceTableEntity::getTableName);
        List<DatasourceTableEntity> tables = datasourceTableMapper.selectList(wrapper);
        return tables.stream().map(this::toTableVO).collect(Collectors.toList());
    }

    /**
     * 获取表详情（含字段列表）
     */
    @Override
    public DatasourceTableVO getTableDetail(Long datasourceId, Long tableId) {
        checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getId, tableId);
        wrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        DatasourceTableEntity table = datasourceTableMapper.selectOne(wrapper);
        if (table == null) {
            return null;
        }
        DatasourceTableVO vo = toTableVO(table);
        vo.setColumns(listColumns(datasourceId, tableId));
        vo.setColumnCount(vo.getColumns().size());
        return vo;
    }

    /**
     * 获取表字段列表
     */
    @Override
    public List<DatasourceColumnVO> listColumns(Long datasourceId, Long tableId) {
        checkDatasourceReadable(datasourceId);
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        wrapper.orderByAsc(DatasourceColumnEntity::getOrdinalPosition);
        List<DatasourceColumnEntity> columns = datasourceColumnMapper.selectList(wrapper);
        return columns.stream().map(this::toColumnVO).collect(Collectors.toList());
    }

    /**
     * 同步单张表的元数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceTableVO syncSingleTable(Long datasourceId, Long tableId, String mode) {
        DatasourceEntity datasourceEntity = datasourceMapper.selectById(datasourceId);
        if (datasourceEntity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        DatasourceTableEntity tableEntity = datasourceTableMapper.selectById(tableId);
        if (tableEntity == null || !tableEntity.getDatasourceId().equals(datasourceId)) {
            throw new RuntimeException("表不存在或与数据源不匹配: " + tableId);
        }
        String databaseName = datasourceEntity.getDatabaseName();
        String catalog = databaseName;
        String schemaPattern = null;
        String sourceType = datasourceEntity.getSourceType();
        if (DataAgentConstants.SOURCE_TYPE_POSTGRESQL.equals(sourceType)) {
            String schema = datasourceEntity.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                schema = "public";
            }
            schemaPattern = schema;
        } else if (DataAgentConstants.SOURCE_TYPE_ORACLE.equals(sourceType)) {
            String schema = datasourceEntity.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                schema = datasourceEntity.getUsername();
            }
            schemaPattern = schema;
        }
        String tableName = tableEntity.getTableName();
        if (DataAgentConstants.SYNC_MODE_OVERWRITE.equals(mode)) {
            syncTableOverwrite(datasourceEntity, tableEntity, catalog, schemaPattern, tableName);
        } else {
            syncTableAppend(datasourceEntity, tableEntity, catalog, schemaPattern, tableName);
        }
        return getTableDetail(datasourceId, tableId);
    }

    /**
     * 预览表数据
     */
    @Override
    public TableDataPreviewVO previewTableData(Long datasourceId, Long tableId, int limit) {
        if (limit <= 0) {
            limit = DataAgentConstants.PREVIEW_DEFAULT_LIMIT;
        }
        if (limit > DataAgentConstants.PREVIEW_MAX_LIMIT) {
            limit = DataAgentConstants.PREVIEW_MAX_LIMIT;
        }
        DatasourceEntity datasourceEntity = datasourceMapper.selectById(datasourceId);
        if (datasourceEntity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        checkDatasourceReadable(datasourceEntity);
        DatasourceTableEntity tableEntity = datasourceTableMapper.selectById(tableId);
        if (tableEntity == null || !tableEntity.getDatasourceId().equals(datasourceId)) {
            throw new RuntimeException("表不存在或与数据源不匹配: " + tableId);
        }
        String tableName = tableEntity.getTableName();
        TableDataPreviewVO vo = new TableDataPreviewVO();
        String jdbcUrl = buildJdbcUrl(datasourceEntity);
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, datasourceEntity.getUsername(), datasourceEntity.getPassword())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "` LIMIT " + limit)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnLabel(i));
                }
                vo.setColumns(columns);
                List<Map<String, Object>> rows = new ArrayList<>();
                long total = 0;
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                    total++;
                }
                vo.setRows(rows);
                vo.setTotal(total);
            }
        } catch (SQLException e) {
            throw new RuntimeException("预览表数据失败: " + e.getMessage(), e);
        }
        return vo;
    }

    /**
     * 删除数据源下的表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTable(Long datasourceId, Long tableId) {
        DatasourceTableEntity table = datasourceTableMapper.selectById(tableId);
        if (table == null || !table.getDatasourceId().equals(datasourceId)) {
            return;
        }
        datasourceTableMapper.deleteById(tableId);
        LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        List<DatasourceColumnEntity> columns = datasourceColumnMapper.selectList(colWrapper);
        for (DatasourceColumnEntity column : columns) {
            datasourceColumnMapper.deleteById(column.getId());
        }
    }

    /**
     * 执行连接测试
     */
    private boolean doTestConnection(DatasourceEntity entity) {
        try {
            String sourceType = entity.getSourceType();
            if (sourceType == null) {
                return false;
            }
            if (DataAgentConstants.SOURCE_TYPE_ALOUDATA.equals(sourceType)) {
                AloudataConfigDTO config = aloudataConfigHelper.parseConfig(entity);
                return aloudataService.testConnection(config);
            }
            return switch (sourceType) {
                case "mysql", "postgresql", "oracle", "clickhouse", "doris" -> testJdbcConnection(entity);
                default -> true;
            };
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * JDBC 连接测试
     */
    private boolean testJdbcConnection(DatasourceEntity entity) {
        try {
            String jdbcUrl = buildJdbcUrl(entity);
            Connection conn = DriverManager.getConnection(
                    jdbcUrl, entity.getUsername(), entity.getPassword());
            conn.close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(DatasourceEntity entity) {
        String sourceType = entity.getSourceType();
        String host = entity.getHost();
        Integer port = entity.getPort();
        String databaseName = entity.getDatabaseName();
        String extraParams = entity.getConnectionParams();
        StringBuilder url = new StringBuilder();
        switch (sourceType) {
            case "mysql":
                url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai");
                }
                break;
            case "postgresql":
                url.append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            case "oracle":
                url.append("jdbc:oracle:thin:@").append(host).append(":").append(port).append(":").append(databaseName);
                break;
            case "clickhouse":
                url.append("jdbc:clickhouse://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            case "doris":
                url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            default:
                url.append("jdbc:").append(sourceType).append("://").append(host).append(":").append(port).append("/").append(databaseName);
                break;
        }
        return url.toString();
    }

    /**
     * 执行 Schema 发现
     */
    private void discoverSchema(DatasourceEntity entity) {
        String sourceType = entity.getSourceType();
        if (sourceType == null) {
            return;
        }
        switch (sourceType) {
            case "mysql":
            case "postgresql":
            case "oracle":
            case "clickhouse":
            case "doris":
                discoverJdbcSchema(entity);
                break;
            default:
                break;
        }
    }

    /**
     * JDBC Schema 发现：自动扫描数据源，提取表结构、字段类型、主外键关系、索引信息
     */
    private void discoverJdbcSchema(DatasourceEntity entity) {
        String databaseName = entity.getDatabaseName();
        if (databaseName == null || databaseName.isEmpty()) {
            throw new RuntimeException("Schema 发现失败: 数据库名称(databaseName)不能为空");
        }
        String catalog = databaseName;
        String schemaPattern = null;
        String sourceType = entity.getSourceType();
        if ("postgresql".equals(sourceType)) {
            String schema = entity.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                schema = "public";
            }
            schemaPattern = schema;
        } else if ("oracle".equals(sourceType)) {
            String schema = entity.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                schema = entity.getUsername();
            }
            schemaPattern = schema;
        }
        cleanSchemaData(entity.getId());
        try (Connection conn = DriverManager.getConnection(
                buildJdbcUrl(entity), entity.getUsername(), entity.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tablesRs = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"});
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                String tableComment = tablesRs.getString("REMARKS");
                String tableType = tablesRs.getString("TABLE_TYPE");
                try {
                    discoverSingleTable(meta, entity, catalog, schemaPattern, tableName, tableComment, tableType);
                } catch (Exception e) {
                    log.warn("Schema 发现: 同步表 [{}] 时出错，跳过该表: {}", tableName, e.getMessage());
                }
            }
            tablesRs.close();
        } catch (SQLException e) {
            throw new RuntimeException("Schema 发现失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步单张表的元数据：字段、主键、外键、索引
     */
    private void discoverSingleTable(DatabaseMetaData meta, DatasourceEntity entity,
                                     String catalog, String schemaPattern,
                                     String tableName, String tableComment, String tableType) throws SQLException {
        DatasourceTableEntity tableEntity = new DatasourceTableEntity();
        tableEntity.setDatasourceId(entity.getId());
        tableEntity.setTableName(tableName);
        tableEntity.setTableComment(tableComment != null ? tableComment : "");
        tableEntity.setTableType(mapTableType(tableType));
        tableEntity.setSchemaName(schemaPattern != null ? schemaPattern : catalog);
        tableEntity.setDeleted(0);
        datasourceTableMapper.insert(tableEntity);
        ResultSet colsRs = meta.getColumns(catalog, schemaPattern, tableName, "%");
        int ordinal = 1;
        while (colsRs.next()) {
            DatasourceColumnEntity colEntity = new DatasourceColumnEntity();
            colEntity.setDatasourceId(entity.getId());
            colEntity.setTableId(tableEntity.getId());
            colEntity.setColumnName(colsRs.getString("COLUMN_NAME"));
            colEntity.setColumnComment(colsRs.getString("REMARKS"));
            colEntity.setDataType(colsRs.getString("TYPE_NAME"));
            colEntity.setColumnSize(colsRs.getInt("COLUMN_SIZE"));
            colEntity.setDecimalDigits(colsRs.getInt("DECIMAL_DIGITS"));
            colEntity.setNullable("YES".equals(colsRs.getString("IS_NULLABLE")));
            colEntity.setDefaultValue(colsRs.getString("COLUMN_DEF"));
            colEntity.setOrdinalPosition(ordinal++);
            colEntity.setDeleted(0);
            datasourceColumnMapper.insert(colEntity);
        }
        colsRs.close();
        try {
            ResultSet pkRs = meta.getPrimaryKeys(catalog, schemaPattern, tableName);
            while (pkRs.next()) {
                String pkCol = pkRs.getString("COLUMN_NAME");
                markPrimaryKey(entity.getId(), tableEntity.getId(), pkCol);
            }
            pkRs.close();
        } catch (SQLException e) {
            log.warn("Schema 发现: 获取表 [{}] 主键信息时出错: {}", tableName, e.getMessage());
        }
        try {
            ResultSet fkRs = meta.getCrossReference(catalog, schemaPattern, null, catalog, schemaPattern, tableName);
            while (fkRs.next()) {
                String fkCol = fkRs.getString("FKCOLUMN_NAME");
                String pkTable = fkRs.getString("PKTABLE_NAME");
                String pkCol = fkRs.getString("PKCOLUMN_NAME");
                markForeignKey(entity.getId(), tableEntity.getId(), fkCol, pkTable, pkCol);
            }
            fkRs.close();
        } catch (SQLException e) {
            log.warn("Schema 发现: 获取表 [{}] 外键信息时出错: {}", tableName, e.getMessage());
        }
        try {
            ResultSet idxRs = meta.getIndexInfo(catalog, schemaPattern, tableName, false, true);
            while (idxRs.next()) {
                String idxCol = idxRs.getString("COLUMN_NAME");
                if (idxCol != null) {
                    markIndexed(entity.getId(), tableEntity.getId(), idxCol);
                }
            }
            idxRs.close();
        } catch (SQLException e) {
            log.warn("Schema 发现: 获取表 [{}] 索引信息时出错: {}", tableName, e.getMessage());
        }
    }

    /**
     * 覆盖模式同步单表：先逻辑删除旧字段，再重新发现字段信息插入
     */
    private void syncTableOverwrite(DatasourceEntity datasourceEntity, DatasourceTableEntity tableEntity,
                                     String catalog, String schemaPattern, String tableName) {
        // 逻辑删除该表下所有旧字段
        LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(DatasourceColumnEntity::getTableId, tableEntity.getId());
        datasourceColumnMapper.delete(colWrapper);
        // 重新获取字段信息并插入
        try (Connection conn = DriverManager.getConnection(
                buildJdbcUrl(datasourceEntity), datasourceEntity.getUsername(), datasourceEntity.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            // 发现字段
            ResultSet colsRs = meta.getColumns(catalog, schemaPattern, tableName, "%");
            int ordinal = 1;
            while (colsRs.next()) {
                DatasourceColumnEntity colEntity = new DatasourceColumnEntity();
                colEntity.setDatasourceId(datasourceEntity.getId());
                colEntity.setTableId(tableEntity.getId());
                colEntity.setColumnName(colsRs.getString("COLUMN_NAME"));
                colEntity.setColumnComment(colsRs.getString("REMARKS"));
                colEntity.setDataType(colsRs.getString("TYPE_NAME"));
                colEntity.setColumnSize(colsRs.getInt("COLUMN_SIZE"));
                colEntity.setDecimalDigits(colsRs.getInt("DECIMAL_DIGITS"));
                colEntity.setNullable("YES".equals(colsRs.getString("IS_NULLABLE")));
                colEntity.setDefaultValue(colsRs.getString("COLUMN_DEF"));
                colEntity.setOrdinalPosition(ordinal++);
                colEntity.setDeleted(0);
                datasourceColumnMapper.insert(colEntity);
            }
            colsRs.close();
            // 发现主键
            try {
                ResultSet pkRs = meta.getPrimaryKeys(catalog, schemaPattern, tableName);
                while (pkRs.next()) {
                    String pkCol = pkRs.getString("COLUMN_NAME");
                    markPrimaryKey(datasourceEntity.getId(), tableEntity.getId(), pkCol);
                }
                pkRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 主键信息时出错: {}", tableName, e.getMessage());
            }
            // 发现外键
            try {
                ResultSet fkRs = meta.getCrossReference(catalog, schemaPattern, null, catalog, schemaPattern, tableName);
                while (fkRs.next()) {
                    String fkCol = fkRs.getString("FKCOLUMN_NAME");
                    String pkTable = fkRs.getString("PKTABLE_NAME");
                    String pkCol = fkRs.getString("PKCOLUMN_NAME");
                    markForeignKey(datasourceEntity.getId(), tableEntity.getId(), fkCol, pkTable, pkCol);
                }
                fkRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 外键信息时出错: {}", tableName, e.getMessage());
            }
            // 发现索引
            try {
                ResultSet idxRs = meta.getIndexInfo(catalog, schemaPattern, tableName, false, true);
                while (idxRs.next()) {
                    String idxCol = idxRs.getString("COLUMN_NAME");
                    if (idxCol != null) {
                        markIndexed(datasourceEntity.getId(), tableEntity.getId(), idxCol);
                    }
                }
                idxRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 索引信息时出错: {}", tableName, e.getMessage());
            }
        } catch (SQLException e) {
            throw new RuntimeException("覆盖同步表 [" + tableName + "] 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 追加模式同步单表：仅发现新字段（与旧字段按 columnName 对比，不存在的才插入）
     */
    private void syncTableAppend(DatasourceEntity datasourceEntity, DatasourceTableEntity tableEntity,
                                  String catalog, String schemaPattern, String tableName) {
        // 获取当前已有的字段名集合
        LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(DatasourceColumnEntity::getTableId, tableEntity.getId());
        List<DatasourceColumnEntity> existingColumns = datasourceColumnMapper.selectList(colWrapper);
        Set<String> existingNames = existingColumns.stream()
                .map(DatasourceColumnEntity::getColumnName)
                .collect(Collectors.toSet());
        // 获取当前最大序号
        int maxOrdinal = existingColumns.stream()
                .mapToInt(c -> c.getOrdinalPosition() != null ? c.getOrdinalPosition() : 0)
                .max().orElse(0);
        // 重新发现字段信息，只插入不存在的字段
        try (Connection conn = DriverManager.getConnection(
                buildJdbcUrl(datasourceEntity), datasourceEntity.getUsername(), datasourceEntity.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet colsRs = meta.getColumns(catalog, schemaPattern, tableName, "%");
            int ordinal = maxOrdinal + 1;
            while (colsRs.next()) {
                String colName = colsRs.getString("COLUMN_NAME");
                if (existingNames.contains(colName)) {
                    continue;
                }
                DatasourceColumnEntity colEntity = new DatasourceColumnEntity();
                colEntity.setDatasourceId(datasourceEntity.getId());
                colEntity.setTableId(tableEntity.getId());
                colEntity.setColumnName(colName);
                colEntity.setColumnComment(colsRs.getString("REMARKS"));
                colEntity.setDataType(colsRs.getString("TYPE_NAME"));
                colEntity.setColumnSize(colsRs.getInt("COLUMN_SIZE"));
                colEntity.setDecimalDigits(colsRs.getInt("DECIMAL_DIGITS"));
                colEntity.setNullable("YES".equals(colsRs.getString("IS_NULLABLE")));
                colEntity.setDefaultValue(colsRs.getString("COLUMN_DEF"));
                colEntity.setOrdinalPosition(ordinal++);
                colEntity.setDeleted(0);
                datasourceColumnMapper.insert(colEntity);
            }
            colsRs.close();
            // 发现主键（对新字段标记）
            try {
                ResultSet pkRs = meta.getPrimaryKeys(catalog, schemaPattern, tableName);
                while (pkRs.next()) {
                    String pkCol = pkRs.getString("COLUMN_NAME");
                    markPrimaryKey(datasourceEntity.getId(), tableEntity.getId(), pkCol);
                }
                pkRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 主键信息时出错: {}", tableName, e.getMessage());
            }
            // 发现外键（对新字段标记）
            try {
                ResultSet fkRs = meta.getCrossReference(catalog, schemaPattern, null, catalog, schemaPattern, tableName);
                while (fkRs.next()) {
                    String fkCol = fkRs.getString("FKCOLUMN_NAME");
                    String pkTable = fkRs.getString("PKTABLE_NAME");
                    String pkCol = fkRs.getString("PKCOLUMN_NAME");
                    markForeignKey(datasourceEntity.getId(), tableEntity.getId(), fkCol, pkTable, pkCol);
                }
                fkRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 外键信息时出错: {}", tableName, e.getMessage());
            }
            // 发现索引（对新字段标记）
            try {
                ResultSet idxRs = meta.getIndexInfo(catalog, schemaPattern, tableName, false, true);
                while (idxRs.next()) {
                    String idxCol = idxRs.getString("COLUMN_NAME");
                    if (idxCol != null) {
                        markIndexed(datasourceEntity.getId(), tableEntity.getId(), idxCol);
                    }
                }
                idxRs.close();
            } catch (SQLException e) {
                log.warn("单表同步: 获取表 [{}] 索引信息时出错: {}", tableName, e.getMessage());
            }
        } catch (SQLException e) {
            throw new RuntimeException("追加同步表 [" + tableName + "] 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理数据源下的旧 Schema 数据（表和字段），用于重新同步
     */
    private void cleanSchemaData(Long datasourceId) {
        LambdaQueryWrapper<DatasourceTableEntity> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(DatasourceTableEntity::getDatasourceId, datasourceId);
        List<DatasourceTableEntity> oldTables = datasourceTableMapper.selectList(tableWrapper);
        for (DatasourceTableEntity table : oldTables) {
            LambdaQueryWrapper<DatasourceColumnEntity> colWrapper = new LambdaQueryWrapper<>();
            colWrapper.eq(DatasourceColumnEntity::getTableId, table.getId());
            datasourceColumnMapper.delete(colWrapper);
            datasourceTableMapper.deleteById(table.getId());
        }
    }

    /**
     * 标记主键
     */
    private void markPrimaryKey(Long datasourceId, Long tableId, String columnName) {
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        wrapper.eq(DatasourceColumnEntity::getColumnName, columnName);
        DatasourceColumnEntity col = datasourceColumnMapper.selectOne(wrapper);
        if (col != null) {
            col.setPrimaryKey(true);
            datasourceColumnMapper.updateById(col);
        }
    }

    /**
     * 标记外键
     */
    private void markForeignKey(Long datasourceId, Long tableId, String columnName, String fkTable, String fkColumn) {
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        wrapper.eq(DatasourceColumnEntity::getColumnName, columnName);
        DatasourceColumnEntity col = datasourceColumnMapper.selectOne(wrapper);
        if (col != null) {
            col.setForeignKeyTable(fkTable);
            col.setForeignKeyColumn(fkColumn);
            datasourceColumnMapper.updateById(col);
        }
    }

    /**
     * 标记索引字段
     */
    private void markIndexed(Long datasourceId, Long tableId, String columnName) {
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getDatasourceId, datasourceId);
        wrapper.eq(DatasourceColumnEntity::getTableId, tableId);
        wrapper.eq(DatasourceColumnEntity::getColumnName, columnName);
        DatasourceColumnEntity col = datasourceColumnMapper.selectOne(wrapper);
        if (col != null) {
            col.setIndexed(true);
            datasourceColumnMapper.updateById(col);
        }
    }

    /**
     * 映射表类型
     */
    private String mapTableType(String jdbcTableType) {
        if (jdbcTableType == null) {
            return "table";
        }
        if (jdbcTableType.startsWith("TABLE")) {
            return "table";
        }
        if (jdbcTableType.startsWith("VIEW")) {
            return "view";
        }
        if (jdbcTableType.contains("MATERIALIZED")) {
            return "materialized_view";
        }
        return "external";
    }

    /**
     * Entity 转 VO（默认不脱敏，供内部工具等场景使用）
     */
    private DatasourceVO toVO(DatasourceEntity entity) {
        return toVO(entity, DataAgentConstants.PERMISSION_EDIT);
    }

    /**
     * Entity 转 VO
     *
     * @param permission 当前用户对该数据源的最高权限：view / use / edit
     */
    private DatasourceVO toVO(DatasourceEntity entity, String permission) {
        DatasourceVO vo = new DatasourceVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getLastTestTime() != null) {
            vo.setLastTestTime(entity.getLastTestTime().toString());
        }
        if (entity.getLastAloudataSyncTime() != null) {
            vo.setLastAloudataSyncTime(entity.getLastAloudataSyncTime().toString());
        }
        if (entity.getLastSchemaDiscoveryTime() != null) {
            vo.setLastSchemaDiscoveryTime(entity.getLastSchemaDiscoveryTime().toString());
        }
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        // use/view 权限不可查看连接配置等敏感信息（仅适用于资源授权 view/use 用户）
        if (DataAgentConstants.PERMISSION_USE.equals(permission)
                || DataAgentConstants.PERMISSION_VIEW.equals(permission)) {
            vo.setHost(null);
            vo.setProductHost(null);
            vo.setSemanticHost(null);
            vo.setPort(null);
            vo.setDatabaseName(null);
            vo.setUsername(null);
            vo.setPassword(null);
            vo.setConnectionParams(null);
            vo.setSchemaName(null);
        } else {
            // edit 权限：密码不再回显给前端，编辑时留空表示不修改
            vo.setPassword(null);
        }
        LambdaQueryWrapper<DatasourceTableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceTableEntity::getDatasourceId, entity.getId());
        vo.setTableCount(datasourceTableMapper.selectCount(wrapper).intValue());
        vo.setPermission(permission);
        return vo;
    }

    /**
     * TableEntity 转 TableVO
     */
    private DatasourceTableVO toTableVO(DatasourceTableEntity entity) {
        DatasourceTableVO vo = new DatasourceTableVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        LambdaQueryWrapper<DatasourceColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceColumnEntity::getTableId, entity.getId());
        vo.setColumnCount(datasourceColumnMapper.selectCount(wrapper).intValue());
        vo.setColumns(Collections.emptyList());
        return vo;
    }

    /**
     * ColumnEntity 转 ColumnVO
     */
    private DatasourceColumnVO toColumnVO(DatasourceColumnEntity entity) {
        DatasourceColumnVO vo = new DatasourceColumnVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

}