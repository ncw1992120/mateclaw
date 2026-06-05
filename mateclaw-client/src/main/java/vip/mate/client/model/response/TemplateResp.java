package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 模板
 */
@Data
public class TemplateResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String nameZh;
    private String description;
    private String descriptionZh;
    private String icon;
    private String agentType;
    private String tags;
    private Integer maxIterations;
    private String systemPrompt;
    private List<WorkspaceFileTemplate> workspaceFiles;
    private List<String> defaultSkillSlugs;
    private List<String> defaultToolNames;

    /**
     * 工作区文件模板
     */
    @Data
    public static class WorkspaceFileTemplate implements Serializable {
        private static final long serialVersionUID = 1L;

        private String filename;
        private String content;
        private Boolean enabled;
        private Integer sortOrder;
    }
}
