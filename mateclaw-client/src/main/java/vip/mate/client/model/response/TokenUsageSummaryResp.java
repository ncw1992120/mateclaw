package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Token 使用统计摘要
 */
@Data
public class TokenUsageSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalMessages;
    private List<ModelUsageItem> byModel;
    private List<DateUsageItem> byDate;

    /**
     * 按模型统计项
     */
    @Data
    public static class ModelUsageItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String runtimeModel;
        private String runtimeProvider;
        private long promptTokens;
        private long completionTokens;
        private long messageCount;
    }

    /**
     * 按日期统计项
     */
    @Data
    public static class DateUsageItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String date;
        private long promptTokens;
        private long completionTokens;
        private long messageCount;
    }
}
