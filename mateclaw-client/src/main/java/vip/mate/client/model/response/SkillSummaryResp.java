package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 技能摘要信息
 * <p>
 * Key 为技能类型，Value 为该类型下所有已启用技能的名称列表
 */
@Data
public class SkillSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 按类型分组的技能名称列表 */
    private Map<String, List<String>> skills;
}