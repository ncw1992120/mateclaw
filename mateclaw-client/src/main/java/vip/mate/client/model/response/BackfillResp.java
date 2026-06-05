package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Token 回填结果
 */
@Data
public class BackfillResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功执行 */
    private boolean ok;

    /** 执行前待处理数 */
    private long pendingBefore;

    /** 执行后待处理数 */
    private long pendingAfter;

    /** 本批次填充数 */
    private long filledThisBatch;

    /** 备注 */
    private String note;
}
