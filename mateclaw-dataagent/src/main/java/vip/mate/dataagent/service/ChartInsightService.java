package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.ChartInterpretRequest;
import vip.mate.dataagent.dto.ChartMetricMetaRequest;
import vip.mate.dataagent.dto.ChartMetricMetaResponse;

/**
 * 问数图表增值能力服务：指标查看（元数据解析）与图表解读。
 * <p>
 * 与「问数」主对话解耦，仅面向已渲染图表的按需能力，不参与流式主链路。
 */
public interface ChartInsightService {

    /**
     * 解析图表背后的指标元数据（中文指标名、业务口径、维度中文名、时间范围、业务限定）。
     *
     * @param request 从图表所属消息的 metrics_query 工具入参提取的查询要素
     * @return 已解析的指标/维度元数据
     */
    ChartMetricMetaResponse resolveChartMetricMeta(ChartMetricMetaRequest request);

    /**
     * 对单张图表的数据做一次性 LLM 解读。
     *
     * @param request 图表 option JSON + 会话上下文
     * @return 解读文字（失败时返回友好提示）
     */
    String interpretChart(ChartInterpretRequest request);
}
