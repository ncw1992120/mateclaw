package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能关联员工
 */
@Data
public class SkillEmployeeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long id;

    /** Agent名称 */
    private String name;

    /** Agent图标 */
    private String icon;

    /** 绑定类型(explicit/implicit) */
    private String binding;
}
