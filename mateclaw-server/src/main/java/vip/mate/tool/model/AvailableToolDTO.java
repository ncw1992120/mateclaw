package vip.mate.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Picker DTO for the unified agent tool selector.
 *
 * <p>One row per atomic tool the agent can be bound to — built-in tools
 * appear under {@code source="builtin"}, channel tools under
 * {@code source="channel"}, MCP tools under {@code source="mcp"} and are
 * grouped by their server, plugin-registered tools under
 * {@code source="plugin"}. The {@link #name} field is the value the UI
 * saves into {@code mate_agent_tool.tool_name}; for MCP tools it is the
 * prefixed callback name returned by the resolver so picker and runtime
 * use the same key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableToolDTO {

    /**
     * Stable per-row identifier for the picker. The frontend uses this as
     * the {@code v-for :key} so two rows with the same prefixed
     * {@link #name} (e.g. a hash-collision pair) don't reuse each other's
     * DOM state. Server-assigned, opaque to the client.
     */
    private String rowId;

    /** {@code "builtin"}, {@code "channel"}, {@code "mcp"} or {@code "plugin"}. */
    private String source;

    /** MCP server id when {@code source == "mcp"}; null otherwise. */
    private Long providerId;

    /** Human-readable provider label — server display name for MCP, empty for builtin. */
    private String providerName;

    /** What the UI saves into {@code mate_agent_tool.tool_name}. */
    private String name;

    /** Original raw tool name as advertised upstream. UI shows this. */
    private String rawName;

    /** Tool description shown as the picker subtitle. */
    private String description;

    /** Group label for the picker UI section header (e.g. {@code "MCP · github"}). */
    private String group;

    /** Stable group key for collapse/expand state across renames. */
    private String groupId;

    /**
     * {@code true} when the entry comes from the cache while the upstream
     * MCP server is currently disconnected. The picker should grey it out;
     * runtime callbacks for stale tools are absent so the LLM cannot call
     * them either way.
     */
    private boolean stale;

    /**
     * {@code false} → the picker must disable selection. Currently set when
     * a hash collision was detected for the same (serverId, prefixed-name)
     * pair. {@code true} for everything that can be safely bound.
     */
    private boolean available;

    /**
     * Machine-readable cause when {@link #available} is {@code false}.
     * Examples: {@code "HASH_COLLISION"} (with the conflicting raw name in
     * a follow-up message), {@code "DUPLICATE_RAW_NAME"}.
     */
    private String unavailableReason;

    public static AvailableToolDTO fromBuiltin(ToolEntity t) {
        return AvailableToolDTO.builder()
                // Built-in tool names are unique by ToolRegistry contract,
                // so name suffices as a stable rowId.
                .rowId("builtin#" + t.getName())
                .source("builtin")
                .providerId(null)
                .providerName(null)
                .name(t.getName())
                .rawName(t.getName())
                .description(t.getDescription() != null ? t.getDescription() : "")
                .group("builtin")
                .groupId("builtin")
                .stale(false)
                .available(true)
                .unavailableReason(null)
                .build();
    }

    /**
     * Channel-native tool — exposed by a
     * {@link vip.mate.channel.tool.ChannelToolProvider} and registered
     * by {@code ChannelToolService}. Grouped per owning channel so the
     * picker shows "Channel · {channelName}" rather than mixing them
     * into the generic Built-in bucket.
     */
    public static AvailableToolDTO fromChannel(ToolEntity t) {
        // displayName format set by ChannelToolService is "{base} ({channelName})";
        // the channel name is what we surface in the picker group label.
        String channelName = extractChannelName(t.getDisplayName());
        String groupLabel = channelName.isEmpty() ? "Channel" : "Channel · " + channelName;
        String groupKey = t.getChannelId() != null ? "channel:" + t.getChannelId() : "channel";
        return AvailableToolDTO.builder()
                .rowId("channel#" + t.getName())
                .source("channel")
                .providerId(t.getChannelId())
                .providerName(channelName)
                .name(t.getName())
                .rawName(t.getName())
                .description(t.getDescription() != null ? t.getDescription() : "")
                .group(groupLabel)
                .groupId(groupKey)
                .stale(false)
                .available(true)
                .unavailableReason(null)
                .build();
    }

    private static String extractChannelName(String displayName) {
        if (displayName == null) return "";
        int open = displayName.lastIndexOf('(');
        int close = displayName.lastIndexOf(')');
        if (open > 0 && close > open) {
            return displayName.substring(open + 1, close).trim();
        }
        return "";
    }

    /**
     * 插件注册的工具 — 由宿主应用（如 mateclaw-dataagent）通过 SDK
     * {@code registerTool} 注册，并经 {@code AvailableToolContributor}
     * 贡献到绑定选择器，统一归入“插件工具”分组。
     *
     * @param name        运行时回调名称（写入 mate_agent_tool.tool_name）
     * @param description 工具描述（picker 副标题展示）
     * @param available   是否可绑定（可用性检查通过）
     */
    public static AvailableToolDTO fromPlugin(String name, String description, boolean available) {
        return AvailableToolDTO.builder()
                .rowId("plugin#" + name)
                .source("plugin")
                .providerId(null)
                .providerName(null)
                .name(name)
                .rawName(name)
                .description(description != null ? description : "")
                .group("插件工具")
                .groupId("plugin")
                .stale(false)
                .available(available)
                .unavailableReason(available ? null : "PLUGIN_UNAVAILABLE")
                .build();
    }
}
