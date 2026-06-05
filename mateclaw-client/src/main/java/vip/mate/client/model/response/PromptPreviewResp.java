package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能提示词预览
 */
@Data
public class PromptPreviewResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 实际长度 */
    private int actualLength;

    /** 预估Token数 */
    private int estimatedTokens;

    /** 完整prompt文本 */
    private String prompt;
}
