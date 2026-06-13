package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * Schema 语义检索请求
 */
@Data
public class SchemaSearchRequest {

    /** 数据源 ID */
    private Long datasourceId;

    /** 自然语言查询 */
    private String query;

    /** 返回结果数量上限 */
    private Integer topK;

    /** 相似度阈值 */
    private Double similarityThreshold;
}
