package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮助文档反馈实体
 */
@Data
@TableName("dataagent_help_feedback")
public class HelpFeedbackEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文档 ID */
    private Long documentId;

    /** 评分（1-5） */
    private Integer rating;

    /** 改进建议 */
    private String suggestion;

    /** 用户标识 */
    private String userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
