package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 服务器
 */
@Data
public class McpServer implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String transport;
    private String url;
    private String headersJson;
    private String command;
    private String argsJson;
    private String envJson;
    private String cwd;
    private Boolean enabled;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private String lastStatus;
    private String lastError;
    private LocalDateTime lastConnectedTime;
    private Integer toolCount;
    private String toolsCacheJson;
    private LocalDateTime toolsCacheUpdatedAt;
    private Boolean builtin;
    private String disclosureTier;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
