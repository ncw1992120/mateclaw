package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 洞察仪表盘 AI 解读报告服务接口
 * <p>
 * 基于仪表盘组件数据，加载报告模板并填充数据占位符，
 * 再调用 LLM 生成结构化分析报告（趋势分析、关键发现、建议）。
 */
public interface InsightReportService {

    /**
     * 同步生成报告（等待 LLM 完成后返回完整 Markdown）
     *
     * @param dashboardId 仪表盘 ID
     * @return 完整 Markdown 报告
     */
    String generateReport(Long dashboardId);

    /**
     * SSE 流式生成报告
     * <p>
     * 通过 SSE 推送 LLM 流式响应，支持 content/error 命名事件。
     *
     * @param dashboardId 仪表盘 ID
     * @return SseEmitter 实例
     */
    SseEmitter streamReport(Long dashboardId);
}
