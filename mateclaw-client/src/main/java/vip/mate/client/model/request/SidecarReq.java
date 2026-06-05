package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Sidecar 配置请求
 */
@Data
public class SidecarReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 默认视觉模型 ID（null 表示清除） */
    private Long defaultVisionModelId;

    /** 默认视频模型 ID（null 表示清除） */
    private Long defaultVideoModelId;
}
