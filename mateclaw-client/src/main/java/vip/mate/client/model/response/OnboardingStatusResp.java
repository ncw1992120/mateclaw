package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 引导状态
 */
@Data
public class OnboardingStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已配置默认模型 */
    private boolean hasDefaultModel;

    /** Ollama 是否在线 */
    private boolean ollamaOnline;

    /** 已配置的 Provider ID 列表 */
    private List<String> configuredProviders;
}
