package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全防护规则
 */
@Data
public class GuardRuleResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 规则ID */
    private String ruleId;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /** 工具名称 */
    private String toolName;

    /** 参数名 */
    private String paramName;

    /** 分类 */
    private String category;

    /** 严重等级 */
    private String severity;

    /** 决策 */
    private String decision;

    /** 匹配模式 */
    private String pattern;

    /** 排除模式 */
    private String excludePattern;

    /** 修复建议 */
    private String remediation;

    /** 是否内置 */
    private Boolean builtin;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级 */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
