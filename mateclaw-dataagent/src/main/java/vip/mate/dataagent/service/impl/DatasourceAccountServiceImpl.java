package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.dto.DatasourceAccountRequest;
import vip.mate.dataagent.dto.DatasourceAccountVO;
import vip.mate.dataagent.model.DatasourceAccountEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceAccountMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.DatasourceAccountService;
import vip.mate.dataagent.util.JdbcUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源用户查询账号服务实现
 */
@Service
@RequiredArgsConstructor
public class DatasourceAccountServiceImpl implements DatasourceAccountService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceAccountServiceImpl.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatasourceAccountMapper datasourceAccountMapper;
    private final DatasourceMapper datasourceMapper;

    /**
     * 查询用户在指定数据源上绑定的查询账号
     */
    @Override
    public DatasourceAccountEntity getByDatasourceIdAndUserId(Long datasourceId, Long userId) {
        LambdaQueryWrapper<DatasourceAccountEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceAccountEntity::getDatasourceId, datasourceId)
                .eq(DatasourceAccountEntity::getUserId, userId)
                .eq(DatasourceAccountEntity::getDeleted, 0);
        return datasourceAccountMapper.selectOne(wrapper);
    }

    /**
     * 解析当前用户在指定 Aloudata 数据源上的认证值（auth-value）
     */
    @Override
    public String resolveAloudataAuthValue(Long datasourceId, Long userId) {
        if (datasourceId == null || userId == null) {
            return null;
        }
        DatasourceAccountEntity account = getByDatasourceIdAndUserId(datasourceId, userId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            return null;
        }
        return account.getQueryPassword();
    }

    /**
     * 查询用户所有已绑定的查询账号
     */
    @Override
    public List<DatasourceAccountVO> listByUserId(Long userId) {
        LambdaQueryWrapper<DatasourceAccountEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceAccountEntity::getUserId, userId)
                .eq(DatasourceAccountEntity::getDeleted, 0);
        List<DatasourceAccountEntity> entities = datasourceAccountMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 创建或更新用户查询账号绑定（upsert）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasourceAccountVO upsertAccount(DatasourceAccountRequest request, Long userId) {
        DatasourceEntity datasource = datasourceMapper.selectById(request.getDatasourceId());
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在, id=" + request.getDatasourceId());
        }

        DatasourceAccountEntity existing = getByDatasourceIdAndUserId(request.getDatasourceId(), userId);
        if (existing != null) {
            existing.setQueryUsername(request.getQueryUsername());
            existing.setQueryPassword(request.getQueryPassword());
            existing.setStatus(1);
            datasourceAccountMapper.updateById(existing);
            return toVO(existing);
        }

        DatasourceAccountEntity entity = new DatasourceAccountEntity();
        entity.setDatasourceId(request.getDatasourceId());
        entity.setWorkspaceId(datasource.getWorkspaceId());
        entity.setUserId(userId);
        entity.setQueryUsername(request.getQueryUsername());
        entity.setQueryPassword(request.getQueryPassword());
        entity.setStatus(1);
        entity.setDeleted(0);
        datasourceAccountMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 删除用户查询账号绑定（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long datasourceId, Long userId) {
        LambdaUpdateWrapper<DatasourceAccountEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DatasourceAccountEntity::getDatasourceId, datasourceId)
                .eq(DatasourceAccountEntity::getUserId, userId)
                .eq(DatasourceAccountEntity::getDeleted, 0)
                .set(DatasourceAccountEntity::getDeleted, 1);
        datasourceAccountMapper.update(null, wrapper);
    }

    /**
     * 测试用户查询账号连接
     */
    @Override
    public boolean testAccountConnection(Long datasourceId, Long userId) {
        DatasourceAccountEntity account = getByDatasourceIdAndUserId(datasourceId, userId);
        if (account == null) {
            throw new IllegalArgumentException("未找到该用户在此数据源上的查询账号绑定");
        }

        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在, id=" + datasourceId);
        }

        // 使用数据源的连接信息（host/port/database），但用用户自己的查询账号
        DatasourceEntity tempEntity = new DatasourceEntity();
        tempEntity.setSourceType(datasource.getSourceType());
        tempEntity.setHost(datasource.getHost());
        tempEntity.setPort(datasource.getPort());
        tempEntity.setDatabaseName(datasource.getDatabaseName());
        tempEntity.setSchemaName(datasource.getSchemaName());
        tempEntity.setUsername(account.getQueryUsername());
        tempEntity.setPassword(account.getQueryPassword());

        String jdbcUrl = JdbcUtils.buildJdbcUrl(tempEntity);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, account.getQueryUsername(), account.getQueryPassword())) {
            // 更新测试结果
            account.setLastTestTime(LocalDateTime.now());
            account.setLastTestOk(true);
            datasourceAccountMapper.updateById(account);
            return true;
        } catch (Exception e) {
            log.warn("查询账号连接测试失败: datasourceId={}, userId={}, error={}", datasourceId, userId, e.getMessage());
            account.setLastTestTime(LocalDateTime.now());
            account.setLastTestOk(false);
            datasourceAccountMapper.updateById(account);
            return false;
        }
    }

    /**
     * 实体转视图对象
     */
    private DatasourceAccountVO toVO(DatasourceAccountEntity entity) {
        DatasourceAccountVO vo = new DatasourceAccountVO();
        vo.setId(entity.getId());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setQueryUsername(entity.getQueryUsername());
        vo.setStatus(entity.getStatus());
        vo.setLastTestOk(entity.getLastTestOk());
        vo.setCreateTime(formatTime(entity.getCreateTime()));
        vo.setUpdateTime(formatTime(entity.getUpdateTime()));
        if (entity.getLastTestTime() != null) {
            vo.setLastTestTime(formatTime(entity.getLastTestTime()));
        }

        // 填充数据源名称和类型
        DatasourceEntity datasource = datasourceMapper.selectById(entity.getDatasourceId());
        if (datasource != null) {
            vo.setDatasourceName(datasource.getName());
            vo.setDatasourceType(datasource.getSourceType());
        }
        return vo;
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.format(FORMATTER);
    }
}
