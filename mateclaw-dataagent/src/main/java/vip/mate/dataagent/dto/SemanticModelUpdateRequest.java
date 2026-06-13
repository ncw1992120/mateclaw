package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 更新字段级语义模型请求
 */
@Data
public class SemanticModelUpdateRequest {

    /** 业务别名 */
    private String businessName;

    /** 业务描述 */
    private String businessDescription;

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 示例值（逗号分隔） */
    private String exampleValues;

    /** 枚举值 JSON */
    private String enumValues;

    /** 单位 */
    private String unit;

    /** 值域范围 */
    private String valueRange;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;
}
