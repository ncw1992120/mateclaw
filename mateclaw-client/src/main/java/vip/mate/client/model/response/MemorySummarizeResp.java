package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 记忆摘要触发结果
 */
@Data
public class MemorySummarizeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 处理状态 */
    private String status;
}