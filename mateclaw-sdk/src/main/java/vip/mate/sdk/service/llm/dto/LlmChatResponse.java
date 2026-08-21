package vip.mate.sdk.service.llm.dto;

import lombok.Data;

/**
 * 大模型直连对话响应
 */
@Data
public class LlmChatResponse {

    /**
     * 模型完整回答内容
     */
    private String content;

    /**
     * 实际运行模型名称
     */
    private String model;

    /**
     * 实际运行 Provider ID
     */
    private String provider;

    /**
     * 输入 token 数（provider 未返回时为 null）
     */
    private Integer promptTokens;

    /**
     * 输出 token 数（provider 未返回时为 null）
     */
    private Integer completionTokens;
}
