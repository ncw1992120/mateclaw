package vip.mate.dataagent.auth.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 密码存储加密初始化配置
 * <p>
 * 仅负责将 {@link PasswordCryptoProperties} 中的 AES 密钥注入静态加解密工具，
 * 供 MyBatis TypeHandler（无法注入 Spring Bean，需无参构造）与存量迁移任务使用，
 * 不承载任何业务逻辑。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PasswordCryptoConfig {

    private final PasswordCryptoProperties properties;

    @PostConstruct
    public void init() {
        AesPasswordCryptor.init(properties.getAesKey());
        log.info("[PwdCrypto] AES 密码存储加密密钥已初始化");
    }
}