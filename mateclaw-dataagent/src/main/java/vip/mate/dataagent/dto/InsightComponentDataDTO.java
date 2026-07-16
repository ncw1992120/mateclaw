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

    /** 渲染类型：echarts / kpi / table / aiAnalysis */
    private String renderType;

    /** ECharts option（renderType=echarts 时） */
    private Map<String, Object> option;

    /** KPI 卡片数据（renderType=kpi 时，单指标模式） */
    private KpiData kpi;

    /** KPI 多指标数据列表（renderType=kpi 且 multiKpi=true 时，按指标逐列展示） */
    private List<KpiData> kpiList;

    /** 表格数据（renderType=table 时） */
    private TableData table;

    /** AI 分析数据（renderType=aiAnalysis 时） */
    private AiAnalysisData aiAnalysis;

    /** 取数失败时的错误信息（可选，前端展示降级提示） */
    private String error;

    /** 多 Tab 渲染数据（key = tabId，value = 该 Tab 的渲染数据，仅当组件配置了 tabs 时有值） */
    private Map<String, TabData> tabs;

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

    /**
     * AI 分析数据
     */
    @Data
    public static class AiAnalysisData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 模板填充后的数据部分（Markdown） */
        private String dataSection;

        /** AI 生成的分析内容（Markdown，可能为空表示尚未生成） */
        private String analysisSection;
    }

    /**
     * 单个 Tab 的渲染数据（结构与主渲染数据一致，但不含 tabs 字段本身）
     */
    @Data
    public static class TabData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Tab 标题 */
        private String title;

        /** ECharts option（renderType=echarts 时） */
        private Map<String, Object> option;

        /** KPI 卡片数据（renderType=kpi 时，单指标模式） */
        private KpiData kpi;

        /** KPI 多指标数据列表（renderType=kpi 且 multiKpi=true 时） */
        private List<KpiData> kpiList;

        /** 表格数据（renderType=table 时） */
        private TableData table;

        /** 取数失败时的错误信息 */
        private String error;
    }
}
