package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 切片响应
 */
@Data
public class WikiChunkResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long rawId;
    private Integer ordinal;
    private String content;
    private Integer charCount;
    private Integer startOffset;
    private Integer endOffset;
    private String contentHash;
    private byte[] embedding;
    private String embeddingModel;
    private String embeddingTextVersion;
    private Integer pageNumber;
    private Integer tokenCount;
    private String headerBreadcrumb;
    private String sourceSection;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
