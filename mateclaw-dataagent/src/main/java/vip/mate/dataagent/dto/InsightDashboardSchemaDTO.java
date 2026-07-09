package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 洞察仪表盘 Schema 解析 DTO
 * <p>
 * 用于反序列化 {@code dataagent_insight_dashboard.schema_json} 字段，
 * 描述仪表盘的版本号与组件列表。
 */
@Data
public class InsightDashboardSchemaDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Schema 版本号 */
    private String version;

    /** 仪表盘组件列表 */
    private List<Component> components;

    /**
     * 仪表盘组件定义
     */
    @Data
    public static class Component implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 组件唯一 ID */
        private String id;

        /** 组件类型：kpi / chart / table / filter / timeFilter */
        private String type;

        /** 组件标题 */
        private String title;

        /** 栅格位置 */
        private Position position;

        /** 数据绑定配置 */
        private DataSource dataSource;

        /** 图表子类型：line / bar / pie / area / scatter / radar（仅 chart 类型组件） */
        private String chartType;

        /** 渲染类型：echarts / kpi / table */
        private String renderType;

        /** 组件扩展配置 */
        private Map<String, Object> config;

        /** 绑定的筛选器 ID 列表（绑定后该组件仅响应专属筛选器，不再受全局筛选器影响） */
        private List<String> boundFilterIds;
    }

    /**
     * 栅格位置（grid-layout-plus 坐标系）
     */
    @Data
    public static class Position implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer x;
        private Integer y;
        private Integer w;
        private Integer h;
    }

    /**
     * 组件数据绑定配置
     */
    @Data
    public static class DataSource implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 数据源 ID（前端为 string，服务层转 Long） */
        private String datasourceId;

        /** 指标名称列表 */
        private List<String> metrics;

        /** 维度名称列表 */
        private List<String> dimensions;

        /**
         * 过滤条件（结构化存储，后端构建查询时转换为表达式字符串）
         * <p>
         * Schema 中保留结构化格式便于前端编辑器交互，构建 AloudataMetricQueryRequest 时
         * 转换为 API 5.4 节要求的表达式字符串数组。
         */
        private List<Map<String, Object>> filters;

        /**
         * 指标日期范围约束（表达式字符串，可选）
         * <p>
         * 符合 Aloudata API 5.6 节 timeConstraint 参数规范，用于静态配置组件的时间范围。
         * 运行时筛选上下文中的时间范围会覆盖此值。
         */
        private String timeConstraint;

        /** 返回行数限制 */
        private Integer limit;
    }
}
