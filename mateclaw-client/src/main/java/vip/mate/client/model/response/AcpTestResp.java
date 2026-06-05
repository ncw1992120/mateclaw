package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * ACP 端点测试结果
 */
@Data
public class AcpTestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 端点名称 */
    private String name;

    /** 端点命令 */
    private String command;

    /** 解析后的参数列表 */
    private List<String> args;

    /** Agent协议版本 */
    private Integer protocolVersion;

    /** Agent能力声明(JSON) */
    private String agentCapabilities;

    /** 会话ID */
    private String sessionId;

    /** 会话创建警告信息 */
    private String sessionWarning;

    /** 测试结果状态(OK/ERROR) */
    private String status;

    /** 错误信息 */
    private String error;

    /** 耗时(毫秒) */
    private long elapsedMs;
}
