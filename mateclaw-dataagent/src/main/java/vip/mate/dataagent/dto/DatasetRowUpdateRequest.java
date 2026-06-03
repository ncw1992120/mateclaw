package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数据集行更新请求
 */
@Data
public class DatasetRowUpdateRequest {

    /** 行标识值（主键字段名 -> 值） */
    private Map<String, Object> rowKey;

    /** 待更新字段（字段名 -> 新值） */
    private Map<String, Object> values;
}
