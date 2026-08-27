package vip.mate.dataagent.auth.enterprise;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.model.EnterpriseAccountEntity;
import vip.mate.dataagent.repository.EnterpriseAccountMapper;
import vip.mate.exception.MateClawException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 企业认证影子账号服务
 * <p>
 * 首次企业登录成功后自动在本地 mate_user 表开通同名影子账号：
 * <ul>
 *   <li>密码为随机值（BCrypt 存储），等效不可通过本地账密通道登录；</li>
 *   <li>默认 role=user、enabled=true，userId/工作区归属/权限矩阵照常生效；</li>
 *   <li>映射关系落 {@code dataagent_enterprise_account} 供审计与禁用联动。</li>
 * </ul>
 * 员工离职后领航侧认证自然失败，本地账号可由管理员手工停用兜底。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowAccountService {

    /** 身份来源标识：平安领航（含 UM 域账号 / AD 主机账号两种认证类型） */
    public static final String SOURCE_PILOT = "PILOT";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final EnterpriseAccountMapper enterpriseAccountMapper;

    /**
     * 确保企业身份对应的影子账号存在且可用，返回可用于签发 JWT 的本地用户。
     *
     * @param info 企业认证通过后的身份信息
     * @return 本地影子账号
     * @throws MateClawException 账号已被禁用（403）
     */
    @Transactional
    public UserEntity ensureShadowAccount(EnterpriseUserInfo info) {
        String username = info.principalName();
        UserEntity existing = authService.findByUsername(username);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                throw new MateClawException("err.auth.user_disabled", 403, "该账号已被禁用，请联系管理员");
            }
            recordLogin(username);
            return existing;
        }
        return createShadowAccount(info);
    }

    private UserEntity createShadowAccount(EnterpriseUserInfo info) {
        UserEntity created;
        try {
            created = doCreate(info);
        } catch (MateClawException e) {
            // 并发首次登录撞唯一键：另一请求已完成开通，直接复用
            if (e.getMsgKey() != null && e.getMsgKey().contains("username_exists")) {
                UserEntity winner = authService.findByUsername(info.principalName());
                if (winner != null && Boolean.TRUE.equals(winner.getEnabled())) {
                    recordLogin(info.principalName());
                    return winner;
                }
            }
            throw e;
        }
        EnterpriseAccountEntity mapping = new EnterpriseAccountEntity();
        mapping.setUsername(created.getUsername());
        mapping.setPrincipalName(info.principalName());
        mapping.setSource(SOURCE_PILOT);
        mapping.setStatus("ACTIVE");
        mapping.setLastLoginAt(LocalDateTime.now());
        try {
            enterpriseAccountMapper.insert(mapping);
        } catch (Exception e) {
            // 映射表写入失败不影响登录主链路（如唯一键并发冲突），仅告警
            log.warn("[ShadowAccount] mapping insert failed for [{}]: {}",
                    created.getUsername(), e.getMessage());
        }
        log.info("[ShadowAccount] provisioned shadow account [{}] from source {}",
                created.getUsername(), SOURCE_PILOT);
        return created;
    }

    private UserEntity doCreate(EnterpriseUserInfo info) {
        String nickname = (info.displayName() == null || info.displayName().isBlank())
                ? info.principalName()
                : info.displayName();
        UserEntity user = new UserEntity();
        user.setUsername(info.principalName());
        user.setNickname(nickname);
        // 随机口令：影子账号不持有可用的本地凭据，仅满足 createUser 的非空校验
        user.setPassword(randomPassword());
        return authService.createUser(user);
    }

    /**
     * 记录/刷新最近企业登录时间（幂等 upsert，失败不影响主链路）
     */
    private void recordLogin(String username) {
        try {
            EnterpriseAccountEntity mapping = enterpriseAccountMapper.selectOne(
                    new LambdaQueryWrapper<EnterpriseAccountEntity>()
                            .eq(EnterpriseAccountEntity::getUsername, username)
                            .last("LIMIT 1"));
            if (mapping != null) {
                mapping.setLastLoginAt(LocalDateTime.now());
                enterpriseAccountMapper.updateById(mapping);
            }
        } catch (Exception e) {
            log.warn("[ShadowAccount] record login failed for [{}]: {}", username, e.getMessage());
        }
    }

    private String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}