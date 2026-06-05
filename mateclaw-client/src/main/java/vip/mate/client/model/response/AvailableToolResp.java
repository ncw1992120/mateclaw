package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 可用工具 DTO
 */
@Data
public class AvailableToolResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String rowId;
    private String source;
    private Long providerId;
    private String providerName;
    private String name;
    private String rawName;
    private String description;
    private String group;
    private String groupId;
    private boolean stale;
    private boolean available;
    private String unavailableReason;
}
