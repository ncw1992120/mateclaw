package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流草稿生成结果
 */
@Data
public class GeneratedWorkflowDraftResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 生成的工作流名称 */
    private String name;

    /** 用户描述 */
    private String description;

    /** 草稿 JSON 字符串 */
    private String draftJson;

    /** 建议的触发器列表 */
    private List<java.util.Map<String, Object>> triggerDrafts;

    /** 警告信息 */
    private List<String> warnings;

    /** 未填充的占位字段说明 */
    private List<String> missingFields;

    /** 模型生成置信度 */
    private Double confidence;

    /** 预编译是否通过 */
    private boolean compileOk;

    /** 预编译失败时的错误列表 */
    private List<CompileErrorResp> compileErrors;
}
