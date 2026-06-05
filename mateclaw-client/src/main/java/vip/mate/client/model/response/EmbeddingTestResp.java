package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 嵌入模型测试结果
 */
@Data
public class EmbeddingTestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 向量维度 */
    private Integer dimensions;

    /** 模型名称 */
    private String model;

    /** 消息 */
    private String message;
}
