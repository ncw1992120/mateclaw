package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 技能安装请求（DataAgent 端使用）
 * <p>
 * 代理 mateclaw-server 的安装接口，支持从 URL / ClawHub 市场 / ZIP 三种来源安装技能。
 * 字段语义与 {@code vip.mate.skill.installer.model.InstallRequest} 保持一致，
 * 在 Controller 层转换后转发。
 */
@Data
public class SkillInstallRequest {

    /** bundle URL（GitHub 仓库 URL 或 ClawHub skill URL） */
    private String bundleUrl;

    /** 版本（git ref / hub version，可选） */
    private String version;

    /** 安装后是否启用，默认 true */
    private Boolean enable = true;

    /** 指定 skill 名称（覆盖 SKILL.md 中的名称，可选） */
    private String targetName;

    /** 若同名 skill 已存在，是否覆盖，默认 false */
    private Boolean overwrite = false;

    /** 所属工作区 ID（来自 X-Workspace-Id 头） */
    private Long workspaceId;
}
