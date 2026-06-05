package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能置顶/取消置顶请求
 */
@Data
public class SkillPinReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否置顶 */
    private boolean pinned;
}
