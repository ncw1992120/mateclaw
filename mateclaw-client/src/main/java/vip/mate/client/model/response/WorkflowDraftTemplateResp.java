package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流草稿模板
 */
@Data
public class WorkflowDraftTemplateResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 稳定的 kebab-case 标识符 */
    private String id;

    /** 双语短标签 */
    private String label;

    /** 一句话描述 */
    private String description;

    /** 匹配提示短语列表 */
    private List<String> matchHints;

    /** 工作流草稿 JSON */
    private String draftJson;

    /** 触发器草稿 JSON 数组 */
    private String triggerDraftsJson;
}
