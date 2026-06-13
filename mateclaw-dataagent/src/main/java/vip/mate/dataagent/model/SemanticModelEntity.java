package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段级语义模型实体
 * <p>
 * 为数据库物理字段建立业务语义映射，是连接"物理数据"与"业务理解"的桥梁。
 * 支持业务别名、同义词、业务描述、枚举值等语义信息，帮助 LLM 准确理解数据含义。
 */
@Data
@TableName("dataagent_semantic_model")
public class SemanticModelEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 字段名 */
    private String columnName;

    /** 业务别名（如"客户满意度分数"） */
    private String businessName;

    /** 业务描述，直接用于 Prompt */
    private String businessDescription;

    /** 同义词（逗号分隔，如"满意度,客户评分"） */
    private String synonyms;

    /** 物理数据类型 */
    private String dataType;

    /** 数据库原始注释 */
    private String columnComment;

    /** 示例值（逗号分隔） */
    private String exampleValues;

    /** 枚举值 JSON（如 {"0":"待支付","1":"已支付"}） */
    private String enumValues;

    /** 单位（如 °C、m/s、%） */
    private String unit;

    /** 值域范围（如 0~100） */
    private String valueRange;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;

    /**
     * 获取用于 Prompt 的语义信息文本
     * <p>
     * 将字段级语义信息格式化为 LLM 可理解的文本描述
     */
    public String getPromptInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(tableName).append(".").append(columnName);
        if (businessName != null && !businessName.isBlank()) {
            sb.append("(").append(businessName).append(")");
        }
        if (dataType != null && !dataType.isBlank()) {
            sb.append(" [").append(dataType).append("]");
        }
        if (businessDescription != null && !businessDescription.isBlank()) {
            sb.append(" - ").append(businessDescription);
        } else if (columnComment != null && !columnComment.isBlank()) {
            sb.append(" - ").append(columnComment);
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(", 同义词: ").append(synonyms);
        }
        if (enumValues != null && !enumValues.isBlank()) {
            sb.append(", 枚举值: ").append(enumValues);
        }
        if (unit != null && !unit.isBlank()) {
            sb.append(", 单位: ").append(unit);
        }
        if (valueRange != null && !valueRange.isBlank()) {
            sb.append(", 值域: ").append(valueRange);
        }
        if (exampleValues != null && !exampleValues.isBlank()) {
            sb.append(", 示例: ").append(exampleValues);
        }
        return sb.toString();
    }
}
