package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮助文档实体
 */
@Data
@TableName("dataagent_help_document")
public class HelpDocumentEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属分类 ID */
    private Long categoryId;

    /** 文档标题 */
    private String title;

    /** 文档内容（Markdown 格式） */
    private String content;

    /** 排序序号（升序） */
    private Integer sortOrder;

    /** 文档状态：draft / published */
    private String status;

    /** 作者 */
    private String author;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档摘要 */
    private String summary;

    /** 浏览次数 */
    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
