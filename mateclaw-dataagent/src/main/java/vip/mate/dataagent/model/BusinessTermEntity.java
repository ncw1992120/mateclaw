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

    /** 租户编码（区分不同业务域） */
    private String tenantCode;

    /** 术语名称（主术语/标准名） */
    private String termName;

    /** 同义词（逗号分隔，如"营收,收入"） */
    private String synonyms;

    /** 术语定义/解释 */
    private String description;

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
        return sb.toString();
    }
}
