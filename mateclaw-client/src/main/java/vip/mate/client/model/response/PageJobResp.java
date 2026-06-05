package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 页面操作结果（增强/修复）
 */
@Data
public class PageJobResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long jobId;

    /** 错误信息 */
    private String error;
}
