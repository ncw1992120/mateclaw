package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 仪表盘 Python 执行持久化记录。 */
@Data
@TableName("dataagent_dashboard_execution")
public class DashboardExecutionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String executionId;
    private Long dashboardId;
    private Long workspaceId;
    private Long userId;
    private String status;
    private String parametersJson;
    private String outputJson;
    private String outputRefJson;
    private String logs;
    private String errorMessage;
    private Integer returnCode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
