package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 策展器计数
 */
@Data
public class CuratorCountsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 标记为 stale 的数量 */
    private int stale;

    /** 归档的数量 */
    private int archived;

    /** 重新激活的数量 */
    private int reactivated;
}
