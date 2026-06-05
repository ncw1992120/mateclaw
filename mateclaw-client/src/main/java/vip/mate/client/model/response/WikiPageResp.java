package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 页面
 */
@Data
public class WikiPageResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String slug;
    private String title;
    private String content;
    private String summary;
    private String outgoingLinks;
    private String sourceRawIds;
    private String sourceEntries;
    private String pageType;
    private String purposeHint;
    private Integer version;
    private String lastUpdatedBy;
    private Integer locked;
    private Integer archived;
    private byte[] embedding;
    private String embeddingModel;
    private String embeddingTextVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
