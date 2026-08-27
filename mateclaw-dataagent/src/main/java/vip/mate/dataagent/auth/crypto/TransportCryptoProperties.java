package vip.mate.dataagent.auth.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 传输加密配置（mateclaw.auth.crypto.*）
 * <p>
 * 无 TLS 部署下前端对敏感字段（password / ssoCookie / 改密口令）做
 * RSA-OAEP 公钥加密，本配置管理私钥与防重放时间窗。
 */
@Data
@ConfigurationProperties(prefix = "mateclaw.auth.crypto")
public class TransportCryptoProperties {

    /**
     * RSA 私钥 PEM（PKCS#8，多实例部署必须显式配置共享同一私钥）；
     * 留空则启动时自动生成内存密钥——重启后公钥变化、前端需重新拉取公钥，且多实例无法互解。
     */
    private String privateKeyPem = "";

    /** 加密信封时间戳容忍窗口（防重放），默认 5 分钟 */
    private Duration timestampWindow = Duration.ofMinutes(5);
}