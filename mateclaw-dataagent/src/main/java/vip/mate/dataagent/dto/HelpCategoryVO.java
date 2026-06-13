package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 帮助文档分类视图对象
 */
@Data
public class HelpCategoryVO {

    private String id;

    private String name;

    private String parentId;

    private Integer sortOrder;

    private String icon;

    private String description;

    /** 子分类列表 */
    private List<HelpCategoryVO> children;

    /** 该分类下的文档数量 */
    private Integer documentCount;

    private String createTime;

    private String updateTime;
}
