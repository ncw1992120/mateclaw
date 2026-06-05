package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能经验教训清除结果
 */
@Data
public class SkillLessonsClearResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已清除 */
    private boolean cleared;
}
