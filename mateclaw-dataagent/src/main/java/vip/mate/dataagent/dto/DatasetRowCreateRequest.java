package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数据集新增行请求
 */
@Data
public class DatasetRowCreateRequest {

    /** 字段值（字段名 -> 值） */
    private Map<String, Object> values;
}
