package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 更新业务术语请求
 */
@Data
public class BusinessTermUpdateRequest {

    /** 术语名称 */
    private String termName;

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 术语定义/解释 */
    private String description;

    /** 分类 */
    private String category;

    /** 父术语 ID */
    private Long parentId;

    /** 状态：0-停用 / 1-启用 */
    private Integer status;
}
