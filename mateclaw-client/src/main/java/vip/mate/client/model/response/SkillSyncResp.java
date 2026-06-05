package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能文件同步结果
 */
@Data
public class SkillSyncResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 技能ID */
    private Long skillId;

    /** 技能名称 */
    private String name;

    /** 已物化文件数 */
    private int filesMaterialized;

    /** 已是最新文件数 */
    private int filesAlreadyCurrent;

    /** 从磁盘回填文件数 */
    private int filesBackfilledFromDisk;

    /** 是否从磁盘回填 */
    private boolean backfilledFromDisk;
}
