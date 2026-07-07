package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.InsightComponentDataDTO;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;

import java.util.List;

/**
 * 洞察仪表盘数据绑定服务接口
 * <p>
 * 根据仪表盘 Schema 中的组件数据源配置，调用 Aloudata 指标查询并生成图表渲染数据。
 */
public interface InsightDataBindService {

    /**
     * 绑定单个组件数据
     *
     * @param component 仪表盘组件定义
     * @return 组件渲染数据；filter 类型或未配置数据源时返回 null
     */
    InsightComponentDataDTO bindComponent(InsightDashboardSchemaDTO.Component component);

    /**
     * 绑定仪表盘所有组件数据
     *
     * @param dashboardId 仪表盘 ID
     * @return 组件渲染数据列表（过滤掉 filter 类型和取数失败的 null 项）
     */
    List<InsightComponentDataDTO> bindDashboard(Long dashboardId);

    /**
     * 预览模式取数（等价于 bindDashboard）
     *
     * @param dashboardId 仪表盘 ID
     * @return 组件渲染数据列表
     */
    List<InsightComponentDataDTO> previewData(Long dashboardId);
}
