package vip.mate.tool.service;

import vip.mate.tool.model.AvailableToolDTO;

import java.util.List;

/**
 * 可绑定工具贡献者
 * <p>
 * 宿主应用（如 mateclaw-dataagent）通过实现本接口并向 Spring 容器注册 Bean，
 * 即可将自身注册的原子工具（如插件工具）纳入智能体工具绑定选择器，
 * 供 {@link AvailableToolService} 聚合展示，同时参与绑定保存校验
 * （{@code AgentBindingService} 的校验与 picker 共用同一数据源）。
 * <p>
 * 现有工具来源与贡献方式：
 * <ul>
 *   <li>内置 {@code @Tool} Bean（mate_tool 表）与 MCP 工具由
 *       {@link AvailableToolService} 自身聚合；</li>
 *   <li>插件注册的工具由 SDK 提供的贡献者实现
 *       （{@code PluginToolAvailabilityContributor}）贡献。</li>
 * </ul>
 */
public interface AvailableToolContributor {

    /**
     * 贡献可绑定工具行
     * <p>
     * 返回的每个 DTO 必须满足 picker 的 “保存名 == 运行时回调 key” 契约：
     * {@code name} 会被写入 {@code mate_agent_tool.tool_name}，并需能被
     * 运行时 {@code AgentToolSet} 的别名索引解析到对应回调。
     *
     * @return 可绑定工具 DTO 列表；返回 null 或空列表表示无贡献
     */
    List<AvailableToolDTO> contributeAvailableTools();
}
