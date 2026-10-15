package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 洞察仪表盘列表摘要查询条件。
 * <p>
 * 所有过滤都在数据库侧完成（工作区、软删除、可见性、状态、关键词、排序、分页），
 * 避免把全量实体读入 Java 内存后再过滤。取值归一化见
 * {@link vip.mate.dataagent.service.impl.InsightDashboardServiceImpl} 的静态方法。
 */
@Data
public class InsightDashboardSummaryQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /** 可见性过滤：逗号分隔取值，如 "template,official"；为空返回工作区内全部 */
    private String visibility;

    /** 状态过滤：draft / published；为空不过滤 */
    private String status;

    /** 关键词：匹配名称、描述、负责人名称 */
    private String keyword;

    /** 排序字段白名单：updateTime / name；为空按 updateTime 处理 */
    private String sortBy;

    /** 排序方向：asc / desc；为空按 desc 处理 */
    private String sortOrder;
}
