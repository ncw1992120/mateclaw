package vip.mate.dataagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** AI生成仪表盘请求 */
@Data
@Schema(description = "AI生成仪表盘请求")
public class InsightDashboardGenerateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘名称 */
    @Schema(description = "仪表盘名称", example = "销售分析仪表盘")
    private String name;

    /** 数据源ID */
    @Schema(description = "数据源ID", example = "1")
    private Long datasourceId;

    /** 用户描述/需求 */
    @Schema(description = "用户描述/需求", example = "帮我生成一个销售分析仪表盘，包含销售额趋势、区域分布、TOP10产品")
    private String description;
}
