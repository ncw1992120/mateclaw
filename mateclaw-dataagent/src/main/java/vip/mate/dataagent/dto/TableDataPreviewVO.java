package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表数据预览视图对象
 */
@Data
public class TableDataPreviewVO {

    /** 列名列表 */
    private List<String> columns;

    /** 数据行列表（每行为 字段名->值 的 Map） */
    private List<Map<String, Object>> rows;

    /** 总行数 */
    private Long total;
}
