package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能依赖要求
 */
@Data
public class SkillRequirementsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否全部满足 */
    private boolean allMet;

    /** 依赖状态列表 */
    private List<DependencyStatusResp> statuses;

    /** 功能状态 */
    private Map<String, String> featureStatuses;

    /** 活跃功能集合 */
    private Set<String> activeFeatures;

    /** 摘要 */
    private String summary;
}
