package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档搜索结果视图对象
 */
@Data
public class HelpSearchResultVO {

    private String id;

    private String categoryId;

    private String categoryName;

    private String title;

    /** 匹配的内容摘要（含高亮标记） */
    private String highlightContent;

    private String status;

    private String author;

    private Integer viewCount;

    private String updateTime;
}
