package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 原始材料
 */
@Data
public class WikiRawMaterialResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String title;
    private String sourceType;
    private String mimeType;
    private String sourcePath;
    private String originalContent;
    private String extractedText;
    private String contentHash;
    private Long fileSize;
    private String processingStatus;
    private Boolean cancelRequested;
    private LocalDateTime lastProcessedAt;
    private String lastProcessedHash;
    private String errorMessage;
    private String progressPhase;
    private Integer progressTotal;
    private Integer progressDone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
