package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.ChartInterpretRequest;
import vip.mate.dataagent.dto.ChartMetricMetaRequest;
import vip.mate.dataagent.dto.ChartMetricMetaResponse;
import vip.mate.dataagent.service.ChartInsightService;

/**
 * 问数图表增值能力：指标查看（元数据解析）与图表解读。
 */
@RestController
@RequestMapping("/v1/chat/chart")
@RequiredArgsConstructor
@Tag(name = "DataAgent 图表能力", description = "问数图表的指标查看与解读接口")
public class DataAgentChartController {

    private final ChartInsightService chartInsightService;

    @PostMapping("/metric-meta")
    @Operation(summary = "指标查看", description = "解析图表背后的指标名、业务口径、分析维度、时间范围、业务限定")
    public R<ChartMetricMetaResponse> metricMeta(@RequestBody ChartMetricMetaRequest req) {
        return R.ok(chartInsightService.resolveChartMetricMeta(req));
    }

    @PostMapping("/interpret")
    @Operation(summary = "图表解读", description = "对单张图表的数据做一次性 AI 解读，不污染真实会话历史")
    public R<String> interpret(@RequestBody ChartInterpretRequest req) {
        return R.ok(chartInsightService.interpretChart(req));
    }
}
