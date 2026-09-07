package vip.mate.dataagent.auth.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据源密码 AES-256-GCM 加解密工具
 * <p>
 * 存储格式：{@code AES:base64( iv(12字节) + ciphertext )}，密文前缀用于：
 * <ol>
 *   <li>读取时识别该列是否已加密（历史明文数据不带前缀，原样返回，平滑兼容存量）；</li>
 *   <li>存量数据迁移时快速筛选未加密记录。</li>
 * </ol>
 * 密钥由 {@link PasswordCryptoProperties} 在应用启动时通过 {@link #init(String)} 注入；
 * 本类是 TypeHandler 与启动迁移任务的共用实现，避免加解密逻辑散落。
 */
public final class AesPasswordCryptor {

    /** 已加密数据前缀标识 */
    public static final String ENCRYPTED_PREFIX = "AES:";

    /** GCM 认证标签位长 */
    private static final int GCM_TAG_BITS = 128;

    /** GCM 推荐随机 IV 长度（12 字节） */
    private static final int IV_LENGTH = 12;

    /** 密钥派生的算法 */
    private static final String KEY_ALGORITHM = "AES";

    /** 加解密转换模式（GCM 自带完整性校验，支持随机 IV 拼接密文） */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 应用启动时由配置注入的 AES-256 密钥（volatile 保证多线程可见） */
    private static volatile SecretKeySpec secretKey;

    private AesPasswordCryptor() {
    }

    /**
     * 注入 AES 密钥（Base64 编码 32 字节）。
     *
     * @param aesKeyBase64 Base64 编码的 256 位密钥
     */
    public static void init(String aesKeyBase64) {
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            throw new IllegalStateException("密码存储加密密钥未配置: mateclaw.dataagent.pwd-crypto.aes-key");
        }
        byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("密码存储加密密钥必须为 32 字节（AES-256），当前长度: " + keyBytes.length);
        }
        secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    /**
     * 判断存量值是否已按本格式加密。
     *
     * @param raw 数据库原值，可能为 null
     * @return true 表示已加密
     */
    public static boolean isEncrypted(String raw) {
        return raw != null && raw.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 加密明文为存储格式。
     *
     * @param plain 明文密码
     * @return {@code AES:base64(iv+ciphertext)}
     */
    public static String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, payload, IV_LENGTH, ciphertext.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("密码存储加密失败", e);
        }
    }

    /** 单值允许的最大加密层数（防御异常窗口期内"密文被再加密"的多层脏数据） */
    private static final int MAX_LAYERS = 4;

    /**
     * 解密存储值（幂等，支持多层）。
     * <p>
     * 逐层剥离 {@link #ENCRYPTED_PREFIX} 并解密，直到无前缀：
     * <ul>
     *   <li>未加密明文 → 原样返回（兼容存量与 TypeHandler 已解密场景）；</li>
     *   <li>单层密文 → 解为明文；</li>
     *   <li>多层密文（历史缺陷窗口期"密文被整体再加密"的脏数据）→ 循环解至明文；</li>
     *   <li>带前缀但解密失败 → 视为碰巧以 {@code AES:} 开头的字面明文密码，原样返回。</li>
     * </ul>
     *
     * @param raw 数据库存储值
     * @return 明文
     */
    public static String decrypt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String result = raw;
        for (int depth = 0; depth < MAX_LAYERS && isEncrypted(result); depth++) {
            String decrypted = decryptOnce(result);
            if (decrypted == null) {
                // GCM 校验失败：当前值是字面明文（如用户密码本身以 AES: 开头），原样返回
                return result;
            }
            result = decrypted;
        }
        return result;
    }

    /**
     * 单层解密；密钥缺失或密文非法（含 GCM 认证失败）返回 null，由上层决定容错。
     */
    private static String decryptOnce(String raw) {
        if (!isEncrypted(raw)) {
            return raw;
        }
        if (secretKey == null) {
            throw new IllegalStateException("密码存储加密密钥未初始化，请检查启动日志");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(raw.substring(ENCRYPTED_PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void requireKey() {
        if (secretKey == null) {
            throw new IllegalStateException("密码存储加密密钥未初始化，请检查启动日志");
        }
    }
}