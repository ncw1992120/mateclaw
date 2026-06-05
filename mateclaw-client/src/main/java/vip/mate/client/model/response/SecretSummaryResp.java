package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 技能密钥摘要
 */
@Data
public class SecretSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 密钥名称 */
    private String key;

    /** 脱敏预览 */
    private String preview;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
