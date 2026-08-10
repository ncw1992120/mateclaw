package vip.mate.dataagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 助手对话请求
 * <p>
 * 统一 AI 生成和 AI 修改的对话请求，通过 dashboardId 是否为空区分生成/修改模式：
 * - dashboardId 为空时：AI 生成模式，根据用户描述和数据源生成新仪表盘
 * - dashboardId 不为空时：AI 修改模式，根据用户指令修改已有仪表盘
 */
@Data
@Schema(description = "AI 助手对话请求，统一 AI 生成和 AI 修改")
public class InsightDashboardAiChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仪表盘 ID（修改模式必填，生成模式为空） */
    @Schema(description = "仪表盘 ID，修改模式必填，生成模式为空", example = "1")
    private String dashboardId;

    /** 仪表盘名称（仅生成模式使用） */
    @Schema(description = "仪表盘名称，仅生成模式使用", example = "销售分析仪表盘")
    private String name;

    /** 数据源 ID（仅生成模式使用） */
    @Schema(description = "数据源 ID，仅生成模式使用", example = "1")
    private Long datasourceId;

    /** 用户消息/指令 */
    @Schema(description = "用户消息/指令，生成模式为需求描述，修改模式为修改指令", example = "帮我生成一个销售分析仪表盘，包含销售额趋势、区域分布、TOP10 产品")
    private String message;

    /** 会话 ID（用于多轮对话，修改模式可选，缺省时后端自动生成） */
    @Schema(description = "会话 ID，用于多轮对话，修改模式可选，缺省时后端自动生成", example = "insight-ai-chat-a1b2c3d4e5f6")
    private String conversationId;

    /** 历史对话消息（用于多轮对话上下文，前端传入，不持久化） */
    @Schema(description = "历史对话消息列表，用于多轮对话上下文")
    private List<HistoryMessage> historyMessages;

    /**
     * 历史对话消息
     */
    @Data
    @Schema(description = "历史对话消息")
    public static class HistoryMessage implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 角色：user 或 assistant */
        @Schema(description = "角色", example = "user")
        private String role;

        /** 消息内容 */
        @Schema(description = "消息内容", example = "帮我生成一个销售分析仪表盘")
        private String content;
    }
}
