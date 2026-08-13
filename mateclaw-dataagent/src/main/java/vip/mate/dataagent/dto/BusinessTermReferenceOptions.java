package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 业务术语关联引用候选
 * <p>
 * 供前端选择器使用的跨数据源指标 / 维度候选列表。
 */
@Data
public class BusinessTermReferenceOptions {

    /** 指标候选列表 */
    private List<BusinessTermRef> metrics;

    /** 维度候选列表 */
    private List<BusinessTermRef> dimensions;
}
