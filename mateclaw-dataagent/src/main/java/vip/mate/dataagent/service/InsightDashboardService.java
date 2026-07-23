package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.dataagent.dto.InsightDashboardAiChatRequest;
import vip.mate.dataagent.dto.InsightDashboardCreateRequest;
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
     * @return 仪表盘列表
     */
    List<InsightDashboardVO> listDashboards();

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
     * AI助手对话（流式）
     * <p>
     * 统一AI生成和AI修改能力，通过dashboardId是否为空区分模式：
     * - dashboardId为空：AI生成模式，根据用户描述和数据源生成新仪表盘
     * - dashboardId不为空：AI修改模式，根据用户指令修改已有仪表盘
     * <p>
     * 通过SSE流式推送AI推理过程，事件类型：
     * - content: AI推理文本增量
     * - result: 最终仪表盘数据（JSON格式）
     * - error: 错误信息
     *
     * @param request AI助手对话请求
     * @return SseEmitter 实例
     */
    SseEmitter streamAiChatDashboard(InsightDashboardAiChatRequest request);
}
