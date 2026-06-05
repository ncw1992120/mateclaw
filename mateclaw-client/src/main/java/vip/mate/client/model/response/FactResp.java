package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 事实实体
 */
@Data
public class FactResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String sourceRef;
    private String category;
    private String subject;
    private String predicate;
    private String objectValue;
    private Double confidence;
    private Double trust;
    private LocalDateTime lastUsedAt;
    private Integer useCount;
    private String extractedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
