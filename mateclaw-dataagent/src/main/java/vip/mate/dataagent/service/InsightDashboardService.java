package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.InsightDashboardAiChatRequest;
import vip.mate.dataagent.dto.InsightDashboardCreateRequest;
import vip.mate.dataagent.dto.InsightDashboardGenerateRequest;
import vip.mate.dataagent.dto.InsightDashboardModifyRequest;
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
     * AI助手对话
     * <p>
     * 统一AI生成和AI修改能力，通过dashboardId是否为空区分模式：
     * - dashboardId为空：AI生成模式，根据用户描述和数据源生成新仪表盘
     * - dashboardId不为空：AI修改模式，根据用户指令修改已有仪表盘
     *
     * @param request AI助手对话请求
     * @return 生成或修改后的仪表盘视图对象
     */
    InsightDashboardVO aiChatDashboard(InsightDashboardAiChatRequest request);

    /**
     * AI生成仪表盘（内部方法，由aiChatDashboard委托调用）
     *
     * @param request AI生成请求（含数据源ID和用户描述）
     * @return 创建后的仪表盘视图对象
     */
    InsightDashboardVO generateDashboard(InsightDashboardGenerateRequest request);

    /**
     * AI对话修改仪表盘（内部方法，由aiChatDashboard委托调用）
     *
     * @param request AI修改请求（含仪表盘ID和用户修改指令）
     * @return 修改后的仪表盘视图对象
     */
    InsightDashboardVO modifyDashboard(InsightDashboardModifyRequest request);
}
