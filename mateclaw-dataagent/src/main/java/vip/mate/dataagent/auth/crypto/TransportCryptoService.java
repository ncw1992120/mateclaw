package vip.mate.dataagent.auth.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.exception.MateClawException;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * 传输加密服务：前端敏感字段（password / ssoCookie / 改密口令）的 RSA-OAEP 加解密。
 * <p>
 * 信封格式：{@code base64( RSA-OAEP( base64( UTF-8( "毫秒时间戳:明文" ) ) ) )}。
 * 明文字符串先经 UTF-8→Base64 再进 OAEP：jsencrypt 的 OAEP 按字节掩码，
 * 对中文等 >255 码位的字符存在缺陷，Base64 化后输入恒为纯 ASCII，规避该问题。
 * 后端解密后按 base64→UTF-8 还原，再校验时间戳防重放窗口
 * （见 {@link TransportCryptoProperties#getTimestampWindow()}）。
 * 私钥仅存在于服务端内存，解密后的明文不落任何日志。
 * <p>
 * 注意：这是无 TLS 部署的兜底防护，只防被动窃听；页面若经 HTTP 加载，主动 MITM
 * 仍可篡改页面/公钥。生产环境仍应以 HTTPS 为准。
 */
@Slf4j
@Component
public class TransportCryptoService {

    /**
     * RSA-OAEP 参数：SHA-256 摘要 + MGF1-SHA-256（与前端 jsencrypt 3.5.x encryptOAEP 一致）。
     * 必须用 OAEPParameterSpec 形式——JDK 命名变换 RSA/ECB/OAEPWithSHA-256AndMGF1Padding
     * 的 MGF1 固定为 SHA-1，与 jsencrypt 不兼容（实测互解失败 BadPaddingException）。
     */
    static final OAEPParameterSpec OAEP_SPEC_SHA256_MGF1_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private static final String BEGIN_PRIVATE = "-----BEGIN PRIVATE KEY-----";
    private static final String END_PRIVATE = "-----END PRIVATE KEY-----";

    private final TransportCryptoProperties properties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public TransportCryptoService(TransportCryptoProperties properties) {
        this.properties = properties;
        try {
            String pem = properties.getPrivateKeyPem();
            if (pem != null && !pem.isBlank()) {
                KeyPair pair = loadKeyPair(pem);
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                log.info("[TransportCrypto] loaded configured RSA key");
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                log.warn("[TransportCrypto] mateclaw.auth.crypto.private-key-pem not configured, "
                        + "using in-memory auto-generated key: restart changes the public key "
                        + "(clients must re-fetch), and multi-instance deployments cannot share it");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize transport crypto", e);
        }
    }

    /** 公钥 PEM（SPKI），供登录页加密前拉取 */
    public String publicKeyPem() {
        String b64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        StringBuilder sb = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        return sb.append("-----END PUBLIC KEY-----").toString();
    }

    /**
     * 解密信封并校验时间戳窗口，返回明文。
     *
     * @param encrypted base64(RSA-OAEP( base64(UTF-8("ts:明文")) ))
     */
    public String unwrapField(String encrypted) {
        String plainB64 = decryptField(encrypted);
        String plain;
        try {
            plain = new String(Base64.getDecoder().decode(plainB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalidEnvelope();
        }
        int idx = plain.indexOf(':');
        if (idx <= 0) {
            throw invalidEnvelope();
        }
        long ts;
        try {
            ts = Long.parseLong(plain.substring(0, idx));
        } catch (NumberFormatException e) {
            throw invalidEnvelope();
        }
        long windowMillis = properties.getTimestampWindow() != null
                ? properties.getTimestampWindow().toMillis()
                : 300_000L;
        if (Math.abs(System.currentTimeMillis() - ts) > windowMillis) {
            throw new MateClawException("err.auth.stale_request", 400, "登录请求已过期，请刷新页面重试");
        }
        String value = plain.substring(idx + 1);
        if (value.isBlank()) {
            throw invalidEnvelope();
        }
        return value;
    }

    private String decryptField(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            throw invalidEnvelope();
        }
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC_SHA256_MGF1_SHA256);
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[TransportCrypto] decrypt failed: {}", e.getClass().getSimpleName());
            throw invalidEnvelope();
        }
    }

    private MateClawException invalidEnvelope() {
        return new MateClawException("err.auth.bad_ciphertext", 400,
                "请求格式异常，请刷新页面重试");
    }

    /**
     * 从配置的 PKCS#8 私钥 PEM 加载密钥对，公钥由 CRT 参数推导。
     * 私钥推导公钥要求密钥为 CRT 结构（openssl genrsa 生成的 PKCS#8 满足）。
     */
    private KeyPair loadKeyPair(String pem) throws GeneralSecurityException {
        byte[] privateInfo = parsePem(pem);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateInfo));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        PublicKey publicKey = factory.generatePublic(
                new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    private byte[] parsePem(String pem) {
        String body = pem;
        int b = body.indexOf(BEGIN_PRIVATE);
        if (b >= 0) {
            body = body.substring(b + BEGIN_PRIVATE.length());
        }
        int e = body.indexOf(END_PRIVATE);
        if (e >= 0) {
            body = body.substring(0, e);
        }
        return Base64.getDecoder().decode(body.replaceAll("\\s", ""));
    }
}