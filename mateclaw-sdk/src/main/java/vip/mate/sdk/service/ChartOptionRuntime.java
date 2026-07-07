package vip.mate.sdk.service;

import java.util.List;

/**
 * 图表 Option 构建运行时接口
 * <p>
 * 封装 mateclaw-server 的 EChartsOptionBuilder.tryBuild 能力，
 * 宿主应用通过此接口可在编程层面生成图表配置，
 * 无需直接依赖 mateclaw-server 的 EChartsOptionBuilder。
 */
public interface ChartOptionRuntime {

    /**
     * 自动推断图表类型并生成 ECharts option JSON
     *
     * @param columns 列名列表
     * @param rows    数据行（每行是字符串列表）
     * @return ECharts option JSON 字符串，数据不适合可视化时返回 null
     */
    String tryBuild(List<String> columns, List<List<String>> rows);
}
