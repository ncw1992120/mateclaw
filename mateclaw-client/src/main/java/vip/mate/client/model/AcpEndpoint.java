package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ACP 端点
 */
@Data
public class AcpEndpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String command;
    private String argsJson;
    private String envJson;
    private String toolParseMode;
    private Boolean builtin;
    private Boolean trusted;
    private Boolean enabled;
    private Long stdioBufferLimitBytes;
    private String lastStatus;
    private LocalDateTime lastTestedAt;
    private String lastError;
    private Long workspaceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
