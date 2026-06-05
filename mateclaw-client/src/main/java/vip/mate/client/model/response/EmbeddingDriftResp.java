package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 嵌入漂移信息
 */
@Data
public class EmbeddingDriftResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前嵌入文本版本 */
    private String currentEmbeddingTextVersion;

    /** 待重新嵌入的分块数 */
    private int pendingReembedChunks;

    /** 已嵌入的总分块数 */
    private long totalEmbeddedChunks;

    /** 待重新嵌入的预估Token数 */
    private long pendingReembedEstimatedTokens;
}
