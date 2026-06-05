package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 技能工作区信息
 */
@Data
public class SkillWorkspaceInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 技能名称 */
    private String skillName;

    /** 约定路径 */
    private String conventionPath;

    /** 工作区是否存在 */
    private boolean exists;

    /** 是否包含SKILL.md */
    private boolean hasSkillMd;

    /** 是否包含引用 */
    private boolean hasReferences;

    /** 是否包含脚本 */
    private boolean hasScripts;

    /** 总大小(字节) */
    private long totalSizeBytes;

    /** 文件列表 */
    private List<String> files;

    /** 错误信息 */
    private String error;
}
