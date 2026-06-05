package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 默认嵌入模型信息
 */
@Data
public class DefaultEmbeddingInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 默认 Embedding 模型 ID */
    private String defaultModelId;
}
