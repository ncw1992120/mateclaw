package vip.mate.workspace.conversation.vo;

import lombok.Data;

/**
 * 上下文压缩状态视图对象
 * <p>
 * 记录最近一次压缩的触发原因与效果，供前端展示压缩边界卡片。
 */
@Data
public class ContextCompressionStatusVO {

    /**
     * 压缩状态：none（无） / compacted（已压缩） / failed（失败）
     */
    private String status;

    /**
     * 压缩前的 token 数
     */
    private Integer preTokens;

    /**
     * 压缩后的 token 数
     */
    private Integer postTokens;

    /**
     * 被摘要的旧消息数量
     */
    private Integer messagesSummarized;

    /**
     * 保留的最近消息数量
     */
    private Integer tailKept;

    /**
     * 持久化后的压缩摘要消息 ID
     */
    private Long summaryId;

    public static ContextCompressionStatusVO none() {
        ContextCompressionStatusVO vo = new ContextCompressionStatusVO();
        vo.setStatus("none");
        return vo;
    }

    public static ContextCompressionStatusVO compacted(Integer preTokens, Integer postTokens,
                                                       Integer messagesSummarized, Integer tailKept,
                                                       Long summaryId) {
        ContextCompressionStatusVO vo = new ContextCompressionStatusVO();
        vo.setStatus("compacted");
        vo.setPreTokens(preTokens);
        vo.setPostTokens(postTokens);
        vo.setMessagesSummarized(messagesSummarized);
        vo.setTailKept(tailKept);
        vo.setSummaryId(summaryId);
        return vo;
    }

    public static ContextCompressionStatusVO failed(Integer preTokens, Integer postTokens,
                                                    Integer messagesSummarized, Integer tailKept) {
        ContextCompressionStatusVO vo = new ContextCompressionStatusVO();
        vo.setStatus("failed");
        vo.setPreTokens(preTokens);
        vo.setPostTokens(postTokens);
        vo.setMessagesSummarized(messagesSummarized);
        vo.setTailKept(tailKept);
        return vo;
    }
}
