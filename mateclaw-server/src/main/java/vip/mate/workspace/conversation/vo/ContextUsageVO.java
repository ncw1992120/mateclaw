package vip.mate.workspace.conversation.vo;

import lombok.Data;

import java.util.List;

/**
 * 上下文使用情况视图对象
 * <p>
 * 汇总一次 LLM 调用前 prompt 中各组成部分的 token 占用，
 * 以及当前会话是否已触发上下文压缩。
 */
@Data
public class ContextUsageVO {

    /**
     * 当前模型上下文窗口大小（token 上限）
     */
    private int contextWindow;

    /**
     * 已使用的总 token 数（含 system prompt、工具定义、历史对话、当前消息预算）
     */
    private int usedTokens;

    /**
     * 已使用比例，范围 [0.0, 1.0]
     */
    private double usedPercent;

    /**
     * 各分类 token 占用明细
     */
    private List<ContextUsageCategoryVO> categories;

    /**
     * 最近一次压缩状态
     */
    private ContextCompressionStatusVO compression;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 数据更新时间戳
     */
    private long timestamp;

    public static ContextUsageVO empty(String conversationId, int contextWindow) {
        ContextUsageVO vo = new ContextUsageVO();
        vo.setConversationId(conversationId);
        vo.setContextWindow(contextWindow);
        vo.setUsedTokens(0);
        vo.setUsedPercent(0.0);
        vo.setCategories(List.of());
        vo.setCompression(ContextCompressionStatusVO.none());
        vo.setTimestamp(System.currentTimeMillis());
        return vo;
    }
}
