package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.crypto.AesPasswordCryptor;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.UserUidMappingStatusVO;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.model.UserUidMappingEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.repository.UserUidMappingMapper;
import vip.mate.dataagent.service.UserUidMappingService;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户 Aloudata UID 映射服务实现
 * <p>
 * 映射表为全局表（不带 workspace 隔离），按「登录名 + 租户」唯一定位一条映射；
 * 数据源的租户 ID 存储于 dataagent_datasource.username 字段（Aloudata 数据源约定），
 * 同一租户下的多个数据源共享一份映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserUidMappingServiceImpl implements UserUidMappingService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserUidMappingMapper userUidMappingMapper;
    private final DatasourceMapper datasourceMapper;
    private final AuthService authService;

    /**
     * 按登录名 + 租户查询映射记录
     */
    @Override
    public UserUidMappingEntity getByUsernameAndTenant(String username, String tenantId) {
        if (isBlank(username) || isBlank(tenantId)) {
            return null;
        }
        LambdaQueryWrapper<UserUidMappingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserUidMappingEntity::getUsername, AuthService.normalizeUsername(username))
                .eq(UserUidMappingEntity::getTenantId, tenantId);
        return userUidMappingMapper.selectOne(wrapper);
    }

    /**
     * 解析指定用户在指定 Aloudata 数据源租户下的自动映射认证值
     * <p>
     * 解析顺序：入参 userId 反查登录名（与手动绑定兜底同源，不依赖线程上下文）
     * → 数据源租户 ID → 映射表查询 → 解密 UID；
     * 任一环节缺失或映射未命中/停用时返回 null，由调用方回落到「无可用认证值」。
     */
    @Override
    public String resolveAutoAuthValue(Long datasourceId, Long userId) {
        if (datasourceId == null || userId == null) {
            return null;
        }
        String tenantId = resolveTenantId(datasourceId);
        if (tenantId == null) {
            return null;
        }
        UserEntity user = authService.findById(userId);
        UserUidMappingEntity mapping = getByUsernameAndTenant(user != null ? user.getUsername() : null, tenantId);
        if (mapping == null || mapping.getStatus() == null || mapping.getStatus() != 1) {
            return null;
        }
        // decrypt 幂等兜底：TypeHandler 已解密时为明文原样返回，旧构建/脏数据时为密文则解为明文
        return AesPasswordCryptor.decrypt(mapping.getAloudataUid());
    }

    /**
     * 查询当前登录用户全部启用的自动映射概要（租户维度）
     */
    @Override
    public List<UserUidMappingStatusVO> listMyEnabledMappings() {
        String username = UserContextHolder.get() != null ? UserContextHolder.get().getUsername() : null;
        if (isBlank(username)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<UserUidMappingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserUidMappingEntity::getUsername, AuthService.normalizeUsername(username))
                .eq(UserUidMappingEntity::getStatus, 1)
                .orderByAsc(UserUidMappingEntity::getTenantId);
        return userUidMappingMapper.selectList(wrapper).stream()
                .map(this::toStatusVO)
                .collect(Collectors.toList());
    }

    /**
     * 实体转映射状态视图对象
     */
    private UserUidMappingStatusVO toStatusVO(UserUidMappingEntity entity) {
        UserUidMappingStatusVO vo = new UserUidMappingStatusVO();
        vo.setTenantId(entity.getTenantId());
        if (entity.getSyncTime() != null) {
            vo.setSyncTime(entity.getSyncTime().format(FORMATTER));
        }
        return vo;
    }

    /**
     * 解析 Aloudata 数据源的租户 ID（存储于 datasource.username 字段）
     * <p>
     * 非 Aloudata 数据源直接返回 null：JDBC 数据源的 username 是数据库账号，
     * 若恰好与某个租户 ID 撞值会被误判为映射命中。
     */
    private String resolveTenantId(Long datasourceId) {
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null
                || !DataAgentConstants.SOURCE_TYPE_ALOUDATA.equalsIgnoreCase(datasource.getSourceType())) {
            return null;
        }
        return datasource.getUsername();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
