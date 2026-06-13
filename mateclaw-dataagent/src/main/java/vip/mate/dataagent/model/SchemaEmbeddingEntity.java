package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Schema 嵌入向量实体
 * <p>
 * 存储数据源表级 Schema 的向量嵌入，用于语义检索。
 * 嵌入粒度为表级，包含表名、表描述、列名列表、列描述列表等信息。
 */
@Data
@TableName("dataagent_schema_embedding")
public class SchemaEmbeddingEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 嵌入输入文本 */
    private String embeddingText;

    /** 向量数据（float32 小端序序列化） */
    private byte[] embedding;

    /** 使用的嵌入模型 ID */
    private Long embeddingModelId;

    /** 嵌入文本格式版本 */
    private Integer embeddingTextVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
