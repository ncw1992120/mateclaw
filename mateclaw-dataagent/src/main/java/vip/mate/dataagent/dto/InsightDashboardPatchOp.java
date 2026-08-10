package vip.mate.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘增量操作指令
 * <p>
 * AI 修改模式不再要求 LLM 返回完整 Schema，而是返回操作指令列表，
 * 由后端逐条应用到现有 Schema。避免 LLM 重生成完整 JSON 带来的：
 * <ul>
 *   <li>慢：输入输出 token 约等于整个 Schema</li>
 *   <li>不可靠：LLM 容易漏改、截断、数值不敏感（如「调大」只 +2）</li>
 *   <li>破坏性：未提及的组件 position 可能被无意改动</li>
 * </ul>
 * <p>
 * 支持的操作类型（op）：
 * <ul>
 *   <li>resize：调整组件尺寸，提供 w 和/或 h</li>
 *   <li>move：调整组件位置，提供 x 和/或 y</li>
 *   <li>add：新增组件，需提供 type/title 等完整定义，position 可选（缺省自动布局）</li>
 *   <li>delete：删除组件</li>
 *   <li>update：修改组件属性（title/chartType/dataSource/config 等，不含位置）</li>
 *   <li>add-page：新增页面，需提供 pageName（可选 pageIcon/parentId/pageOrder）</li>
 *   <li>delete-page：删除页面及其所有组件，提供 id（页面ID）</li>
 *   <li>rename-page：重命名页面，提供 id（页面ID）和 pageName</li>
 *   <li>move-page：调整页面顺序，提供 id（页面ID）和 pageOrder（目标位置序号）</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "仪表盘增量操作指令")
public class InsightDashboardPatchOp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型：resize / move / add / delete / update / add-page / delete-page / rename-page / move-page */
    @Schema(description = "操作类型", example = "resize",
            allowableValues = {"resize", "move", "add", "delete", "update",
                    "add-page", "delete-page", "rename-page", "move-page"})
    private String op;

    /** 目标组件/页面 ID（add 操作时由 LLM 提供临时 ID 或留空由后端生成；页面操作时为页面ID） */
    @Schema(description = "目标组件/页面ID，add/delete/resize/move/update/delete-page/rename-page 必填", example = "comp_0")
    private String id;

    // ---- resize / move / reposition 字段 ----

    /** 目标 x 坐标（move） */
    @Schema(description = "目标x坐标（move操作）", example = "0")
    private Integer x;

    /** 目标 y 坐标（move） */
    @Schema(description = "目标y坐标（move操作）", example = "4")
    private Integer y;

    /** 目标宽度（resize），1-24 */
    @Schema(description = "目标宽度（resize），1-24", example = "16")
    private Integer w;

    /** 目标高度（resize），1-30 */
    @Schema(description = "目标高度（resize），1-30", example = "8")
    private Integer h;

    // ---- add / update 字段 ----

    /** 组件类型（add 必填）：kpi / chart / table / filter / timeFilter / aiAnalysis */
    @Schema(description = "组件类型（add必填）", example = "kpi",
            allowableValues = {"kpi", "chart", "table", "filter", "timeFilter", "aiAnalysis"})
    private String type;

    /** 组件标题（add 可选，update 可修改） */
    @Schema(description = "组件标题", example = "销售额")
    private String title;

    /** 图表子类型（add 且 type=chart 时必填）：line / bar / pie / area / scatter / radar */
    @Schema(description = "图表子类型（type=chart时）", example = "bar",
            allowableValues = {"line", "bar", "pie", "area", "scatter", "radar"})
    private String chartType;

    /** 数据绑定配置（add/update） */
    @Schema(description = "数据绑定配置")
    private InsightDashboardSchemaDTO.DataSource dataSource;

    /** 组件扩展配置（add/update） */
    @Schema(description = "组件扩展配置")
    private Map<String, Object> config;

    /** add 操作的目标页面 ID（可选，缺省追加到第一个页面） */
    @Schema(description = "add操作的目标页面ID，缺省追加到第一个页面")
    private String pageId;

    /** 新增组件时指定的位置（add 可选，缺省自动布局到末尾） */
    @Schema(description = "新增组件的指定位置（add可选）")
    private InsightDashboardSchemaDTO.Position position;

    /** 渲染类型（add 可选，缺省按 type 自动推导） */
    @Schema(description = "渲染类型，缺省按type自动推导")
    private String renderType;

    /** 多 Tab 配置（add/update 可选） */
    @Schema(description = "多Tab配置")
    private List<InsightDashboardSchemaDTO.Tab> tabs;

    /** 绑定的筛选器 ID 列表（add/update 可选） */
    @Schema(description = "绑定的筛选器ID列表")
    private List<String> boundFilterIds;

    // ---- 页面操作字段（add-page / rename-page） ----

    /** 页面名称（add-page 必填，rename-page 必填） */
    @Schema(description = "页面名称（add-page/rename-page必填）", example = "销售分析")
    private String pageName;

    /** 页面图标（add-page 可选） */
    @Schema(description = "页面图标（add-page可选）", example = "DataAnalysis")
    private String pageIcon;

    /** 父页面 ID（add-page 可选，设置后为子页面） */
    @Schema(description = "父页面ID（add-page可选，设置后为子页面）")
    private String pageParentId;

    /** 页面排序序号（add-page 可选，缺省追加到末尾） */
    @Schema(description = "页面排序序号（add-page可选，缺省追加到末尾）", example = "2")
    private Integer pageOrder;
}
