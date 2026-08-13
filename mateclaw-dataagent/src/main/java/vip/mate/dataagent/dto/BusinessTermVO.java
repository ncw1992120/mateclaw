package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 业务术语视图对象
 */
@Data
public class BusinessTermVO {

    /** 主键 ID */
    private Long id;

    /** 租户编码 */
    private String tenantCode;

    /** 术语名称 */
    private String termName;

    /** 同义词 */
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

    /** 关联指标引用列表 */
    private List<BusinessTermRef> relatedMetrics;

    /** 关联维度引用列表 */
    private List<BusinessTermRef> relatedDimensions;

    /** 示例/用例 */
    private String example;

    /** 安全分级 */
    private String securityLevel;

    /** 分类 */
    private String category;

    /** 父术语 ID */
    private Long parentId;

    /** 父术语名称 */
    private String parentTermName;

    /** 嵌入文本 */
    private String embeddingText;

    /** 嵌入模型 ID */
    private Long embeddingModelId;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    /** Prompt 格式的术语信息 */
    private String promptInfo;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
