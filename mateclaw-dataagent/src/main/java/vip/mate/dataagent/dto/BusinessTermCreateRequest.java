package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 创建业务术语请求
 */
@Data
public class BusinessTermCreateRequest {

    /** 租户编码 */
    private String tenantCode;

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
}
