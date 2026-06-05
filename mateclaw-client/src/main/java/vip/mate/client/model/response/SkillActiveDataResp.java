package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 活跃技能数据
 */
@Data
public class SkillActiveDataResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活跃技能数 */
    private int count;

    /** 活跃技能列表 */
    private List<ResolvedSkillResp> skills;
}
