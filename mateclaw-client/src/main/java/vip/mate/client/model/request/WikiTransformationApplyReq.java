package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 转换应用请求
 */
@Data
public class WikiTransformationApplyReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 原始材料 ID */
    private Long rawId;

    /** 页面 ID */
    private Long pageId;
}