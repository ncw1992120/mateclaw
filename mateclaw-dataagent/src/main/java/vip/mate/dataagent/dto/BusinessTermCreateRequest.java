package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 创建业务术语请求
 */
@Data
public class BusinessTermCreateRequest {

    /** 租户编码 */
    private String tenantCode;

    /** 术语名称 */
    private String termName;

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 术语定义/解释 */
    private String description;

    /** 计算公式 */
    private String calculationFormula;

    /** 数据口径 */
    private String dataCaliber;

    /** 数据来源/源系统 */
    private String dataSource;

    /** 责任人/归属部门 */
    private String owner;

    /** 业务规则 */
    private String businessRule;

    /** 关联术语ID（逗号分隔） */
    private String relatedTerms;

    /** 示例/用例 */
    private String example;

    /** 安全分级 */
    private String securityLevel;

    /** 分类 */
    private String category;

    /** 父术语 ID */
    private Long parentId;
}
