package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * Aloudata 维度分页查询参数
 */
@Data
public class AloudataDimensionPageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNumber = 1;

    /** 每页大小 */
    private Integer pageSize = 20;

    /** 搜索关键词，模糊匹配维度名、展示名、描述、类目名 */
    private String keyword;

    /** 类目 ID 过滤 */
    private String categoryId;
}
