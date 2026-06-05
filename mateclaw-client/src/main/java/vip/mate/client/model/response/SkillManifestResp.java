package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 技能 Manifest
 */
@Data
public class SkillManifestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private String icon;
    private String version;
    private String author;
    /** prompt / code / mcp / acp / knowledge */
    private String type;
    private String category;
    private List<String> allowedTools;
    private List<RequirementDefResp> requires;
    private List<String> platforms;
    private List<FeatureDefResp> features;
    private List<SettingDefResp> settings;
    private List<String> requiresModel;
    private List<DashboardMetricResp> dashboardMetrics;
    private SelfEvolutionResp selfEvolution;
    private KnowledgeBindingResp knowledge;
    private AcpBindingResp acp;
    private List<ScriptDefResp> scripts;
    /** 未知 key 兜底 */
    private Map<String, Object> extras;

    /**
     * 依赖定义
     */
    @Data
    public static class RequirementDefResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String type;
        private String description;
    }

    /**
     * 功能定义
     */
    @Data
    public static class FeatureDefResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
        private String description;
    }

    /**
     * 设置定义
     */
    @Data
    public static class SettingDefResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String key;
        private String label;
        private String type;
        private String defaultValue;
        private String description;
    }

    /**
     * 仪表盘指标
     */
    @Data
    public static class DashboardMetricResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String label;
        private String type;
    }

    /**
     * 自进化配置
     */
    @Data
    public static class SelfEvolutionResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private Boolean enabled;
        private String mode;
    }

    /**
     * 知识绑定
     */
    @Data
    public static class KnowledgeBindingResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String kbId;
        private String query;
    }

    /**
     * ACP 绑定
     */
    @Data
    public static class AcpBindingResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String endpointId;
        private String direction;
    }

    /**
     * 脚本定义
     */
    @Data
    public static class ScriptDefResp implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
        private String type;
        private String command;
    }
}
