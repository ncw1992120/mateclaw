package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档请求对象
 */
@Data
public class HelpDocumentRequest {

    private String categoryId;

    private String title;

    private String content;

    private Integer sortOrder;

    private String status;

    private String author;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档摘要 */
    private String summary;
}
