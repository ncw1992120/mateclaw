package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * MCP 工具描述符
 */
@Data
public class McpToolDescriptorResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具的 JSON Schema 输入定义 */
    private Object inputSchema;
}
