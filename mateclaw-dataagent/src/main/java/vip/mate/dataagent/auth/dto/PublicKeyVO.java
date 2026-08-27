package vip.mate.dataagent.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 传输加密公钥信息（GET /v1/auth/pubkey）
 * <p>
 * 登录/SSO/改密请求中的敏感字段（password/ssoCookie 等）用 publicKey 做 RSA-OAEP
 * 加密后传输；公钥本身公开可下发，密文仅服务端私钥可解。
 */
@Data
@AllArgsConstructor
public class PublicKeyVO {

    /** RSA 公钥 PEM（SPKI 格式，前端 jsencrypt 可直接使用） */
    private String publicKey;

    /** 算法标识 */
    private String algorithm;
}