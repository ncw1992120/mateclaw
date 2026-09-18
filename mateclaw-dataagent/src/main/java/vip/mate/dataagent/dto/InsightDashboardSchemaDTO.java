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

    /**
     * 统一数据集编排输入；使用 Map 保留前端来源配置、字段映射和筛选扩展字段。
     * <p>
     * 字段名与展示名契约（定版见 docs/策略解读/字段名与展示名契约-实施计划.md §4.3）：
     * <ul>
     *   <li>{@code fieldMappings[].source} = <b>字段名</b>（技术主键，组件生命周期内不可变），
     *       未来实现下推时<b>只读 source</b>；</li>
     *   <li>{@code fieldMappings[].target} = <b>展示名</b>（表现层标签，数据集内唯一、可空回退 source），
     *       <b>仅</b>透传给导出表头等展示场景，不得用于下推 SQL；</li>
     *   <li>{@code filters[].field} 自本版本起恒为 <b>字段名</b>（老配置的展示名由前端读入时惰性归一）。</li>
     * </ul>
     */
    private List<Map<String, Object>> datasetInputs;

    /** Python 预处理草稿，系统生成区和用户区域由前端维护。 */
    private String script;

    /** 脚本参数定义。 */
    private List<Map<String, Object>> parameters;

    /** 筛选器到数据集输入的声明式绑定。 */
    private List<Map<String, Object>> scriptFilterBindings;

    /** Runner 执行限制。 */
    private Map<String, Object> executionPolicy;

    /** 脚本结果到组件的显式绑定。 */
    private List<Map<String, Object>> scriptBindings;

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

        /**
         * KPI 指标分组配置（仅 kpi 类型）。
         * <p>
         * 口径（docs/策略解读/指标分组卡片原型设计.md）：「结果集优先」，指标由最终结果集字段
         * 逐列投影生成，fieldKey 为稳定 key。Schema 必须显式声明该字段：复制组件 / AI 修改
         * 会把 schema_json 反序列化为 DTO 再序列化，未声明的字段会被静默丢弃。
         */
        private List<KpiMetric> kpiMetrics;
    }

    /**
     * KPI 指标配置（结果集优先：由最终结果集字段逐列投影生成，fieldKey 为稳定 key）
     */
    @Data
    public static class KpiMetric implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 结果集字段名（稳定 key，只读标识） */
        private String fieldKey;

        /** 展示列名（用户可编辑） */
        private String displayName;

        /** 单位（用户手动填写，留空不显示） */
        private String unit;

        /** 辅助说明（用户可编辑） */
        private String helperText;

        /** 是否在卡片中展示 */
        private Boolean visible;

        /** 自由布局：距卡片内容区左侧 px（拖动可能产生小数，用 Double 保真回传） */
        private Double x;

        /** 自由布局：距卡片内容区顶部 px */
        private Double y;

        /** 自由布局：宽 px */
        private Double w;

        /** 自由布局：高 px */
        private Double h;

        /** 各字段样式（展示名 / 指标值 / 单位 / 辅助说明） */
        private KpiMetricStyles styles;
    }

    /**
     * KPI 指标四个字段的样式集合
     */
    @Data
    public static class KpiMetricStyles implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 展示名样式 */
        private KpiMetricFieldStyle name;

        /** 指标值样式 */
        private KpiMetricFieldStyle value;

        /** 单位样式 */
        private KpiMetricFieldStyle unit;

        /** 辅助说明样式 */
        private KpiMetricFieldStyle helper;
    }

    /**
     * KPI 指标单字段样式（大小 / 字体 / 颜色 / 字重）
     */
    @Data
    public static class KpiMetricFieldStyle implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 字号 px */
        private Double size;

        /** 字体键（前端映射 CSS font-family） */
        private String family;

        /** 颜色（HEX，如 #1f2329） */
        private String color;

        /** 字重：bold / normal */
        private String bold;
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

        /** 来源类型：ALOUDATA/JDBC/HTTP_API/FILE；缺省时兼容旧 Aloudata Schema。 */
        private String sourceType;

        /** JDBC 来源的只读 SQL 草稿；仅 sourceType=JDBC 时生效。 */
        private String sql;

        /** 已固化统一数据集 ID（供脚本输入或后续统一预览使用）。 */
        private String datasetId;

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
