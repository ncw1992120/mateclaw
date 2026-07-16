package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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

    /** 仪表盘视角列表（顶层 Tab 配置） */
    private List<Perspective> perspectives;

    /** 仪表盘组件列表（旧格式，向下兼容） */
    private List<Component> components;

    /** 仪表盘页面列表（新格式，每个页面拥有独立的组件列表） */
    private List<Page> pages;

    /**
     * 获取仪表盘中所有组件（兼容旧格式和新格式）
     * <p>
     * 新格式（pages）优先：从所有页面中收集组件；
     * 旧格式（components）：直接返回扁平组件列表。
     *
     * @return 所有组件列表，不会返回 null
     */
    public List<Component> getAllComponents() {
        // 新格式：从 pages 中收集
        if (pages != null && !pages.isEmpty()) {
            List<Component> all = new ArrayList<>();
            for (Page page : pages) {
                if (page.getComponents() != null) {
                    all.addAll(page.getComponents());
                }
            }
            return all;
        }
        // 旧格式：直接返回 components
        if (components != null) {
            return components;
        }
        return Collections.emptyList();
    }

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

        /** 是否启用组件级时间筛选（右上角时间选择器） */
        private Boolean enableTimeFilter;

        /** AI 分析内容（Markdown，生成后持久化到 Schema，刷新不丢失） */
        private String aiAnalysisContent;

        /** 多 Tab 配置（可选，配置后组件渲染为多 Tab 切换模式） */
        private List<Tab> tabs;

        /** 组件所属视角 ID 列表（空或未配置时表示在所有视角显示） */
        private List<String> perspectiveIds;

        /** 是否启用多指标模式（仅 kpi 类型，开启后卡片同时展示多个指标） */
        private Boolean multiKpi;
    }

    /**
     * 仪表盘页面定义（多页面结构）
     */
    @Data
    public static class Page implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 页面唯一 ID */
        private String id;

        /** 页面显示名称 */
        private String name;

        /** 页面图标（可选） */
        private String icon;

        /** 父页面 ID（可选，设置后为子页面，实现多级菜单） */
        private String parentId;

        /** 页面排序序号 */
        private Integer order;

        /** 页面内的组件列表 */
        private List<Component> components;
    }

    /**
     * 仪表盘视角定义（顶层 Tab）
     */
    @Data
    public static class Perspective implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 视角唯一 ID */
        private String id;

        /** 视角显示名称 */
        private String name;

        /** 视角图标（可选） */
        private String icon;
    }

    /**
     * 组件 Tab 配置（每个 Tab 拥有独立的数据源配置）
     */
    @Data
    public static class Tab implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Tab 唯一 ID */
        private String id;

        /** Tab 标题 */
        private String title;

        /** Tab 数据源配置 */
        private DataSource dataSource;
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
