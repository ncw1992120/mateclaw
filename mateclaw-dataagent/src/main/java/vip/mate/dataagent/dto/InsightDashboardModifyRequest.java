package vip.mate.dataagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** AI对话修改仪表盘请求 */
@Data
@Schema(description = "AI对话修改仪表盘请求")
public class InsightDashboardModifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘ID */
    @Schema(description = "仪表盘ID", example = "1")
    private Long dashboardId;

    /** 用户修改指令 */
    @Schema(description = "用户修改指令", example = "把第一个图表改成饼图，再添加一个销售额的KPI卡片")
    private String instruction;
}
