package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道预检结果
 */
@Data
public class ChannelPreflightResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否验证通过 */
    private boolean ok;

    /** 是否跳过 */
    private boolean skipped;

    /** 验证耗时(毫秒) */
    private long durationMs;

    /** 一行状态描述 */
    private String headline;

    /** 账户展示字段 */
    private Map<String, Object> identity;

    /** 失败时应高亮的表单字段 */
    private String invalidField;

    /** 失败时的操作提示 */
    private String hint;
}
