package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Provider 启用/禁用结果
 */
@Data
public class ProviderEnableResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 禁用时是否导致默认模型自动切换 */
    private boolean defaultSwitched;

    /** 新默认模型的 Provider ID */
    private String newDefaultProviderId;

    /** 新默认模型名称 */
    private String newDefaultModel;
}
