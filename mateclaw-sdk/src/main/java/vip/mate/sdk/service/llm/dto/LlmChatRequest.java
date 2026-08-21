package vip.mate.sdk.service.llm.dto;

import lombok.Data;

import java.util.List;

/**
 * 大模型直连对话请求
 * <p>
 * 最小参数集：{@code messages} 必填；{@code provider} / {@code model} 可选
 * （缺省时使用默认模型）；{@code temperature} / {@code maxTokens} 可选。
 */
@Data
public class LlmChatRequest {

    /**
     * Provider ID，可选。缺省时使用默认模型所属 provider。
     */
    private String provider;

    /**
     * 模型名称，可选。缺省时使用默认模型。
     */
    private String model;

    /**
     * 消息列表（必填），每项为 {role, content}。
     */
    private List<LlmChatMessage> messages;

    /**
     * 采样温度，可选。
     */
    private Double temperature;

    /**
     * 最大输出 token 数，可选。
     */
    private Integer maxTokens;
}
