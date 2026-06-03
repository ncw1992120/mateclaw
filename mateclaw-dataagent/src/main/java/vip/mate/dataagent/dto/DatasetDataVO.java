package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据集数据视图对象
 * <p>
 * 用于数据集表格展示和编辑，包含列定义和数据行。
 */
@Data
public class DatasetDataVO {

    /** 列定义 */
    private List<DatasetColumnDef> columns;

    /** 数据行列表 */
    private List<Map<String, Object>> rows;

    /** 总行数 */
    private Long total;

    /**
     * 列定义
     */
    @Data
    public static class DatasetColumnDef {

        /** 列名 */
        private String name;

        /** 显示标题 */
        private String title;

        /** 数据类型 */
        private String dataType;

        /** 字段分类：dimension / measure */
        private String fieldCategory;

        /** 是否可编辑 */
        private Boolean editable;

        /** 列宽（像素） */
        private Integer width;
    }
}
