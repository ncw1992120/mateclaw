package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务术语实体
 * <p>
 * 术语是跨数据源的业务概念统一定义，同义词作为术语的附属属性。
 * 支持租户隔离、类目分组、层级结构。
 */
@Data
@TableName("dataagent_business_term")
public class BusinessTermEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 租户编码（区分不同业务域） */
    private String tenantCode;

    /** 术语名称（主术语/标准名） */
    private String termName;

    /** 同义词（逗号分隔，如"营收,收入"） */
    private String synonyms;

    /** 术语定义/解释 */
    private String description;

    /** 计算公式（描述该术语的指标计算逻辑/表达式） */
    private String calculationFormula;

    /** 数据口径（统计范围、边界条件、排除规则等） */
    private String dataCaliber;

    /** 数据来源/源系统（如CRM、ERP等） */
    private String dataSource;

    /** 责任人/归属部门（负责维护该术语定义的准确性） */
    private String owner;

    /** 业务规则（约束条件/业务逻辑规则） */
    private String businessRule;

    /** 关联术语ID（逗号分隔，如"101,102"） */
    private String relatedTerms;

    /** 示例/用例（该术语在实际业务中的使用示例） */
    private String example;

    /** 安全分级（公开/内部/机密） */
    private String securityLevel;

    /** 分类（如：财务类、客户类） */
    private String category;

    /** 父术语 ID（支持层级结构，顶级为 NULL） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    /** 嵌入文本（用于生成向量） */
    private String embeddingText;

    /** 向量数据（float32小端序序列化） */
    private byte[] embedding;

    /** 嵌入模型 ID */
    private Long embeddingModelId;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;

    /**
     * 获取用于 Prompt 的术语信息文本
     * <p>
     * 将术语信息格式化为 LLM 可理解的文本描述
     */
    public String getPromptInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(termName);
        if (description != null && !description.isBlank()) {
            sb.append(" - ").append(description);
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(", 同义词: ").append(synonyms);
        }
        if (category != null && !category.isBlank()) {
            sb.append(", 分类: ").append(category);
        }
        if (calculationFormula != null && !calculationFormula.isBlank()) {
            sb.append(", 计算公式: ").append(calculationFormula);
        }
        if (dataCaliber != null && !dataCaliber.isBlank()) {
            sb.append(", 数据口径: ").append(dataCaliber);
        }
        if (businessRule != null && !businessRule.isBlank()) {
            sb.append(", 业务规则: ").append(businessRule);
        }
        return sb.toString();
    }

    /**
     * 构建嵌入文本（用于向量化）
     * <p>
     * 将术语的核心语义信息拼接为文本，用于 Embedding 模型生成向量
     */
    public String buildEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        sb.append(termName);
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(" 同义词: ").append(synonyms);
        }
        if (description != null && !description.isBlank()) {
            sb.append(" 定义: ").append(description);
        }
        if (category != null && !category.isBlank()) {
            sb.append(" 分类: ").append(category);
        }
        if (calculationFormula != null && !calculationFormula.isBlank()) {
            sb.append(" 计算公式: ").append(calculationFormula);
        }
        if (dataCaliber != null && !dataCaliber.isBlank()) {
            sb.append(" 数据口径: ").append(dataCaliber);
        }
        if (businessRule != null && !businessRule.isBlank()) {
            sb.append(" 业务规则: ").append(businessRule);
        }
        return sb.toString();
    }
}
