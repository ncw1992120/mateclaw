package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型测试结果
 */
@Data
public class ModelTestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 延迟(毫秒) */
    private long latencyMs;

    /** 成功时的消息 */
    private String message;

    /** 失败时的错误信息 */
    private String errorMessage;
}
