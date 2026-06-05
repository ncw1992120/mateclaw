package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 功能开关
 */
@Data
public class FeatureFlagResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 稳定标识符 */
    private String flagKey;

    /** 主开关 */
    private Boolean enabled;

    /** 描述 */
    private String description;

    /** 逗号分隔的 KB ID 白名单 */
    private String whitelistKbIds;

    /** 逗号分隔的用户 ID 白名单 */
    private String whitelistUserIds;

    /** 灰度百分比(0-100) */
    private Integer rolloutPercent;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
