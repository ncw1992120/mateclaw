package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Aloudata 维度元数据实体
 * <p>
 * 存储从 Aloudata 指标平台同步的维度级语义信息，
 * 包含维度名、展示名、描述、同义词、数据类型、类目等。
 * 支持 ES 混合检索（关键词 + 向量语义 + RRF 融合）。
 */
@Data
@TableName("dataagent_aloudata_dimension")
public class AloudataDimensionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 维度名称（租户下唯一） */
    private String dimName;

    /** 维度编码 */
    private String dimCode;

    /** 维度中文名 */
    private String dimDisplayName;

    /** 维度类目 ID（未分类为-1） */
    private String dimCategoryId;

    /** 维度类目名称 */
    private String dimCategoryName;

    /** 维度描述 */
    private String dimDescription;

    /** 数据集名称 */
    private String datasetName;

    /** 原始数据类型（如VARCHAR） */
    private String originDataType;

    /** 维度状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE */
    private String displayStatus;

    /** 维度类型：COLUMN_BIND/CUSTOM */
    private String configType;

    /** 配置值（列名或自定义表达式） */
    private String configValue;

    /** 是否时间维度：0-否，1-是 */
    private Boolean isTimeDimension;

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 示例值（逗号分隔，低基数维度） */
    private String exampleValues;

    /** 嵌入文本（用于生成向量） */
    private String embeddingText;

    /** 向量数据（float32 小端序序列化） */
    private byte[] embedding;

    /** 嵌入模型 ID */
    private Long embeddingModelId;

    /** 同步版本号（每次全量同步递增） */
    private Integer syncVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 构建用于 Prompt 的维度语义描述
     */
    public String getPromptInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(dimName);
        if (dimDisplayName != null && !dimDisplayName.isBlank()) {
            sb.append("(").append(dimDisplayName).append(")");
        }
        if (originDataType != null && !originDataType.isBlank()) {
            sb.append(" [").append(originDataType).append("]");
        }
        if (dimDescription != null && !dimDescription.isBlank()) {
            sb.append(" - ").append(dimDescription);
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(", 同义词: ").append(synonyms);
        }
        if (Boolean.TRUE.equals(isTimeDimension)) {
            sb.append(", 时间维度");
        }
        if (dimCategoryName != null && !dimCategoryName.isBlank()) {
            sb.append(", 类目: ").append(dimCategoryName);
        }
        if (exampleValues != null && !exampleValues.isBlank()) {
            sb.append(", 示例: ").append(exampleValues);
        }
        return sb.toString();
    }

    /**
     * 构建 ES 索引用的嵌入文本
     * <p>
     * 嵌入文本越丰富，语义检索召回率越高。包含维度英文名、中文展示名、编码、
     * 数据类型、描述、同义词、类目名称、数据集名称、是否时间维度、示例值等语义信息。
     * <p>
     * 修改此方法后需递增 {@code SCHEMA_EMBEDDING_TEXT_VERSION} 以触发重新嵌入。
     */
    public String buildEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        // 核心语义层：展示名 + 同义词优先，用户自然语言最直接的对应物
        if (dimDisplayName != null && !dimDisplayName.isBlank()) {
            sb.append(dimDisplayName);
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(" ").append(synonyms);
        }
        // 英文名和编码作为辅助语义（后置）
        if (dimName != null && !dimName.isBlank()) {
            sb.append(" ").append(dimName);
        }
        if (dimCode != null && !dimCode.isBlank()) {
            sb.append(" ").append(dimCode);
        }
        sb.append(" | ");
        if (originDataType != null && !originDataType.isBlank()) {
            sb.append("类型: ").append(originDataType).append(", ");
        }
        // 描述截断：长文本描述会稀释展示名的核心语义向量
        if (dimDescription != null && !dimDescription.isBlank()) {
            String desc = dimDescription.length() > 80
                    ? dimDescription.substring(0, 80) : dimDescription;
            sb.append("描述: ").append(desc).append(", ");
        }
        if (dimCategoryName != null && !dimCategoryName.isBlank()) {
            sb.append("类目: ").append(dimCategoryName).append(", ");
        }
        if (datasetName != null && !datasetName.isBlank()) {
            sb.append("数据集: ").append(datasetName).append(", ");
        }
        if (Boolean.TRUE.equals(isTimeDimension)) {
            sb.append("时间维度, ");
        }
        if (exampleValues != null && !exampleValues.isBlank()) {
            sb.append("示例值: ").append(exampleValues);
        }
        return sb.toString().trim();
    }
}
