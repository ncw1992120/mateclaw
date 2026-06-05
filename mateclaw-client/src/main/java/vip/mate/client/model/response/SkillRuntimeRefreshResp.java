package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 技能运行时刷新结果
 */
@Data
public class SkillRuntimeRefreshResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活跃技能数 */
    private int count;

    /** 刷新消息 */
    private String message;

    /** 重新同步的技能列表 */
    private List<String> resynced;
}
