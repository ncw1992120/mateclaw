package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 图表「解读」请求
 * <p>
 * 前端把用户点击的那张图表的 ECharts option JSON 连同会话上下文一起提交，
 * 后端用一次性轻量 LLM 调用生成图表数据解读文字（不污染真实会话历史）。
 */
@Data
public class ChartInterpretRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long agentId;

    /** 会话 ID（仅用于构造独立的解读会话 ID，避免污染真实历史） */
    private String conversationId;

    /** 图表的 ECharts option JSON 字符串 */
    private String echartsOption;

    /** 触发该图表的原始用户问题（可选，用于增强解读上下文） */
    private String question;
}
