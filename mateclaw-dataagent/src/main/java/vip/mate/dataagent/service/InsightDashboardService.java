package vip.mate.dataagent.service;

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
}
