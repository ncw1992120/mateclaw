package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.dto.DatasourceAccountRequest;
import vip.mate.dataagent.dto.DatasourceAccountVO;
import vip.mate.dataagent.model.DatasourceAccountEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceAccountMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataService;
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
public class DatasourceAccountServiceImpl implements DatasourceAccountService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceAccountServiceImpl.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatasourceAccountMapper datasourceAccountMapper;
    private final DatasourceMapper datasourceMapper;
    /** @Lazy 打破与 AloudataServiceImpl 的循环依赖 */
    private final AloudataService aloudataService;
    private final AloudataConfigHelper aloudataConfigHelper;

    public DatasourceAccountServiceImpl(
            DatasourceAccountMapper datasourceAccountMapper,
            DatasourceMapper datasourceMapper,
            @Lazy AloudataService aloudataService,
            AloudataConfigHelper aloudataConfigHelper) {
        this.datasourceAccountMapper = datasourceAccountMapper;
        this.datasourceMapper = datasourceMapper;
        this.aloudataService = aloudataService;
        this.aloudataConfigHelper = aloudataConfigHelper;
    }

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
     * <p>
     * 按数据源类型区分测试方式：
     * <ul>
     *   <li>Aloudata 类型：用用户 auth-value 调用 Aloudata HTTP API 测试</li>
     *   <li>JDBC 类型：用用户 username/password 走 JDBC 连接测试</li>
     * </ul>
     * <p>
     * 支持传入临时账号参数进行预测试，此时不修改数据库；
     * 不传参数时使用已绑定的账号测试并更新 last_test_time / last_test_ok。
     */
    @Override
    public boolean testAccountConnection(Long datasourceId, Long userId, DatasourceAccountRequest request) {
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在, id=" + datasourceId);
        }

        boolean ok;
        boolean isTempTest = request != null && request.getQueryPassword() != null && !request.getQueryPassword().isEmpty();

        if (isTempTest) {
            // 临时测试：使用传入的参数，不查询数据库，不持久化
            DatasourceAccountEntity tempAccount = new DatasourceAccountEntity();
            tempAccount.setQueryUsername(request.getQueryUsername());
            tempAccount.setQueryPassword(request.getQueryPassword());
            if (DataAgentConstants.SOURCE_TYPE_ALOUDATA.equals(datasource.getSourceType())) {
                ok = testAloudataAccountConnection(datasource, tempAccount);
            } else {
                ok = testJdbcAccountConnection(datasource, tempAccount);
            }
            return ok;
        }

        // 非临时测试：使用已绑定的账号
        DatasourceAccountEntity account = getByDatasourceIdAndUserId(datasourceId, userId);
        if (account == null) {
            throw new IllegalArgumentException("未找到该用户在此数据源上的查询账号绑定");
        }

        if (DataAgentConstants.SOURCE_TYPE_ALOUDATA.equals(datasource.getSourceType())) {
            ok = testAloudataAccountConnection(datasource, account);
        } else {
            ok = testJdbcAccountConnection(datasource, account);
        }

        // 更新测试结果
        account.setLastTestTime(LocalDateTime.now());
        account.setLastTestOk(ok);
        datasourceAccountMapper.updateById(account);
        return ok;
    }

    /**
     * Aloudata 类型查询账号连接测试
     * <p>
     * 用用户绑定的 auth-value（存于 queryPassword 字段）覆盖管理员 auth-value，
     * 调用 Aloudata HTTP API（category_list 端点）验证连通性。
     */
    private boolean testAloudataAccountConnection(DatasourceEntity datasource, DatasourceAccountEntity account) {
        try {
            AloudataConfigDTO config = aloudataConfigHelper.parseConfig(datasource);
            // 覆盖为用户自己的 auth-value（存于 queryPassword 字段）
            config.setAuthValue(account.getQueryPassword());
            return aloudataService.testConnection(config);
        } catch (Exception e) {
            log.warn("Aloudata 查询账号连接测试失败: datasourceId={}, userId={}, error={}",
                    datasource.getId(), account.getUserId(), e.getMessage());
            return false;
        }
    }

    /**
     * JDBC 类型查询账号连接测试
     * <p>
     * 复用数据源的连接信息（host/port/database），但用用户自己的查询账号连接。
     */
    private boolean testJdbcAccountConnection(DatasourceEntity datasource, DatasourceAccountEntity account) {
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
            return true;
        } catch (Exception e) {
            log.warn("JDBC 查询账号连接测试失败: datasourceId={}, userId={}, error={}",
                    datasource.getId(), account.getUserId(), e.getMessage());
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
