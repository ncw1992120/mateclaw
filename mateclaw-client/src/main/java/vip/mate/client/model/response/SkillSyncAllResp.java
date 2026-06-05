package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 全量技能文件同步结果
 */
@Data
public class SkillSyncAllResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 考虑的技能数 */
    private int skillsConsidered;

    /** 回填的技能数 */
    private int skillsBackfilled;

    /** 已物化文件数 */
    private int filesMaterialized;

    /** 已是最新文件数 */
    private int filesAlreadyCurrent;

    /** 从磁盘回填文件数 */
    private int filesBackfilledFromDisk;
}
