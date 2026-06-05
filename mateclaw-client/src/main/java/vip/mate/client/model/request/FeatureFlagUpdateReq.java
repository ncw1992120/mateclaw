package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 功能开关更新请求
 */
@Data
public class FeatureFlagUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否启用 */
    private Boolean enabled;

    /** 描述 */
    private String description;

    /** 白名单知识库 ID */
    private String whitelistKbIds;

    /** 白名单用户 ID */
    private String whitelistUserIds;

    /** 灰度百分比（0-100） */
    private Integer rolloutPercent;
}
