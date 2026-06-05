package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 转换聚合结果
 */
@Data
public class TransformationAggregateResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 页面slug */
    private String slug;

    /** 页面标题 */
    private String title;

    /** 使用的来源数 */
    private int sourcesUsed;

    /** 输入字符数 */
    private int charsFed;

    /** 是否新建 */
    private boolean created;
}
