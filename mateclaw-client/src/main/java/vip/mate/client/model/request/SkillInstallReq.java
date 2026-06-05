package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能安装请求
 */
@Data
public class SkillInstallReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Bundle URL（GitHub 仓库 URL 或 ClawHub skill URL） */
    private String bundleUrl;

    /** 版本（git ref / hub version） */
    private String version;

    /** 安装后是否启用（默认 true） */
    private Boolean enable = true;

    /** 指定 skill 名称（覆盖 SKILL.md 中的名称） */
    private String targetName;

    /** 若同名 skill 已存在，是否覆盖（默认 false） */
    private Boolean overwrite = false;

    /** 绕过空 bundle 剪枝保护（默认 false） */
    private Boolean forcePrune = false;
}