package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 洞察仪表盘组件渲染数据 DTO
 * <p>
 * 后端取数 + 图表构建后返回给前端的组件渲染数据，
 * 对应前端 {@code InsightComponentData} 类型。
 */
@Data
public class InsightComponentDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对应组件 ID */
    private String componentId;

    /** 渲染类型：echarts / kpi / table */
    private String renderType;

    /** ECharts option（renderType=echarts 时） */
    private Map<String, Object> option;

    /** KPI 卡片数据（renderType=kpi 时） */
    private KpiData kpi;

    /** 表格数据（renderType=table 时） */
    private TableData table;

    /** 取数失败时的错误信息（可选，前端展示降级提示） */
    private String error;

    /**
     * KPI 卡片数据
     */
    @Data
    public static class KpiData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 指标名称 */
        private String name;

        /** 指标值 */
        private String value;

        /** 环比变化（如 "12.5%"），可为 null */
        private String chg;

        /** 是否上升 */
        private Boolean up;
    }

    /**
     * 表格数据
     */
    @Data
    public static class TableData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 列名列表 */
        private List<String> columns;

        /** 数据行（每行是字符串列表） */
        private List<List<String>> rows;
    }
}
