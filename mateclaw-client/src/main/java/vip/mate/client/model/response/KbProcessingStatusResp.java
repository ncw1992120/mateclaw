package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库处理状态
 */
@Data
public class KbProcessingStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** KB状态 */
    private String status;

    /** 待处理数 */
    private long pending;

    /** 处理中数 */
    private long processing;

    /** 已完成数 */
    private long completed;

    /** 失败数 */
    private long failed;

    /** 总原始材料数 */
    private int totalRaw;

    /** 总页面数 */
    private Integer totalPages;
}
