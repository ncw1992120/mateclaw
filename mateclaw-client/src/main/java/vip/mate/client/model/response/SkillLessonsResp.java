package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能经验教训
 */
@Data
public class SkillLessonsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 技能ID */
    private Long skillId;

    /** 技能名称 */
    private String skillName;

    /** LESSONS.md完整内容 */
    private String raw;

    /** 条目数 */
    private int entryCount;
}
