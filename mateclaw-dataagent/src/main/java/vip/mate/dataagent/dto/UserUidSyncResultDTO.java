package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户 UID 映射同步结果
 */
@Data
public class UserUidSyncResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 从源库拉取的有效映射条数（去除空值与重复后） */
    private int fetched;

    /** 本次 upsert 写入条数 */
    private int upserted;

    /** 源库中已不存在而被置为停用的条数 */
    private int disabled;

    /** 跳过的无效行数（关键字段为空） */
    private int skipped;
}
