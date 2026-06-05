package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 连接测试结果
 */
@Data
public class McpConnectionResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 连接是否成功 */
    private boolean success;

    /** 提示信息 */
    private String message;

    /** 发现的工具数量 */
    private int toolCount;

    /** 连接耗时(毫秒) */
    private long latencyMs;

    /** 已发现的工具名称列表 */
    private List<String> discoveredTools;

    /** 连接时间戳 */
    private LocalDateTime timestamp;
}
