package vip.mate.dataagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AI助手对话请求
 * <p>
 * 统一AI生成和AI修改的对话请求，通过dashboardId是否为空区分生成/修改模式：
 * - dashboardId为空时：AI生成模式，根据用户描述和数据源生成新仪表盘
 * - dashboardId不为空时：AI修改模式，根据用户指令修改已有仪表盘
 */
@Data
@Schema(description = "AI助手对话请求，统一AI生成和AI修改")
public class InsightDashboardAiChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘ID（修改模式必填，生成模式为空） */
    @Schema(description = "仪表盘ID，修改模式必填，生成模式为空", example = "1")
    private Long dashboardId;

    /** 仪表盘名称（仅生成模式使用） */
    @Schema(description = "仪表盘名称，仅生成模式使用", example = "销售分析仪表盘")
    private String name;

    /** 数据源ID（仅生成模式使用） */
    @Schema(description = "数据源ID，仅生成模式使用", example = "1")
    private Long datasourceId;

    /** 用户消息/指令 */
    @Schema(description = "用户消息/指令，生成模式为需求描述，修改模式为修改指令", example = "帮我生成一个销售分析仪表盘，包含销售额趋势、区域分布、TOP10产品")
    private String message;
}
