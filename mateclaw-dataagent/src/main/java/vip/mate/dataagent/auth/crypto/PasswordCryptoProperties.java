package vip.mate.dataagent.auth.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据源密码存储加密配置（mateclaw.dataagent.pwd-crypto.*）
 * <p>
 * 用于 dataagent_datasource.password 与 dataagent_datasource_account.query_password
 * 的 AES-256 静态存储加密。生产环境必须通过环境变量 MATECLAW_DATAAGENT_PWD_AES_KEY
 * 显式配置独立密钥，禁止使用内置开发默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mateclaw.dataagent.pwd-crypto")
public class PasswordCryptoProperties {

    /**
     * AES-256 密钥，Base64 编码的 32 字节（如 openssl rand -base64 32 生成）；
     * 内置值仅供本地开发联调，生产必须覆盖。
     */
    private String aesKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
}