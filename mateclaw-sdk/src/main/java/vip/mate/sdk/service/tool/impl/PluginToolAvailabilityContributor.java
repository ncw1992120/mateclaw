package vip.mate.sdk.service.tool.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.tool.ToolRegistry;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.tool.service.AvailableToolContributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件工具可绑定性贡献者
 * <p>
 * 将宿主应用通过 {@code ToolRuntime.registerTool}（即
 * {@code ToolRegistry.registerPluginTool}）注册的插件工具纳入智能体工具绑定
 * 选择器，使 dataagent 注册的工具（如 query_dataset / data_query /
 * python_analysis / aloudata_* 等）也能像内置工具一样绑定到特定智能体。
 * <p>
 * 作为 {@link AvailableToolContributor} 的 SDK 侧实现，宿主应用无需直接接触
 * mateclaw-server 内部类库即可获得该能力。
 */
@Component
@RequiredArgsConstructor
public class PluginToolAvailabilityContributor implements AvailableToolContributor {

    private final ToolRegistry toolRegistry;

    /**
     * 将注册中心中的插件工具映射为可绑定工具行
     *
     * @return 插件工具 DTO 列表（source 为 "plugin"，归入“插件工具”分组）
     */
    @Override
    public List<AvailableToolDTO> contributeAvailableTools() {
        List<AvailableToolDTO> out = new ArrayList<>();
        for (ToolRegistry.PluginToolDescriptor descriptor : toolRegistry.listPluginTools()) {
            out.add(AvailableToolDTO.fromPlugin(
                    descriptor.name(), descriptor.description(), descriptor.available()));
        }
        return out;
    }
}
