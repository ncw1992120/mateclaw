package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已解析的技能
 */
@Data
public class ResolvedSkillResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String content;
    private String source;
    private String skillDirPath;
    private String configuredSkillDir;
    private boolean runtimeAvailable;
    private String resolutionError;
    private Map<String, Object> references;
    private Map<String, Object> scripts;
    private boolean enabled;
    private String icon;
    private boolean builtin;
    private Long workspaceId;
    private LocalDateTime createTime;
    private boolean securityBlocked;
    private String securitySeverity;
    private String securitySummary;
    private List<SecurityFindingResp> securityFindings;
    private List<String> securityWarnings;
    private boolean dependencyReady;
    private List<String> missingDependencies;
    private String dependencySummary;
    private Map<String, String> featureStatuses;
    private Set<String> activeFeatures;
    private Set<String> effectiveAllowedTools;
    private Set<String> effectiveAllowedToolsDisplay;
    private String runtimeStatusLabel;
    /** 解析后的 manifest */
    private SkillManifestResp manifest;
}
