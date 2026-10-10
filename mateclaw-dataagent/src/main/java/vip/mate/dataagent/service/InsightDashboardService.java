package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.dataagent.dto.InsightDashboardAiChatRequest;
import vip.mate.dataagent.dto.InsightDashboardCreateRequest;
import vip.mate.dataagent.dto.InsightDashboardPageVO;
import vip.mate.dataagent.dto.InsightDashboardSaveAsTemplateRequest;
import vip.mate.dataagent.dto.InsightDashboardSummaryQuery;
import vip.mate.dataagent.dto.InsightDashboardUpdateRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;

import java.util.List;

/**
 * 洞察仪表盘服务接口
 * <p>
 * 提供低代码仪表盘 Schema 的 CRUD 能力，按工作区隔离。
 */
public interface InsightDashboardService {

    /**
     * 列出当前工作区的仪表盘
     *
     * @param visibility 可见性过滤（可选）：逗号分隔的 visibility 取值，如 "template,official"；
     *                   为空或不传时返回工作区内全部仪表盘
     * @return 仪表盘列表
     */
    List<InsightDashboardVO> listDashboards(String visibility);

    /**
     * 分页查询当前工作区的仪表盘摘要（列表专用轻量契约）。
     * <p>
     * 只投影卡片所需字段，并额外计算列表缩略图类型；不含 {@code schemaJson} 与
     * {@code reportContent}。计数与分页均在数据库侧完成。
     *
     * @param query 查询条件（分页 / 可见性 / 状态 / 关键词 / 排序）
     * @return 分页摘要响应
     */
    InsightDashboardPageVO pageDashboards(InsightDashboardSummaryQuery query);

    /**
     * 获取仪表盘详情
     *
     * @param id 仪表盘 ID
     * @return 仪表盘视图对象
     */
    InsightDashboardVO getDashboard(Long id);

    /**
     * 创建仪表盘
     *
     * @param request 创建请求
     * @return 创建后的仪表盘视图对象
     */
    InsightDashboardVO createDashboard(InsightDashboardCreateRequest request);

    /**
     * 更新仪表盘（含保存 Schema）
     *
     * @param id      仪表盘 ID
     * @param request 更新请求
     * @return 更新后的仪表盘视图对象
     */
    InsightDashboardVO updateDashboard(Long id, InsightDashboardUpdateRequest request);

    /**
     * 删除仪表盘
     *
     * @param id 仪表盘 ID
     */
    void deleteDashboard(Long id);

    /**
     * 复制仪表盘
     *
     * @param id 被复制的仪表盘 ID
     * @return 复制后的新仪表盘视图对象
     */
    InsightDashboardVO copyDashboard(Long id);

    /**
     * 存为样例模板：将指定仪表盘派生为团队共享样例模板（visibility=template）。
     * 模板副本保留数据集绑定与 Schema，带示例数据可直接使用；模板元信息记录来源与作者。
     *
     * @param id      源仪表盘 ID（需为当前用户所有或工作区管理员）
     * @param request 模板元信息（名称/描述/标签/分类），为空时使用源仪表盘信息
     * @return 生成的模板视图对象
     */
    InsightDashboardVO saveAsTemplate(Long id, InsightDashboardSaveAsTemplateRequest request);

    /**
     * AI助手对话（流式）
     * <p>
     * 统一AI生成和AI修改能力，通过dashboardId是否为空区分模式：
     * - dashboardId为空：AI生成模式，根据用户描述和数据源生成新仪表盘
     * - dashboardId不为空：AI修改模式，根据用户指令修改已有仪表盘
     * <p>
     * 通过SSE流式推送AI推理过程，事件类型：
     * - reasoning: AI思考过程增量（展示给用户）
     * - tool_call: 工具调用信息
     * - tool_result: 工具调用结果
     * - hint: RAG/记忆/规划系统提示信息
     * - result: 仪表盘生成/修改成功（JSON直接更新到数据库，前端展示成功提示）
     * - error: 错误信息
     *
     * @param request AI助手对话请求
     * @return SseEmitter 实例
     */
    SseEmitter streamAiChatDashboard(InsightDashboardAiChatRequest request);
}
