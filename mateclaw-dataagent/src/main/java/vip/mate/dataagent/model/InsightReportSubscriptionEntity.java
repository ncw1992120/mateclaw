package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 洞察报告订阅实体
 * <p>
 * 记录用户对已发布报告的订阅关系，支持"我的订阅"视图。
 * 唯一索引 (report_id, user_id) 防止重复订阅。
 */
@Data
@TableName("dataagent_insight_report_subscription")
public class InsightReportSubscriptionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 报告 ID */
    @TableField("report_id")
    private Long reportId;

    /** 订阅用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 所属工作区 ID */
    @TableField("workspace_id")
    private Long workspaceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
