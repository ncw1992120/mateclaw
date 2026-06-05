package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型信息
 */
@Data
public class ModelInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 模型 ID */
    private String id;

    /** 模型名称 */
    private String name;

    /** 探测结果 */
    private Boolean probeOk;

    /** 探测失败原因 */
    private String probeError;

    /** 是否支持 reasoning_effort 参数 */
    private boolean supportsReasoningEffort;

    /** 是否支持深度思考 */
    private boolean supportsThinking;
}
