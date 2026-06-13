package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档分类请求对象
 */
@Data
public class HelpCategoryRequest {

    private String name;

    private String parentId;

    private Integer sortOrder;

    private String icon;

    private String description;
}
