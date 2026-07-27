package vip.mate.dataagent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布报告请求
 */
@Data
public class InsightReportPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联的仪表盘 ID（必填） */
    @NotNull(message = "仪表盘ID不能为空")
    private Long dashboardId;

    /** 报告名称（可选，默认取仪表盘名称） */
    private String name;

    /** 描述（可选） */
    private String description;
}
