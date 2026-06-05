package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * WebChat 渠道配置
 */
@Data
public class WebChatConfigResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 渠道名称 */
    private String channelName;

    /** 关联Agent ID */
    private Long agentId;

    /** 聊天窗口标题 */
    private String title;

    /** 输入框占位文字 */
    private String placeholder;

    /** 主题色 */
    private String primaryColor;

    /** 欢迎消息 */
    private String welcomeMessage;
}
