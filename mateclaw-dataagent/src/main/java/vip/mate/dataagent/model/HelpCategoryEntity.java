package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮助文档分类实体
 */
@Data
@TableName("dataagent_help_category")
public class HelpCategoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 父分类 ID（0 表示顶级分类） */
    private Long parentId;

    /** 排序序号（升序） */
    private Integer sortOrder;

    /** 分类图标（emoji 或 URL） */
    private String icon;

    /** 分类描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
