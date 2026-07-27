package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.dataagent.dto.AttributionAnalysisRequest;
import vip.mate.dataagent.dto.AttributionAnalysisResponse;
import vip.mate.dataagent.dto.InsightReportPublishRequest;
import vip.mate.dataagent.dto.InsightReportVO;

import java.util.List;

/**
 * 洞察仪表盘 AI 解读报告服务接口
 * <p>
 * 基于仪表盘组件数据，加载报告模板并填充数据占位符，
 * 再调用 LLM 生成结构化分析报告（趋势分析、关键发现、建议），
 * 以及基于 Aloudata 归因分析 API 的指标归因分析能力。
 * <p>
 * 同时提供报告发布管理能力，支持将仪表盘报告内容发布为独立报告记录。
 */
public interface InsightReportService {

    /**
     * 同步生成报告（等待 LLM 完成后返回完整 HTML 报告）
     * <p>
     * 生成 HTML 格式报告并持久化到仪表盘的 reportContent 字段。
     *
     * @param dashboardId 仪表盘 ID
     * @return 完整 HTML 报告内容
     */
    String generateReport(Long dashboardId);

    /**
     * 获取已生成的报告内容
     *
     * @param dashboardId 仪表盘 ID
     * @return HTML 报告内容，未生成时返回 null
     */
    String getReport(Long dashboardId);

    /**
     * SSE 流式生成报告
     * <p>
     * 通过 SSE 推送 LLM 流式响应，支持 content/error 命名事件。
     *
     * @param dashboardId 仪表盘 ID
     * @return SseEmitter 实例
     */
    SseEmitter streamReport(Long dashboardId);

    /**
     * 执行归因分析
     * <p>
     * 完整流程：
     * <ol>
     *   <li>调用 attribution_check 校验指标是否可归因</li>
     *   <li>校验通过后调用 attribution_multi_dim 执行多维归因分析</li>
     *   <li>对高贡献维度调用 attribution_drilldown 进行下钻分析</li>
     * </ol>
     *
     * @param request 归因分析请求
     * @return 归因分析响应
     */
    AttributionAnalysisResponse attributionAnalysis(AttributionAnalysisRequest request);

    /**
     * 发布报告
     * <p>
     * 从仪表盘获取 reportContent，创建独立的报告记录，状态设为 published。
     * 报告名称默认取仪表盘名称，也可通过 request 自定义。
     *
     * @param request 发布报告请求（包含 dashboardId、可选 name 和 description）
     * @return 发布后的报告视图对象
     */
    InsightReportVO publishReport(InsightReportPublishRequest request);

    /**
     * 查询当前工作区的报告列表
     * <p>
     * 按工作区隔离，按 updateTime 降序排列。
     *
     * @return 报告视图对象列表
     */
    List<InsightReportVO> listReports();

    /**
     * 获取报告详情
     * <p>
     * 查询报告详情，校验工作区归属权限。
     *
     * @param id 报告 ID
     * @return 报告视图对象
     */
    InsightReportVO getReportDetail(Long id);

    /**
     * 删除报告
     * <p>
     * 逻辑删除，校验工作区归属权限。
     *
     * @param id 报告 ID
     */
    void deleteReport(Long id);
}
