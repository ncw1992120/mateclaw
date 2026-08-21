package vip.mate.sdk.service.llm.dto;

import lombok.Data;

/**
 * 大模型直连对话消息
 * <p>
 * 角色（role）及作用：
 * <ul>
 *   <li><b>system</b>：系统提示。设定模型的角色身份、行为规范、输出格式与全局约束，
 *       通常放在消息列表最前面，优先级最高。例如设定"你是一名严谨的数据分析师"。</li>
 *   <li><b>user</b>：用户消息。代表调用者/终端用户的输入，是模型需要直接响应的内容。</li>
 *   <li><b>assistant</b>：助手（模型）历史回复。用于携带多轮对话上下文，让模型基于
 *       之前的问答继续；也可用于注入"示范回答"（few-shot）引导输出风格。</li>
 * </ul>
 * 约定：消息按数组顺序拼接组成完整上下文；role 非上述值时按 user 处理。
 */
@Data
public class LlmChatMessage {

    /**
     * 角色：system（系统提示，优先级最高）/ user（用户输入）/ assistant（助手历史回复）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}
