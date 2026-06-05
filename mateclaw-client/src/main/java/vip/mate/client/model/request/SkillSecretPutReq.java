package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能密钥写入请求
 */
@Data
public class SkillSecretPutReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 密钥名称 */
    private String key;

    /** 密钥值（空值会删除该密钥） */
    private String value;
}