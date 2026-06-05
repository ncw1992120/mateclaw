package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 页面搜索预览结果
 */
@Data
public class PageSearchResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面slug */
    private String slug;

    /** 页面标题 */
    private String title;

    /** 页面摘要 */
    private String summary;

    /** 查询相关的正文片段 */
    private String snippet;

    /** 匹配模式列表 */
    private List<String> matchedBy;

    /** 命中原因说明 */
    private String reason;

    /** 检索得分 */
    private double score;

    /** 页面中的图片引用列表 */
    private List<ImageRefResp> imageRefs;
}
