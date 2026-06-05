package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能数量统计
 */
@Data
public class SkillCountsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 所有技能总数 */
    private Long all;

    /** builtin 类型技能数 */
    private Long builtin;

    /** mcp 类型技能数 */
    private Long mcp;

    /** dynamic 类型技能数 */
    private Long dynamic;

    /** acp 类型技能数 */
    private Long acp;
}