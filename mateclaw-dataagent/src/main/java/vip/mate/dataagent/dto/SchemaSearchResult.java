package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * Schema 语义检索结果
 */
@Data
public class SchemaSearchResult {

    /** 命中的表列表 */
    private List<TableHit> tableHits;

    /** 关联关系列表 */
    private List<LogicalRelationVO> relations;

    /** 检索耗时（毫秒） */
    private long elapsedMs;

    /**
     * 表级命中项
     */
    @Data
    public static class TableHit {

        /** 表名 */
        private String tableName;

        /** 表注释 */
        private String tableComment;

        /** 匹配分数 */
        private double score;

        /** 匹配来源：keyword / semantic / hybrid */
        private String matchSource;

        /** 字段级语义信息列表 */
        private List<SemanticModelVO> semanticFields;

        /** 样本数据（JSON 格式） */
        private String sampleData;
    }
}
