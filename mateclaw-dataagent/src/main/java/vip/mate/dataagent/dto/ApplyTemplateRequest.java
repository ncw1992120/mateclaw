package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 应用模板请求
 */
@Data
public class ApplyTemplateRequest {

    /** 模板 ID */
    private String templateId;

    /** 工作区 ID */
    private Long workspaceId;
}
