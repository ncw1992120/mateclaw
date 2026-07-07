package vip.mate.sdk.service.impl;

import org.springframework.stereotype.Service;
import vip.mate.datasource.service.EChartsOptionBuilder;
import vip.mate.sdk.service.ChartOptionRuntime;

import java.util.List;

/**
 * 图表 Option 构建运行时实现
 * <p>
 * 包装 mateclaw-server 的 EChartsOptionBuilder.tryBuild 静态方法，
 * 通过 Spring DI 暴露给宿主应用调用。
 */
@Service
public class ChartOptionRuntimeImpl implements ChartOptionRuntime {

    @Override
    public String tryBuild(List<String> columns, List<List<String>> rows) {
        return EChartsOptionBuilder.tryBuild(columns, rows);
    }
}
