package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 从对话合成技能结果
 */
@Data
public class SkillSynthesizeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 技能ID */
    private Long skillId;

    /** 技能名称 */
    private String skillName;

    /** 是否被安全阻断 */
    private boolean blocked;

    /** 错误信息 */
    private String error;

    /** 安全扫描摘要 */
    private String scanSummary;
}
