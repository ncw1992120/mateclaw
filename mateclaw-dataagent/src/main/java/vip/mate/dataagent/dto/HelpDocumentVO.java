package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档视图对象
 */
@Data
public class HelpDocumentVO {

    private String id;

    private String categoryId;

    /** 分类名称（冗余，便于展示） */
    private String categoryName;

    private String title;

    private String content;

    private Integer sortOrder;

    /** 文档状态：draft / published */
    private String status;

    private String author;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档摘要 */
    private String summary;

    private Integer viewCount;

    private String createTime;

    private String updateTime;
}
