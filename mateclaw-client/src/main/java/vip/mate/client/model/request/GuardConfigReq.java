package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全防护配置
 */
@Data
public class GuardConfigReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 总开关 */
    private Boolean enabled;

    /** 防护范围 */
    private String guardScope;

    /** 受保护工具列表(JSON) */
    private String guardedToolsJson;

    /** 拒绝工具列表(JSON) */
    private String deniedToolsJson;

    /** 文件防护开关 */
    private Boolean fileGuardEnabled;

    /** 敏感路径(JSON) */
    private String sensitivePathsJson;

    /** 审计日志总开关 */
    private Boolean auditEnabled;

    /** 最低记录等级 */
    private String auditMinSeverity;

    /** 审计日志保留天数(0=永不清理) */
    private Integer auditRetentionDays;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
